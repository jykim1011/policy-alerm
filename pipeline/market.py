"""정책 → 시장(업종·관련 종목) 영향 정보.

설계 원칙
- LLM은 **업종 key만** 고른다. ETF·기업 목록은 사람이 관리하는 sector_map.json에서만 붙인다.
  모델이 종목명·코드를 직접 쓰면 존재하지 않는 종목이나 틀린 코드가 나오기 때문이다.
- 매수·매도 권유나 주가 예측으로 읽힐 문장은 버린다(유사투자자문 소지). 화면에는 항상
  "투자 권유가 아닙니다" 고지를 함께 띄운다.
- 근거가 없으면 비운다. 복지·육아 정책 대부분은 업종이 없는 것이 정상이다.
"""
import json
import re
from functools import lru_cache
from pathlib import Path

SECTOR_MAP_PATH = Path(__file__).with_name("sector_map.json")

# 한 정책에 붙일 업종 상한. 많을수록 근거가 약해진다.
MAX_SECTORS = 3

DIRECTIONS = ("긍정", "부정", "혼재")

# 영향 경로. "직접"만 발행한다. 간접(회의·협약·캠페인, 업종이 언급만 됨)은 표본을 흐린다.
STRENGTHS = ("직접", "간접")

POLICY_TYPES = ("예산·지원", "규제 강화", "규제 완화", "세제", "금리·금융", "공급 확대", "통상·대외", "기타")

# 예측·권유로 읽히는 표현. 이런 문장은 고치지 않고 통째로 버린다.
# "매수·매도" 단독은 막지 않는다: "공매도", "주택 매수자"처럼 정책 사실을 설명하는 말에도 나온다.
_ADVICE = re.compile(
    r"(매수|매도|종목)\s*(추천|권유|기회|타이밍|시점)|사세요|파세요|사야|팔아야|담아|"
    r"비중\s*(확대|축소)|목표\s*주가|목표가|주가가?\s*(오를|내릴|상승|하락|뛸)|"
    r"오를\s*것|내릴\s*것|상승할\s*(것|전망)|하락할\s*(것|전망)|급등|급락|수익을\s*낼|투자\s*기회"
)


# 모델이 근거가 약하다고 스스로 적은 문장.
_WEAK = re.compile(r"(직접|구체적)[^.]{0,20}(관련|연관|내용)[^.]{0,10}(없|않)|확인되지\s*않|명시되지\s*않")


@lru_cache(maxsize=1)
def load_sectors() -> dict[str, dict]:
    """sector key → {key, description, etfs, stocks}. 파일 순서를 유지한다."""
    data = json.loads(SECTOR_MAP_PATH.read_text(encoding="utf-8"))
    return {s["key"]: s for s in data["sectors"]}


def sector_keys() -> list[str]:
    return list(load_sectors().keys())


def prompt_sector_list() -> str:
    """요약 프롬프트에 넣을 업종 목록("- 건설: 주택 공급·SOC…")."""
    return "\n".join(f"- {k}: {s['description']}" for k, s in load_sectors().items())


def market_schema() -> dict:
    """요약 구조화 출력 스키마의 market 부분. 업종은 enum으로 묶어 목록 밖 값을 막는다."""
    return {
        "type": "object",
        "properties": {
            "sectors": {
                "type": "array",
                "items": {
                    "type": "object",
                    "properties": {
                        "sector": {"type": "string", "enum": sector_keys()},
                        "strength": {"type": "string", "enum": list(STRENGTHS)},
                        "direction": {"type": "string", "enum": list(DIRECTIONS)},
                        "reason": {"type": "string"},
                    },
                    "required": ["sector", "strength", "direction", "reason"],
                    "additionalProperties": False,
                },
            },
            "policy_type": {"type": "string", "enum": list(POLICY_TYPES)},
            "investor_note": {"type": "string"},
        },
        "required": ["sectors", "policy_type", "investor_note"],
        "additionalProperties": False,
    }


