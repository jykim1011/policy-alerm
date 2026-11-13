"""기존 docs/policies/*.json 에 시장 영향(market) 블록을 소급 생성한다.

원문은 저장돼 있지 않으므로 기존 요약(무엇이·누가·언제·핵심·배경)을 근거로 업종만 고른다.
과거 정책에 업종이 붙어야 "비슷한 정책 발표 후 주가 반응" 통계(market_stats)의 표본이 생긴다.

사용:  python -m pipeline.backfill_market [개수]
  - 인자가 없으면 전체, 숫자를 주면 최신순 그 개수만.
  - 현재 판정 방식(market_version)으로 이미 판정한 정책은 건너뛴다(재실행 안전).
"""
import json
import sys
import time
from pathlib import Path

from pipeline import market as mkt
from pipeline.llm import ClaudeModel
from pipeline.summarizer import _parse_json

DOCS_ROOT = "docs"

_PROMPT = """정부 정책 요약을 읽고, 이 정책과 관련된 상장 기업 업종을 판정해 JSON으로만 답하세요.

제목: {title}
발행일: {published_at}

요약:
{text}

작성 지침(모든 문장은 해요체):
{guide}"""

_SCHEMA = {
    "type": "object",
    "properties": {"market": mkt.market_schema()},
    "required": ["market"],
    "additionalProperties": False,
}


def _source_text(s: dict) -> str:
    parts = [
        s.get("what_changed", ""),
        s.get("who_is_affected", ""),
        s.get("when_effective", "") or "",
        s.get("background", ""),
        *s.get("key_points", []),
    ]
    return "\n".join(p for p in parts if p)


def tag_policy(d: dict, model) -> dict:
    """정책 JSON 하나에 market을 판정해 붙인 결과를 돌려준다(업종 없으면 market 키 없음)."""
    s = d.get("summary") or {}
    raw = _parse_json(model.complete(
        _PROMPT.format(
            title=d["title"],
            published_at=d.get("published_at", "")[:10],
            text=_source_text(s),
            guide=mkt.PROMPT_GUIDE.format(sectors=mkt.prompt_sector_list()),
        ),
        schema=_SCHEMA,
        timeout=60,
    ))
    block = mkt.build_market_block(mkt.normalize_market(raw.get("market")))
    d = {k: v for k, v in d.items() if k not in ("market", "market_checked")}
    if block:
        d["market"] = block
    d["market_version"] = mkt.MARKET_VERSION  # 업종 없음도 판정 결과다. 다음 실행에서 다시 묻지 않는다.
    return d


def _update_index_sectors(sectors_by_id: dict[str, list[str]]) -> None:
    """목록(index·archive)의 sectors 필드를 맞춘다."""
    root = Path(DOCS_ROOT)
    targets = [root / "policies" / "index.json", *sorted((root / "archive").glob("[0-9]*.json"))]
    for path in targets:
        if not path.exists():
            continue
        data = json.loads(path.read_text(encoding="utf-8"))
        changed = False
        for it in data.get("items", []):
            if it["id"] in sectors_by_id:
                names = sectors_by_id[it["id"]]
                if names:
                    it["sectors"] = names
                else:
                    it.pop("sectors", None)
                changed = True
        if changed:
            path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")


def main() -> None:
    limit = int(sys.argv[1]) if len(sys.argv) > 1 else None
    files = sorted(
        (f for f in (Path(DOCS_ROOT) / "policies").glob("*.json") if f.name != "index.json"),
        key=lambda f: json.loads(f.read_text(encoding="utf-8")).get("published_at", ""),
        reverse=True,
    )
    model = ClaudeModel()
    done = tagged = skipped = failed = 0
    sectors_by_id: dict[str, list[str]] = {}
    for f in files:
        if limit is not None and done >= limit:
            break
        d = json.loads(f.read_text(encoding="utf-8"))
        if d.get("market_version") == mkt.MARKET_VERSION or not d.get("summary"):
            skipped += 1
            continue
        try:
            d = tag_policy(d, model)
        except Exception as e:
            print(f"  FAIL {f.name}: {e}")
            failed += 1
            continue
        f.write_text(json.dumps(d, ensure_ascii=False, indent=2), encoding="utf-8")
        names = mkt.sector_names(d.get("market"), mkt.SHOW_IMPACT)
        sectors_by_id[d["id"]] = names
        done += 1
        tagged += bool(names)
        m = d.get("market") or {}
        print(f"  OK   {f.name}  {m.get('impact', '-')}({m.get('impact_score', '')}) "
              f"{m.get('novelty', '')}/{m.get('stage', '')} {mkt.sector_names(m)} {m.get('figures', '')}")
        time.sleep(0.3)

    _update_index_sectors(sectors_by_id)
    print(f"\n판정 {done}건(카드 노출 {tagged}) / 건너뜀 {skipped} / 실패 {failed}")


if __name__ == "__main__":
    main()
