"""기존 정책에 쉬운 제목(easy_title)을 소급 생성한다.

보도자료 원제목("[보도자료] ○○ 장관, △△ 현장 점검")은 시민이 읽기 어렵다. 새 정책은
summarizer 가 easy_title 을 함께 만들지만, 이미 발행된 정책에는 없으므로 원제목 +
기존 요약(what_changed)을 근거로 묶음 단위로 생성해 채운다.

갱신 대상: docs/policies/{id}.json(summary.easy_title), docs/policies/index.json,
docs/archive/{year}.json 의 각 항목(easy_title). 이미 있는 항목은 건너뛴다(재실행 안전).

사용:  python -m pipeline.backfill_easy_title [개수]
  - 인자가 없으면 전체, 숫자를 주면 최신순 그 개수만 처리.
"""
import json
import os
import sys
import time
from pathlib import Path

import google.generativeai as genai

from pipeline.summarizer import _easy_title, _parse_json

DOCS_ROOT = Path("docs")
BATCH = 25

_PROMPT = """당신은 정부 보도자료 제목을 시민 눈높이로 다시 쓰는 편집자입니다.
아래 각 정책의 원제목과 요약을 보고, 시민 입장에서 무엇이 달라지는지 한눈에 알 수 있는
쉬운 제목을 지어 주세요.

규칙:
- 28자 이내. 존댓말 서술형 종결(~해요/~돼요) 또는 명사형.
- "[보도자료]", "[참고]" 같은 머리표, 장관·총리 이름, "개최", "점검", "추진" 같은 행정 용어는 빼세요.
- 요약에 없는 혜택이나 수치를 지어내지 마세요.
- 예) "한성숙 국무총리 주택공급현장 점검" → "3기 신도시 입주, 더 빨라질 수 있어요"

JSON 객체 하나로만 답하세요. 키는 입력의 id, 값은 쉬운 제목입니다.

입력:
{items}"""


def _load(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def _save(path: Path, data: dict) -> None:
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")


def _generate(model, batch: list[dict]) -> dict[str, str]:
    items = "\n".join(
        json.dumps({"id": d["id"], "title": d["title"],
                    "summary": (d.get("summary") or {}).get("what_changed", "")[:300]},
                   ensure_ascii=False)
        for d in batch
    )
    resp = model.generate_content(_PROMPT.format(items=items), request_options={"timeout": 120})
    raw = _parse_json(resp.text)
    return {k: t for k, v in raw.items() if (t := _easy_title(v))}


def _apply_to_list(path: Path, titles: dict[str, str]) -> None:
    if not path.exists():
        return
    data = _load(path)
    changed = False
    for it in data.get("items", []):
        t = titles.get(it.get("id"))
        if t and it.get("easy_title") != t:
            it["easy_title"] = t
            changed = True
    if changed:
        _save(path, data)


def main() -> None:
    limit = int(sys.argv[1]) if len(sys.argv) > 1 else None
    genai.configure(api_key=os.environ["GEMINI_API_KEY"].strip())
    model = genai.GenerativeModel("gemini-2.5-flash")

    files = sorted(
        (f for f in (DOCS_ROOT / "policies").glob("*.json") if f.name != "index.json"),
        key=lambda f: _load(f).get("published_at", ""),
        reverse=True,
    )
    if limit:
        files = files[:limit]

    # 이미 쉬운 제목이 있는 정책도 목록 파일 동기화를 위해 수집한다.
    titles: dict[str, str] = {}
    todo: list[tuple[Path, dict]] = []
    for f in files:
        d = _load(f)
        s = d.get("summary")
        if not s:
            continue
        if s.get("easy_title"):
            titles[d["id"]] = s["easy_title"]
        else:
            todo.append((f, d))

    print(f"대상 {len(todo)}건 (이미 있음 {len(titles)}건)")
    failed = 0
    for i in range(0, len(todo), BATCH):
        chunk = todo[i:i + BATCH]
        try:
            got = _generate(model, [d for _, d in chunk])
        except Exception as e:  # 일시 오류는 건너뛰고 재실행 때 다시 시도한다
            print(f"  FAIL batch {i // BATCH}: {e}")
            failed += len(chunk)
            continue
        for f, d in chunk:
            t = got.get(d["id"])
            if not t:
                failed += 1
                continue
            d["summary"]["easy_title"] = t
            _save(f, d)
            titles[d["id"]] = t
        print(f"  OK   batch {i // BATCH}: {len(got)}/{len(chunk)}")
        time.sleep(1)

    _apply_to_list(DOCS_ROOT / "policies" / "index.json", titles)
    for year_file in (DOCS_ROOT / "archive").glob("[0-9]*.json"):
        _apply_to_list(year_file, titles)

    print(f"\n완료: 쉬운 제목 {len(titles)}건 / 실패 {failed}건")


if __name__ == "__main__":
    main()
