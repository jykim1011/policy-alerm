import json
import os
import sys
import time
from datetime import datetime, timezone, timedelta
from pathlib import Path
from urllib.parse import quote

import requests

from pipeline.crawler import CRAWLERS, load_seen, save_seen, is_new_policy
from pipeline.extractor import extract_text
from pipeline.summarizer import summarize_policy
from pipeline.publisher import build_policy_id, publish_policy, update_index
from pipeline.notifier import notify_new_policy
from pipeline.models import PolicyItem

KST = timezone(timedelta(hours=9))
HEADERS = {"User-Agent": "Mozilla/5.0 (compatible; PolicyBot/1.0)"}

# 갓 발행한 정책의 상세 JSON이 GitHub Pages(CDN)에 반영되기 전에 FCM 푸시가 나가면
# 사용자가 알림을 탭했을 때 "정책을 불러올 수 없습니다"가 뜬다. 그래서 크롤·발행(run)과
# 알림(notify)을 분리해 CI가 docs/를 push한 뒤에만 알림을 보낸다.
PENDING_FILE = Path("pipeline/pending_notify.json")
CDN_BASE = "https://jykim1011.github.io/policy-alerm/"


def download_file(url: str) -> bytes:
    resp = requests.get(url, headers=HEADERS, timeout=30, stream=True)
    resp.raise_for_status()
    return resp.content


def run(batch: str) -> None:
    seen = load_seen()
    new_items = []

    for crawler in CRAWLERS:
        print(f"[{crawler.SOURCE}] 크롤링 시작")
        try:
            raw_policies = crawler.fetch()
        except Exception as e:
            print(f"[{crawler.SOURCE}] 크롤링 실패: {e}", file=sys.stderr)
            continue

        new_in_source = [r for r in raw_policies if is_new_policy(r.url_hash, seen)]
        print(f"[{crawler.SOURCE}] 수집 {len(raw_policies)}건 → seen 필터 후 {len(new_in_source)}건 신규")

        for raw in new_in_source:
            print(f"  신규 정책 발견: {raw.title}")

            # 텍스트 추출: 본문이 이미 있으면 그대로 사용한다.
            # (정책브리핑 OpenAPI는 본문을 inline으로 제공하며, 첨부파일 호스트
            #  korea.kr은 GitHub Actions IP를 차단하므로 다운로드는 항상 실패·지연된다.)
            text = raw.html_content
            if not text.strip() and raw.file_url and raw.file_type:
                try:
                    file_bytes = download_file(raw.file_url)
                    extracted = extract_text(file_bytes, raw.file_type)
                    if extracted.strip():
                        text = extracted
                except Exception as e:
                    print(f"  파일 추출 실패, HTML 폴백: {e}", file=sys.stderr)

            # AI 요약
            try:
                summary = summarize_policy(raw.title, text)
            except Exception as e:
                # 요약 실패(일시적 할당량/네트워크 등)는 seen에 넣지 않아 다음 실행에서 재시도한다.
                print(f"  요약 실패, 다음 실행에서 재시도: {e}", file=sys.stderr)
                continue

            policy_id = build_policy_id(raw.source, raw.published_at, raw.url)
            item = PolicyItem(
                id=policy_id,
                category=raw.category,
                subcategory=_classify_subcategory(raw.title, raw.category),
                title=raw.title,
                source=raw.source,
                source_url=raw.url,
                file_url=raw.file_url,
                file_type=raw.file_type,
                published_at=raw.published_at,
                crawled_at=datetime.now(KST).isoformat(),
                summary=summary,
            )

            publish_policy(item)
            update_index(item)
            new_items.append((item, batch))
            seen.add(raw.url_hash)

    save_seen(seen)

    # 알림은 여기서 보내지 않는다. docs/가 CDN에 반영된 뒤 별도 단계(notify)에서 보낸다.
    _write_pending(new_items)

    print(f"완료: {len(new_items)}건 처리됨 (알림은 push 후 notify 단계에서 발송)")


