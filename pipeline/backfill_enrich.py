"""기존 docs/policies/*.json 에 시민 가치 보강 필드(background·eligibility·how_to_apply·
faq·glossary)를 소급 생성한다.

원문 텍스트는 저장돼 있지 않으므로, 기존 요약(what_changed·who·when·key_points)을
source 텍스트로 삼아 보강 필드만 생성한다. 기존 핵심 4필드는 그대로 보존한다.

사용:  python -m pipeline.backfill_enrich [개수]
  - 인자가 없으면 전체, 숫자를 주면 최신순 그 개수만 처리.
  - 이미 보강된(faq/eligibility 보유) 항목은 건너뛴다(재실행 안전).
"""
import json
import sys
import time
from pathlib import Path

from pipeline.summarizer import summarize_policy

DOCS_ROOT = "docs"


def _source_text(s: dict) -> str:
    parts = [
        s.get("what_changed", ""),
        s.get("who_is_affected", ""),
        s.get("when_effective", "") or "",
        *s.get("key_points", []),
    ]
    return "\n".join(p for p in parts if p)


def main() -> None:
    limit = int(sys.argv[1]) if len(sys.argv) > 1 else None
    policies_dir = Path(DOCS_ROOT) / "policies"

    files = [f for f in policies_dir.glob("*.json") if f.name != "index.json"]
    # 최신순(파일명에 날짜 포함) 정렬 후 limit 적용 — 홈에 보이는 최신부터 보강.
    files.sort(reverse=True)
    if limit:
        files = files[:limit]

    done = skipped = failed = 0
    for f in files:
        d = json.loads(f.read_text(encoding="utf-8"))
        s = d.get("summary")
        if not s:
            skipped += 1
            continue
        # 이미 보강됨 → 건너뜀(재실행 안전)
        if s.get("faq") or s.get("eligibility") or s.get("glossary"):
            skipped += 1
            continue
        try:
            enriched = summarize_policy(d["title"], _source_text(s))
        except Exception as e:  # 일시 오류는 건너뛰고 다음 실행에서 재시도
            print(f"  FAIL {f.name}: {e}")
            failed += 1
            continue

        # 기존 핵심 4필드는 보존하고 새 필드만 머지
        if enriched.background:
            s["background"] = enriched.background
        if enriched.eligibility:
            s["eligibility"] = enriched.eligibility
        if enriched.how_to_apply:
            s["how_to_apply"] = enriched.how_to_apply
        if enriched.faq:
            s["faq"] = enriched.faq
        if enriched.glossary:
            s["glossary"] = enriched.glossary

        d["summary"] = s
        f.write_text(json.dumps(d, ensure_ascii=False, indent=2), encoding="utf-8")
        done += 1
        print(f"  OK   {f.name}  (+{len(enriched.faq)}FAQ {len(enriched.glossary)}용어)")
        time.sleep(0.5)  # 레이트리밋 여유

    print(f"\n보강 완료: {done}건 / 건너뜀 {skipped} / 실패 {failed}")


if __name__ == "__main__":
    main()
