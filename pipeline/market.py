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

# 신규 여부. 시장은 "처음 나온 정보"에만 반응한다. 재확인·점검 발표는 정보가 아니다.
NOVELTY = ("신규", "확대", "연장", "재확인")
NOVELTY_SCORE = {"신규": 3, "확대": 2, "연장": 1, "재확인": 0}

# 정책 단계. 실제 시행 확정에 가까울수록 불확실성이 작다.
STAGES = ("검토·계획", "발표", "입법예고", "의결·통과", "시행")

# 중요도. 사람이 검증할 수 있게 LLM이 아니라 아래 규칙(impact_of)으로 계산한다.
IMPACTS = ("높음", "보통", "낮음")
# 카드·목록 배지·관심 업종 알림을 띄우는 최소 중요도. 낮음은 데이터만 남기고 보여주지 않는다.
SHOW_IMPACT = ("높음", "보통")
# 관심 업종 알림도 보통 이상. 높음은 신규·대규모일 때만이라 드물어서(최근 40건 중 0건) 알림이 사실상 끊긴다.
NOTIFY_IMPACT = ("높음", "보통")

MAX_FIGURES = 3

# 판정 방식 버전. 백필은 이 값이 다른 정책만 다시 판정한다(1: 업종만, 2: 신규성·단계·수치·중요도).
MARKET_VERSION = 2

# 정량 수치 중 "큰 규모": 1조원 이상, 주택 1만 호(세대) 이상.
# 수혜 가구·인원 수는 기업 매출 규모가 아니므로 세지 않는다(에너지바우처 "21만 가구"가 높음으로 뜬 사례).
_TRILLION = re.compile(r"(\d+(?:[.,]\d+)?)\s*조")
_BILLION = re.compile(r"(\d[\d,]*)\s*억")
_TEN_THOUSAND = re.compile(r"(\d+(?:\.\d+)?)\s*만\s*(호|세대)")
_NOT_FIGURE = re.compile(r"과태료|벌금|과징금|대상|인원|가구|수혜|실적|누적|증가율")
_EFFECTIVE = re.compile(r"^\d{4}-\d{2}(-\d{2})?$")

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
            "novelty": {"type": "string", "enum": list(NOVELTY)},
            "stage": {"type": "string", "enum": list(STAGES)},
            "figures": {
                "type": "array",
                "items": {
                    "type": "object",
                    "properties": {"label": {"type": "string"}, "value": {"type": "string"}},
                    "required": ["label", "value"],
                    "additionalProperties": False,
                },
            },
            "effective_date": {"type": "string"},
            "investor_note": {"type": "string"},
        },
        "required": ["sectors", "policy_type", "novelty", "stage", "figures", "effective_date", "investor_note"],
        "additionalProperties": False,
    }


PROMPT_GUIDE = """- market은 이 정책이 **상장 기업의 사업에 직접 영향을 줄 수 있는 업종**을 고르는 항목이에요.
  아래 업종 목록에서만 고르고, 최대 3개까지 쓰세요. **대부분의 정책은 해당 업종이 없어요. 억지로 채우지 마세요.**
  · strength "직접": 정책이 그 업종의 매출·비용·수요를 직접 바꿀 때만. 예) 업종에 예산·보조금이
    흘러감, 업종 제품·서비스의 세금·가격·규제가 바뀜, 업종 수요(주택 공급량, 대출 한도 등)가 바뀜.
  · strength "간접": 회의 개최, 협약·MOU, 캠페인, 행사, 인사, 실적 홍보, 단순 해명, 특정 기업의
    자체 투자 소개, 업종이 본문에 언급만 되는 경우. 확신이 없으면 "간접"이에요.
  · 개인·가구에 주는 복지 급여, 바우처, 고용·취업 장려금, 일자리 예산은 기업 매출로 이어지는 경로가
    약하므로 "간접"이에요. 예산은 조달·발주·공사·기업 보조금처럼 그 업종 기업에 돈이 들어갈 때만 "직접".
  · 금융 상품·제도(대출, 신탁, 보증)는 은행·증권·보험 업종이지 건설 업종이 아니에요.
  · "그 업종 상장사의 매출이나 비용이 이렇게 바뀐다"를 한 문장으로 설명할 수 없으면 고르지 마세요.
  · reason에 "직접 관련은 없어요", "확인되지 않아요"처럼 근거가 약하다고 쓰게 되면 그 업종은 빼세요.
  · direction: 업종 기업의 매출·비용·수요에 유리하면 "긍정", 불리하면 "부정", 엇갈리면 "혼재".
  · reason: 본문 근거(예산 규모, 규제 내용, 시행 시점)로 왜 그 업종과 관련되는지 1문장.
  · novelty: 처음 나온 제도·사업이면 "신규", 기존 것의 규모·대상을 늘리면 "확대", 기한만 늘리면 "연장",
    이미 발표된 내용을 다시 알리거나 점검·회의·해명이면 "재확인". 판단이 어려우면 "재확인"이에요.
  · stage: "검토·계획"(추진 예정·검토), "발표"(정부가 방안 확정 발표), "입법예고", "의결·통과"(국무회의 의결,
    국회 통과), "시행"(이미 시행 중이거나 시행일 확정 공포).
  · figures: 투자자에게 중요한 정량 수치(예산·지원 금액, 공급량, 세율·한도 변화)만 최대 3개.
    label은 2~6자 명사(예: "예산", "공급", "세율"), value는 본문 수치 그대로(예: "3.2조원", "5만 호", "10%→5%").
    날짜, 과태료·벌금, 수혜 인원·가구 수, 지난 실적 통계는 넣지 마세요. 본문에 없는 수치는 절대 만들지 마세요.
    없으면 빈 배열.
  · effective_date: 시행·집행이 시작되는 날짜. "YYYY-MM-DD" 또는 일을 모르면 "YYYY-MM". 모르면 빈 문자열.
  · investor_note: 시장 관점에서 이 정책의 핵심을 1문장(무엇이 얼마나, 언제). 업종이 없으면 빈 문자열.
  · 주가 전망, 매수·매도 권유, "오를 것" 같은 예측 표현은 절대 쓰지 마세요. 사실만 쓰세요.
  · 종목명이나 회사명은 쓰지 마세요. "본문", "요약"처럼 문서 자체를 언급하는 말도 쓰지 마세요.
  업종 목록:
{sectors}"""