def _write_pending(new_items: list[tuple[PolicyItem, str]]) -> None:
    """발행은 됐지만 아직 알림을 보내지 않은 정책 목록을 파일로 남긴다.
    이전 런에서 실패해 남은 항목이 있으면 병합한다(덮어쓰면 소실됨).
    """
    existing: list[dict] = []
    if PENDING_FILE.exists():
        try:
            existing = json.loads(PENDING_FILE.read_text(encoding="utf-8"))
        except Exception:
            existing = []

    existing_ids = {p["id"] for p in existing}
    new_entries = [
        {
            "id": item.id,
            "category": item.category,
            "subcategory": item.subcategory,
            "title": item.title,
            "batch": b,
        }
        for item, b in new_items
        if item.id not in existing_ids  # 중복 방지
    ]
    PENDING_FILE.write_text(
        json.dumps(existing + new_entries, ensure_ascii=False), encoding="utf-8"
    )


def _wait_for_cdn(policy_id: str, timeout: int = 900, interval: int = 15) -> bool:
    """상세 JSON이 CDN에 라이브될 때까지 폴링한다. timeout이면 False."""
    url = f"{CDN_BASE}policies/{quote(policy_id)}.json"
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            if requests.get(url, headers=HEADERS, timeout=15).status_code == 200:
                return True
        except Exception:
            pass
        time.sleep(interval)
    return False


def notify_pending() -> None:
    """docs/ push 이후 호출. CDN 반영을 확인한 뒤 FCM 알림을 보낸다."""
    if not PENDING_FILE.exists():
        print("대기 중인 알림 없음")
        return
    pending = json.loads(PENDING_FILE.read_text(encoding="utf-8"))
    failures = []
    for p in pending:
        # CDN 반영 확인 실패 시 알림을 보내지 않는다. 사용자가 탭했을 때 404가 뜨므로
        # 타임아웃이면 실패 목록에 넣어 CI 스텝을 실패로 표시한다.
        if not _wait_for_cdn(p["id"]):
            print(f"  CDN 반영 확인 실패(타임아웃), 알림 미발송: {p['title']}", file=sys.stderr)
            failures.append(p)
            continue
        item = PolicyItem(
            id=p["id"], category=p["category"], subcategory=p["subcategory"],
            title=p["title"], source="", source_url="",
            file_url=None, file_type=None, published_at="",
        )
        try:
            notify_new_policy(item, batch=p["batch"])
            print(f"  FCM 트리거 완료: {p['title']}")
        except Exception as e:
            print(f"  FCM 트리거 실패: {p['title']} ({e})", file=sys.stderr)
            failures.append(p)

    if failures:
        # 실패분(CDN 타임아웃 또는 FCM 트리거 오류)을 pending에 다시 써 둔다.
        # pipeline/pending_notify.json은 git에 커밋되므로 다음 자동 실행에서 재시도된다.
        PENDING_FILE.write_text(json.dumps(failures, ensure_ascii=False), encoding="utf-8")
        raise SystemExit(f"알림 발송 미완료 {len(failures)}건: {[p['id'] for p in failures]}")
    PENDING_FILE.unlink(missing_ok=True)


def _classify_subcategory(title: str, category: str) -> str:
    if category != "부동산":
        return category
    if any(k in title for k in ["청약", "분양", "공급"]):
        return "청약"
    elif any(k in title for k in ["대출", "LTV", "DSR", "금리"]):
        return "대출"
    elif any(k in title for k in ["세금", "취득세", "종부세", "양도세"]):
        return "세금"
    elif any(k in title for k in ["재개발", "재건축", "정비"]):
        return "재개발"
    elif any(k in title for k in ["전세", "월세", "임대"]):
        return "전월세"
    return "부동산"


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "notify":
        notify_pending()
    else:
        batch = os.environ.get("BATCH", "morning")
        run(batch)
