import os
import sys
import requests
from datetime import datetime, timezone, timedelta

from pipeline.crawler import CRAWLERS, load_seen, save_seen, is_new_policy
from pipeline.extractor import extract_text
from pipeline.summarizer import summarize_policy
from pipeline.publisher import build_policy_id, publish_policy, update_index
from pipeline.notifier import notify_new_policy
from pipeline.models import PolicyItem

KST = timezone(timedelta(hours=9))
HEADERS = {"User-Agent": "Mozilla/5.0 (compatible; PolicyBot/1.0)"}


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
                category="부동산",
                subcategory=_classify_subcategory(raw.title),
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

    # 모든 JSON 커밋 후 Firestore 알림
    for item, b in new_items:
        try:
            notify_new_policy(item, batch=b)
            print(f"  FCM 트리거 완료: {item.title}")
        except Exception as e:
            print(f"  FCM 트리거 실패: {e}", file=sys.stderr)

    print(f"완료: {len(new_items)}건 처리됨")


def _classify_subcategory(title: str) -> str:
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
    batch = os.environ.get("BATCH", "morning")
    run(batch)
