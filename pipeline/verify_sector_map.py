"""sector_map.json 의 종목코드·이름을 금융위원회 시세 API와 대조한다.

사용:  DATA_GO_KR_KEY=... python -m pipeline.verify_sector_map
코드가 없거나(상장폐지·오타) 이름이 다르면 목록으로 출력하고 종료코드 1.
"""
import os
import sys
from datetime import date, timedelta

import requests

from pipeline.market import load_sectors
from pipeline.market_stats import ETF_API, STOCK_API


def latest(session, key: str, code: str, etf: bool) -> dict | None:
    resp = session.get(ETF_API if etf else STOCK_API, params={
        "serviceKey": key, "resultType": "json", "numOfRows": 20, "likeSrtnCd": code,
        "beginBasDt": (date.today() - timedelta(days=14)).strftime("%Y%m%d"),
    }, timeout=20)
    items = (resp.json()["response"]["body"].get("items") or {}).get("item") or []
    items = [i for i in items if str(i.get("srtnCd", "")).lstrip("A") == code]
    return max(items, key=lambda i: i["basDt"]) if items else None


def _norm(name: str) -> str:
    return name.replace(" ", "").upper()


def main() -> None:
    key = os.environ["DATA_GO_KR_KEY"].strip()
    s = requests.Session()
    problems = 0
    for sec in load_sectors().values():
        for t, etf in [(e, True) for e in sec["etfs"]] + [(x, False) for x in sec["stocks"]]:
            it = latest(s, key, t["code"], etf)
            if it is None:
                print(f"[없음] {sec['key']} {t['code']} {t['name']}")
                problems += 1
            elif _norm(it["itmsNm"]) != _norm(t["name"]):
                print(f"[이름] {sec['key']} {t['code']} 매핑={t['name']} / 시세={it['itmsNm']}")
                problems += 1
            else:
                print(f"  OK  {t['code']} {t['name']}  종가 {it['clpr']} ({it['basDt']})")
    print(f"\n문제 {problems}건")
    sys.exit(1 if problems else 0)


if __name__ == "__main__":
    main()