def _clean_text(v) -> str:
    t = str(v).strip() if isinstance(v, str) else ""
    return "" if _ADVICE.search(t) else t


def _figures(v) -> list[dict]:
    out = []
    for f in v or []:
        if not isinstance(f, dict):
            continue
        label, value = _clean_text(f.get("label")), _clean_text(f.get("value"))
        # 수치가 없는 값("대폭", "상당 규모")은 정량 정보가 아니다.
        if _NOT_FIGURE.search(label):
            continue
        if label and value and re.search(r"\d", value) and len(label) <= 10 and len(value) <= 24:
            out.append({"label": label, "value": value})
        if len(out) >= MAX_FIGURES:
            break
    return out


def _is_large(figures: list[dict]) -> bool:
    for f in figures:
        v = f["value"].replace(",", "")
        m = _TRILLION.search(v)
        if m and float(m.group(1)) >= 1:
            return True
        m = _BILLION.search(v)
        if m and float(m.group(1)) >= 10000:  # "1만억"은 드물지만 "12,000억원" 표기 대비
            return True
        m = _TEN_THOUSAND.search(v)
        if m and float(m.group(1)) >= 1:
            return True
    return False


def impact_of(novelty: str, stage: str, figures: list[dict]) -> tuple[str, int]:
    """중요도와 점수. 화면에 근거를 함께 보여줄 수 있게 단순한 가산 규칙으로 둔다.

    신규 3 · 확대 2 · 연장 1 · 재확인 0
    + 정량 수치 있음 1 + 큰 규모(1조원·1만 호 이상) 1
    + 의결·통과/시행 1, 검토·계획 −1
    높음 ≥ 5 이면서 큰 규모, 보통 3~4(또는 5 이상이지만 규모가 작음), 낮음 ≤ 2.
    재확인은 수치와 무관하게 낮음(새 정보가 아니므로).
    """
    score = NOVELTY_SCORE.get(novelty, 0)
    large = bool(figures) and _is_large(figures)
    if figures:
        score += 1
        if large:
            score += 1
    if stage in ("의결·통과", "시행"):
        score += 1
    elif stage == "검토·계획":
        score -= 1
    if novelty == "재확인":
        return "낮음", score
    # 높음은 "새 정보 + 큰 규모"일 때만. 작은 규제가 시행 단계라는 이유로 높음이 되지 않게 한다.
    return ("높음" if score >= 5 and large else "보통" if score >= 3 else "낮음"), score


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
    novelty = raw.get("novelty") if raw.get("novelty") in NOVELTY else "재확인"
    stage = raw.get("stage") if raw.get("stage") in STAGES else "발표"
    figures = _figures(raw.get("figures"))
    effective = str(raw.get("effective_date") or "").strip()
    impact, score = impact_of(novelty, stage, figures)
    return {
        "sectors": sectors,
        "policy_type": policy_type,
        "novelty": novelty,
        "stage": stage,
        "figures": figures,
        "effective_date": effective if _EFFECTIVE.match(effective) else "",
        "impact": impact,
        "impact_score": score,
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
    for k in ("novelty", "stage", "figures", "effective_date", "impact", "impact_score", "investor_note"):
        if market.get(k) not in (None, "", []):
            block[k] = market[k]
    return block


def sector_names(market: dict | None, impacts: tuple[str, ...] | None = None) -> list[str]:
    """업종 이름 목록. impacts를 주면 그 중요도일 때만(아니면 빈 목록).

    중요도 필드가 없는 market(구버전 데이터)은 impacts 조건을 통과시키지 않는다.
    """
    m = market or {}
    if impacts is not None and m.get("impact") not in impacts:
        return []
    return [s["sector"] for s in m.get("sectors", [])]
