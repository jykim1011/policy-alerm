"""시장 데이터 일일 갱신: 업종 목록, 정책 발표 후 주가 반응 통계, 관련 기업 최근 공시.

사용:  python -m pipeline.market_stats
산출물(docs/market/, GitHub Pages로 앱·웹에 서비스):
  - sectors.json      업종 목록과 ETF·관련 기업(앱의 관심 업종 설정 화면용). 항상 갱신한다.
  - reactions.json    정책×업종별 발표 후 1·5·20거래일 수익률과 KOSPI200 대비 초과수익률.
  - sector_stats.json 업종(×방향)별 과거 반응 집계("비슷한 정책 N건 발표 후 평균").
  - disclosures.json  관련 기업의 최근 30일 DART 공시.

키가 없거나 해당 API 활용신청이 안 돼 있으면 그 단계만 건너뛴다(CI를 깨지 않는다).
  - DATA_GO_KR_KEY: 금융위원회_주식시세정보, 금융위원회_증권상품시세정보 활용신청 필요
  - DART_API_KEY:   opendart.fss.or.kr 인증키

통계는 과거 사실의 집계일 뿐 예측이 아니다. 화면에도 그렇게 고지한다.
"""
import io
import json
import os
import sys
import time
import zipfile
from datetime import date, datetime, timedelta, timezone
from pathlib import Path
from xml.etree import ElementTree

import requests

from pipeline.market import load_sectors

KST = timezone(timedelta(hours=9))
DOCS_ROOT = Path("docs")
MARKET_DIR = DOCS_ROOT / "market"

# 시장 비교 기준(KODEX 200). 업종 수익률에서 이 값을 빼 "시장 대비" 움직임을 본다.
MARKET_BENCHMARK = "069500"

# 발표 후 관찰 구간(거래일). 20거래일 ≈ 한 달.
HORIZONS = (1, 5, 20)

# 집계를 보여줄 최소 표본. 이보다 적으면 우연과 구분되지 않는다.
MIN_SAMPLES = 3

# V2 엔드포인트. 구버전(/service/…/getStockPriceInfo)은 V2로 신청한 키로 부르면 "등록되지 않은 서비스키"가 난다.
STOCK_API = "https://apis.data.go.kr/1160100/GetStockSecuritiesInfoService_V2/getStockPriceInfo_V2"
ETF_API = "https://apis.data.go.kr/1160100/GetSecuritiesProductInfoService_V2/getETFPriceInfo_V2"
DART_CORP_CODE = "https://opendart.fss.or.kr/api/corpCode.xml"
DART_LIST = "https://opendart.fss.or.kr/api/list.json"
DART_VIEW = "https://dart.fss.or.kr/dsaf001/main.do?rcpNo="


class PriceApiUnavailable(RuntimeError):
    """키 미설정·미등록 등으로 시세 API를 쓸 수 없다. 반응 통계 단계를 통째로 건너뛴다."""


def _now() -> str:
    return datetime.now(KST).isoformat()


def _write(name: str, data) -> None:
    MARKET_DIR.mkdir(parents=True, exist_ok=True)
    (MARKET_DIR / name).write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")


def _read(name: str, default):
    p = MARKET_DIR / name
    if not p.exists():
        return default
    try:
        return json.loads(p.read_text(encoding="utf-8"))
    except Exception:
        return default


# ── 1. 업종 목록 ────────────────────────────────────────────────────────────

def write_sectors() -> None:
    _write("sectors.json", {
        "updated_at": _now(),
        "sectors": [
            {"key": s["key"], "description": s["description"], "etfs": s["etfs"], "stocks": s["stocks"]}
            for s in load_sectors().values()
        ],
    })


# ── 2. 발표 후 주가 반응 ───────────────────────────────────────────────────

class PriceClient:
    """금융위원회 시세 API(종가). 같은 (코드, 구간)은 한 번만 부른다."""

    def __init__(self, key: str, etf_codes: set[str], session=None):
        self._key = key
        self._etf = etf_codes
        self._s = session or requests.Session()
        self._cache: dict[tuple, dict[str, float]] = {}

    def closes(self, code: str, begin: date, end: date) -> dict[str, float]:
        """{YYYYMMDD: 종가}. 거래일만 들어 있다."""
        ck = (code, begin, end)
        if ck in self._cache:
            return self._cache[ck]
        url = ETF_API if code in self._etf else STOCK_API
        out: dict[str, float] = {}
        page = 1
        while True:
            params = {
                "serviceKey": self._key, "resultType": "json", "numOfRows": 200, "pageNo": page,
                "likeSrtnCd": code,
                "beginBasDt": begin.strftime("%Y%m%d"), "endBasDt": end.strftime("%Y%m%d"),
            }
            resp = self._s.get(url, params=params, timeout=20)
            try:
                body = resp.json()
            except ValueError:
                # 미등록 키는 resultType과 무관하게 XML 오류를 돌려준다.
                raise PriceApiUnavailable(resp.text[:200])
            if "response" not in body:
                raise PriceApiUnavailable(json.dumps(body, ensure_ascii=False)[:200])
            header = body["response"].get("header", {})
            if header.get("resultCode") not in ("00", "0", None):
                raise PriceApiUnavailable(f"{header.get('resultCode')} {header.get('resultMsg')}")
            b = body["response"].get("body", {})
            items = (b.get("items") or {}).get("item") or []
            if isinstance(items, dict):
                items = [items]
            for it in items:
                if str(it.get("srtnCd", "")).lstrip("A") != code:
                    continue  # likeSrtnCd는 부분 일치라 다른 종목이 섞일 수 있다
                try:
                    out[str(it["basDt"])] = float(str(it["clpr"]).replace(",", ""))
                except (KeyError, ValueError):
                    continue
            total = int(b.get("totalCount") or 0)
            if page * 200 >= total:
                break
            page += 1
        self._cache[ck] = out
        return out