PROMPT_GUIDE = """- market은 이 정책이 **상장 기업의 사업에 직접 영향을 줄 수 있는 업종**을 고르는 항목이에요.
  아래 업종 목록에서만 고르고, 최대 3개까지 쓰세요. **대부분의 정책은 해당 업종이 없어요. 억지로 채우지 마세요.**
  · strength "직접": 정책이 그 업종의 매출·비용·수요를 직접 바꿀 때만. 예) 업종에 예산·보조금이
    흘러감, 업종 제품·서비스의 세금·가격·규제가 바뀜, 업종 수요(주택 공급량, 대출 한도 등)가 바뀜.
  · strength "간접": 회의 개최, 협약·MOU, 캠페인, 행사, 인사, 실적 홍보, 단순 해명, 특정 기업의
    자체 투자 소개, 업종이 본문에 언급만 되는 경우. 확신이 없으면 "간접"이에요.
  · reason에 "직접 관련은 없어요", "확인되지 않아요"처럼 근거가 약하다고 쓰게 되면 그 업종은 빼세요.
  · direction: 업종 기업의 매출·비용·수요에 유리하면 "긍정", 불리하면 "부정", 엇갈리면 "혼재".
  · reason: 본문 근거(예산 규모, 규제 내용, 시행 시점)로 왜 그 업종과 관련되는지 1문장.
  · investor_note: 시장 관점에서 이 정책이 어떤 성격인지(정책 유형·규모·시점) 1문장. 업종이 없으면 빈 문자열.
  · 주가 전망, 매수·매도 권유, "오를 것" 같은 예측 표현은 절대 쓰지 마세요. 사실만 쓰세요.
  · 종목명이나 회사명은 쓰지 마세요. "본문", "요약"처럼 문서 자체를 언급하는 말도 쓰지 마세요.
  업종 목록:
{sectors}"""


def _clean_text(v) -> str:
    t = str(v).strip() if isinstance(v, str) else ""
    return "" if _ADVICE.search(t) else t


def normalize_market(raw) -> dict:
    """모델 응답의 market을 검증한다. 쓸 업종이 없으면 빈 dict.

    결과: {"sectors": [{"sector","direction","reason"}], "policy_type", "investor_note"}
    """
    if not isinstance(raw, dict):
        return {}
    known = load_sectors()
    sectors, seen = [], set()
    for s in raw.get("sectors") or []:
        if not isinstance(s, dict):
            continue
        key = s.get("sector")
        reason = _clean_text(s.get("reason"))
        if key not in known or key in seen or not reason:
            continue
        # strength가 없으면(구버전 응답·테스트) 직접으로 본다. 간접이거나 스스로 근거 없음을 밝히면 버린다.
        if s.get("strength", "직접") != "직접" or _WEAK.search(reason):
            continue
        direction = s.get("direction") if s.get("direction") in DIRECTIONS else "혼재"
        sectors.append({"sector": key, "direction": direction, "reason": reason})
        seen.add(key)
        if len(sectors) >= MAX_SECTORS:
            break
    if not sectors:
        return {}
    policy_type = raw.get("policy_type") if raw.get("policy_type") in POLICY_TYPES else "기타"
    return {
        "sectors": sectors,
        "policy_type": policy_type,
        "investor_note": _clean_text(raw.get("investor_note")),
    }


def build_market_block(market: dict) -> dict | None:
    """발행용 market 블록. 업종마다 sector_map의 ETF·관련 기업을 붙인다(LLM이 아닌 사람 관리 값)."""
    if not market or not market.get("sectors"):
        return None
    known = load_sectors()
    sectors = []
    for s in market["sectors"]:
        meta = known.get(s["sector"])
        if not meta:
            continue
        sectors.append({
            **s,
            "etfs": meta.get("etfs", []),
            "stocks": meta.get("stocks", []),
        })
    if not sectors:
        return None
    block = {"sectors": sectors, "policy_type": market.get("policy_type", "기타")}
    if market.get("investor_note"):
        block["investor_note"] = market["investor_note"]
    return block


def sector_names(market: dict | None) -> list[str]:
    return [s["sector"] for s in (market or {}).get("sectors", [])]
