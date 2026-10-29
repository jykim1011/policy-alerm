"""본문 없이 제목만으로 생성된 '부실 요약'을 첨부 원문으로 다시 요약한다.

OpenAPI가 FileUrl을 비워 보낸 기사(주로 '사실은 이렇습니다' 설명자료)는 본문도 비어 있어
제목만 보고 요약이 만들어졌다("누가 대상인가"가 비고, "구체적인 내용은 나와 있지 않습니다"
같은 답이 채워짐). 이제는 상세 페이지에서 첨부를 찾아 읽으므로, 이미 발행된 건도 소급한다.

갱신 대상: docs/policies/{id}.json(summary 전체), 목록 파일의 easy_title.
원문을 MIN_BODY_CHARS 이상 확보하지 못한 건은 그대로 둔다(재실행 안전).
함께, 모든 정책에서 저작권 고지에서 뽑힌 용어 풀이·FAQ(공공누리 등)를 지운다(LLM 호출 없음).

사용:  python -m pipeline.backfill_thin [--dry-run]
"""
import sys
import time
from pathlib import Path

from pipeline.backfill_easy_title import DOCS_ROOT, _apply_to_list, _load, _save
from pipeline.main import MIN_BODY_CHARS, _with_attachment_text
from pipeline.publisher import _summary_dict
from pipeline.summarizer import _BOILERPLATE, summarize_policy

# 원문 없이 요약했을 때 모델이 남기는 흔적("자세한 내용은 첨부파일 참고" 등).
# "명시되어 있지 않"은 충실한 요약의 FAQ에도 흔히 나와 기준으로 쓰지 않는다.
_THIN_MARKERS = ("첨부파일", "첨부자료", "보도자료에서는", "보도자료가 발표", "내용의 보도자료")


def is_thin(summary: dict | None) -> bool:
    if not summary:
        return False
    if not (summary.get("who_is_affected") or "").strip():
        return True
    blob = " ".join(
        [summary.get("what_changed", "")]
        + summary.get("key_points", [])
        + [f.get("answer", "") for f in summary.get("faq", [])]
    )
    return any(m in blob for m in _THIN_MARKERS)


def strip_boilerplate(summary: dict) -> bool:
    """저작권 고지에서 뽑힌 용어 풀이·FAQ를 지운다. 바뀌었으면 True."""
    before = (len(summary.get("glossary", [])), len(summary.get("faq", [])))
    for key, field in (("glossary", "term"), ("faq", "question")):
        if key in summary:
            summary[key] = [x for x in summary[key] if not _BOILERPLATE.search(x.get(field, ""))]
            if not summary[key]:
                del summary[key]
    return before != (len(summary.get("glossary", [])), len(summary.get("faq", [])))


def main() -> None:
    dry_run = "--dry-run" in sys.argv
    files = [f for f in (DOCS_ROOT / "policies").glob("*.json") if f.name != "index.json"]

    cleaned = 0
    for f in files:
        d = _load(f)
        if d.get("summary") and strip_boilerplate(d["summary"]):
            cleaned += 1
            if not dry_run:
                _save(f, d)
    print(f"저작권 고지 용어·FAQ 정리 {cleaned}건")
    todo = [(f, d) for f in files if is_thin((d := _load(f)).get("summary"))]
    print(f"부실 요약 {len(todo)}건")
    if dry_run:
        for _, d in todo:
            print(f"  {d['id']}  {d['title'][:50]}")
        return

    titles: dict[str, str] = {}
    fixed = skipped = 0
    for f, d in todo:
        text = _with_attachment_text("", d.get("file_url"), d.get("file_type"), d["source_url"])
        if len(text.strip()) < MIN_BODY_CHARS:
            print(f"  SKIP 원문 부족({len(text.strip())}자): {d['id']}")
            skipped += 1
            continue
        try:
            s = summarize_policy(d["title"], text, published_at=d.get("published_at", ""))
        except Exception as e:  # 일시 오류는 건너뛰고 재실행 때 다시 시도한다
            print(f"  FAIL {d['id']}: {e}")
            skipped += 1
            continue
        d["summary"] = _summary_dict(s)
        _save(f, d)
        if s.easy_title:
            titles[d["id"]] = s.easy_title
        fixed += 1
        print(f"  OK   {d['id']}")
        time.sleep(1)

    _apply_to_list(DOCS_ROOT / "policies" / "index.json", titles)
    for p in list((DOCS_ROOT / "archive").glob("[0-9]*.json")) + list((DOCS_ROOT / "categories").glob("*.json")):
        _apply_to_list(p, titles)
    print(f"\n완료: 재요약 {fixed}건 / 건너뜀 {skipped}건")


if __name__ == "__main__":
    main()