def _basket(sector: dict) -> list[str]:
    """업종 대표 바스켓: 업종 ETF가 있으면 그 ETF, 없으면 관련 기업 동일가중."""
    if sector.get("etfs"):
        return [sector["etfs"][0]["code"]]
    return [s["code"] for s in sector.get("stocks", [])]


def event_returns(closes: dict[str, float], pub: date) -> dict:
    """발표일 기준 수익률. 기준가는 발표일 **전** 마지막 거래일 종가(발표 당일 반응 포함).

    반환: {"d1": .., "d5": .., "d20": ..} — 아직 그만큼 거래일이 지나지 않았으면 키가 없다.
    """
    days = sorted(closes)
    key = pub.strftime("%Y%m%d")
    before = [d for d in days if d < key]
    after = [d for d in days if d >= key]
    if not before:
        return {}
    base = closes[before[-1]]
    out = {}
    for h in HORIZONS:
        if len(after) >= h:
            out[f"d{h}"] = round(closes[after[h - 1]] / base - 1, 4)
    return out


def _avg(xs):
    return round(sum(xs) / len(xs), 4) if xs else None


def basket_returns(client: PriceClient, codes: list[str], pub: date) -> dict:
    """바스켓 동일가중 수익률. 일부 종목 시세가 없으면 있는 종목만으로 낸다."""
    begin, end = pub - timedelta(days=14), min(pub + timedelta(days=45), date.today())
    per = [event_returns(client.closes(c, begin, end), pub) for c in codes]
    per = [r for r in per if r]
    out = {}
    for h in HORIZONS:
        vals = [r[f"d{h}"] for r in per if f"d{h}" in r]
        # 바스켓 일부만 값이 있으면 다른 날과 비교가 어긋나므로 전 종목이 있을 때만 쓴다.
        if vals and len(vals) == len(per):
            out[f"d{h}"] = _avg(vals)
    return out


def _policies_with_market() -> list[dict]:
    out = []
    for f in sorted((DOCS_ROOT / "policies").glob("*.json")):
        if f.name == "index.json":
            continue
        try:
            d = json.loads(f.read_text(encoding="utf-8"))
        except Exception:
            continue
        if (d.get("market") or {}).get("sectors"):
            out.append(d)
    return out


def update_reactions(client: PriceClient, policies: list[dict], today: date | None = None) -> dict:
    """정책×업종 반응을 채운다. 20거래일까지 다 찬(complete) 항목은 다시 부르지 않는다."""
    today = today or date.today()
    known = load_sectors()
    data = _read("reactions.json", {"items": {}})
    items: dict = data.get("items", {})
    for p in policies:
        pub = date.fromisoformat(p["published_at"][:10])
        if pub >= today:
            continue
        for s in p["market"]["sectors"]:
            k = f"{p['id']}|{s['sector']}"
            if items.get(k, {}).get("complete"):
                continue
            meta = known.get(s["sector"])
            if not meta:
                continue
            r = basket_returns(client, _basket(meta), pub)
            if not r:
                continue
            m = basket_returns(client, [MARKET_BENCHMARK], pub)
            entry = {
                "policy_id": p["id"],
                "title": (p.get("summary") or {}).get("easy_title") or p["title"],
                "date": p["published_at"][:10],
                "sector": s["sector"],
                "direction": s.get("direction", "혼재"),
                **r,
            }
            for h in HORIZONS:
                kk = f"d{h}"
                if kk in r and kk in m:
                    entry[f"x{h}"] = round(r[kk] - m[kk], 4)  # KOSPI200 대비 초과수익률
            entry["complete"] = f"d{HORIZONS[-1]}" in r
            items[k] = entry
    data = {"updated_at": _now(), "items": items}
    _write("reactions.json", data)
    return data


