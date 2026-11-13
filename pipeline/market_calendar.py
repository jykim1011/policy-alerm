"""투자자용 정책 모아보기: 다가오는 시행 일정 + 최근 4주 업종별 정책 흐름.

정책 카드 하나보다 "언제 무엇이 시행되나", "요즘 정부 정책이 어느 업종에 몰리나"를 모아 볼 때
가치가 크다. 외부 API 없이 docs/policies/*.json 의 market 블록만으로 만든다.

사용:  python -m pipeline.market_calendar   (main.run 과 market_stats 도 호출한다)
산출물: docs/market/calendar.json
"""
import json
from datetime import date, datetime, timedelta, timezone
from pathlib import Path

from pipeline.market import SHOW_IMPACT

KST = timezone(timedelta(hours=9))
DOCS_ROOT = Path("docs")

UPCOMING_DAYS = 180   # 이 기간 안에 시행되는 정책만
MOMENTUM_DAYS = 28    # 업종 흐름 집계 구간
# 업종 카드에 싣는 정책 상한. 건수와 목록이 어긋나면 긍정·부정 내역을 확인할 수 없으므로
# 화면에서는 3건만 먼저 보이고 나머지는 펼쳐 보게 하되, 데이터에는 전부 싣는다.
MOMENTUM_MAX_POLICIES = 50


def _load_market_policies(docs_root: Path) -> list[dict]:
    out = []
    for f in (docs_root / "policies").glob("*.json"):
        if f.name == "index.json":
            continue
        try:
            d = json.loads(f.read_text(encoding="utf-8"))
        except Exception:
            continue
        m = d.get("market") or {}
        if m.get("sectors") and m.get("impact") in SHOW_IMPACT:
            out.append(d)
    return out


def _entry(d: dict) -> dict:
    m = d["market"]
    return {
        "policy_id": d["id"],
        "title": (d.get("summary") or {}).get("easy_title") or d["title"],
        "published_at": d["published_at"][:10],
        "sectors": [s["sector"] for s in m["sectors"]],
        "directions": {s["sector"]: s.get("direction", "혼재") for s in m["sectors"]},
        "impact": m["impact"],
        **{k: m[k] for k in ("novelty", "stage", "figures") if m.get(k)},
    }


def _effective_key(eff: str) -> str:
    """정렬·비교용 날짜. 월만 있으면("2027-01") 그 달 1일로 본다."""
    return eff if len(eff) == 10 else f"{eff}-01"


def build_calendar(policies: list[dict], today: date) -> dict:
    today_s = today.isoformat()
    month_s = today_s[:7]
    horizon = (today + timedelta(days=UPCOMING_DAYS)).isoformat()

    upcoming = []
    for d in policies:
        eff = d["market"].get("effective_date") or ""
        if not eff:
            continue
        # 일자를 아는 건 오늘 이후, 월만 아는 건 이번 달 이후.
        if (len(eff) == 10 and eff < today_s) or (len(eff) == 7 and eff < month_s):
            continue
        if _effective_key(eff) > horizon:
            continue
        upcoming.append({"effective_date": eff, "date_exact": len(eff) == 10, **_entry(d)})
    # 같은 달이면 일자 확정 건을 먼저, 그다음 중요도 높은 순.
    upcoming.sort(key=lambda e: (_effective_key(e["effective_date"]), e["impact"] != "높음"))

    since = (today - timedelta(days=MOMENTUM_DAYS)).isoformat()
    groups: dict[str, list[dict]] = {}
    for d in policies:
        if d["published_at"][:10] < since:
            continue
        e = _entry(d)
        for s in e["sectors"]:
            groups.setdefault(s, []).append(e)
    momentum = []
    for sector, rows in groups.items():
        rows.sort(key=lambda e: (e["impact"] == "높음", e["published_at"]), reverse=True)
        dirs = [r["directions"][sector] for r in rows]
        momentum.append({
            "sector": sector,
            "count": len(rows),
            "high": sum(1 for r in rows if r["impact"] == "높음"),
            "positive": dirs.count("긍정"),
            "negative": dirs.count("부정"),
            "mixed": dirs.count("혼재"),
            "policies": [
                {k: r[k] for k in ("policy_id", "title", "published_at", "impact")}
                | {"direction": r["directions"][sector]}
                for r in rows[:MOMENTUM_MAX_POLICIES]
            ],
        })
    momentum.sort(key=lambda m: (m["count"], m["high"]), reverse=True)

    return {
        "updated_at": datetime.now(KST).isoformat(),
        "today": today_s,
        "upcoming_days": UPCOMING_DAYS,
        "momentum_days": MOMENTUM_DAYS,
        "upcoming": upcoming,
        "momentum": momentum,
    }


def write_calendar(docs_root: Path | str = DOCS_ROOT, today: date | None = None) -> dict:
    docs_root = Path(docs_root)
    today = today or datetime.now(KST).date()
    cal = build_calendar(_load_market_policies(docs_root), today)
    out = docs_root / "market"
    out.mkdir(parents=True, exist_ok=True)
    (out / "calendar.json").write_text(json.dumps(cal, ensure_ascii=False, indent=2), encoding="utf-8")
    return cal


if __name__ == "__main__":
    c = write_calendar()
    print(f"시행 예정 {len(c['upcoming'])}건, 업종 흐름 {len(c['momentum'])}개")