def build_sector_stats(reactions: dict) -> dict:
    """업종별·방향별 5거래일 반응 집계. 표본이 MIN_SAMPLES 미만이면 수치를 싣지 않는다."""
    groups: dict[str, list[dict]] = {}
    for e in reactions.get("items", {}).values():
        if "d5" in e:
            groups.setdefault(e["sector"], []).append(e)

    def _agg(rows: list[dict]) -> dict:
        d5 = [r["d5"] for r in rows]
        x5 = [r["x5"] for r in rows if "x5" in r]
        d20 = [r["d20"] for r in rows if "d20" in r]
        out = {"count": len(rows)}
        if len(rows) >= MIN_SAMPLES:
            out.update({
                "avg_d5": _avg(d5),
                "avg_x5": _avg(x5) if len(x5) >= MIN_SAMPLES else None,
                "up_ratio_d5": round(sum(1 for v in d5 if v > 0) / len(d5), 2),
                "avg_d20": _avg(d20) if len(d20) >= MIN_SAMPLES else None,
            })
        return out

    sectors = {}
    for key, rows in groups.items():
        rows.sort(key=lambda r: r["date"], reverse=True)
        by_dir = {}
        for d in ("긍정", "부정", "혼재"):
            sub = [r for r in rows if r["direction"] == d]
            if sub:
                by_dir[d] = _agg(sub)
        sectors[key] = {
            **_agg(rows),
            "by_direction": by_dir,
            "recent": [
                {k: r[k] for k in ("policy_id", "title", "date", "direction", "d5") if k in r}
                for r in rows[:5]
            ],
        }
    stats = {"updated_at": _now(), "horizon_days": 5, "min_samples": MIN_SAMPLES, "sectors": sectors}
    _write("sector_stats.json", stats)
    return stats


# ── 3. DART 공시 ───────────────────────────────────────────────────────────

def _dart_corp_codes(key: str, session) -> dict[str, str]:
    """종목코드 → DART 고유번호(corp_code)."""
    resp = session.get(DART_CORP_CODE, params={"crtfc_key": key}, timeout=60)
    resp.raise_for_status()
    with zipfile.ZipFile(io.BytesIO(resp.content)) as z:
        xml = z.read(z.namelist()[0])
    root = ElementTree.fromstring(xml)
    out = {}
    for node in root.iter("list"):
        sc = (node.findtext("stock_code") or "").strip()
        if sc:
            out[sc] = (node.findtext("corp_code") or "").strip()
    return out


def update_disclosures(key: str, session=None, days: int = 30, per_stock: int = 8) -> dict:
    session = session or requests.Session()
    corp = _dart_corp_codes(key, session)
    end = date.today()
    begin = end - timedelta(days=days)
    codes = sorted({s["code"] for sec in load_sectors().values() for s in sec["stocks"]})
    stocks = {}
    for code in codes:
        cc = corp.get(code)
        if not cc:
            continue
        resp = session.get(DART_LIST, params={
            "crtfc_key": key, "corp_code": cc,
            "bgn_de": begin.strftime("%Y%m%d"), "end_de": end.strftime("%Y%m%d"),
            "page_count": per_stock,
        }, timeout=20)
        body = resp.json()
        if body.get("status") == "013":  # 조회된 데이터 없음
            continue
        if body.get("status") != "000":
            print(f"  DART {code} 실패: {body.get('status')} {body.get('message')}", file=sys.stderr)
            continue
        stocks[code] = [
            {
                "title": it["report_nm"].strip(),
                "date": f"{it['rcept_dt'][:4]}-{it['rcept_dt'][4:6]}-{it['rcept_dt'][6:8]}",
                "url": DART_VIEW + it["rcept_no"],
            }
            for it in body.get("list", [])[:per_stock]
        ]
        time.sleep(0.1)
    data = {"updated_at": _now(), "days": days, "stocks": stocks}
    _write("disclosures.json", data)
    return data


# ── 진입점 ────────────────────────────────────────────────────────────────

def main() -> None:
    write_sectors()
    print("sectors.json 갱신")

    key = (os.environ.get("DATA_GO_KR_KEY") or "").strip()
    if key:
        etf_codes = {e["code"] for s in load_sectors().values() for e in s["etfs"]} | {MARKET_BENCHMARK}
        try:
            reactions = update_reactions(PriceClient(key, etf_codes), _policies_with_market())
            stats = build_sector_stats(reactions)
            print(f"반응 {len(reactions['items'])}건, 업종 통계 {len(stats['sectors'])}개 갱신")
        except PriceApiUnavailable as e:
            print(f"시세 API 사용 불가(활용신청 확인), 반응 통계 건너뜀: {e}", file=sys.stderr)
    else:
        print("DATA_GO_KR_KEY 없음, 반응 통계 건너뜀", file=sys.stderr)

    dart = (os.environ.get("DART_API_KEY") or "").strip()
    if dart:
        try:
            d = update_disclosures(dart)
            print(f"공시 {sum(len(v) for v in d['stocks'].values())}건 갱신")
        except Exception as e:
            print(f"DART 공시 갱신 실패, 건너뜀: {e}", file=sys.stderr)
    else:
        print("DART_API_KEY 없음, 공시 건너뜀", file=sys.stderr)


if __name__ == "__main__":
    main()
