import json
import os
import re
from typing import Optional
import google.generativeai as genai
from pipeline.models import PolicySummary

_PROMPT_TEMPLATE = """당신은 정부 보도자료를 일반 시민이 이해하기 쉽게 풀어주는 정책 해설가입니다.
다음 정책 문서를 분석하여 JSON 형식으로만 응답하세요. 설명 없이 JSON만 출력하세요.

제목: {title}
발행일: {published_at}

내용:
{text}

작성 지침:
- 쉬운 우리말로, 공무원 용어를 풀어서 설명하세요.
- **문서 본문에 근거가 없는 내용은 절대 지어내지 마세요.** 근거가 없으면 빈 배열([]) 또는 null로 두세요.
- **날짜는 위 발행일을 기준으로 해석하세요.** 본문에 "7.16.", "'25.3.2." 처럼 연도가 없거나
  축약된 날짜가 나오면 발행일의 연도로 해석하고, 연도를 임의로 추측하지 마세요.
- who_is_affected는 기대효과나 좋은 점이 아니라 **누가 해당되는지(대상 범위)** 를 쓰세요.
  독자가 "나는 해당되나?"를 판단할 수 있어야 합니다. "전 국민", "모든 국민" 같은
  범용 표현은 본문이 실제로 전 국민 대상일 때만 쓰세요.
- glossary는 **이 문서를 이해하는 데 꼭 필요한 제도·법령·전문 용어만 최대 5개**만 쓰세요.
  부처명·기관명·직책명(예: 고용노동부, 항만국장), 일상어(예: 숙원사업, 답보상태),
  문서 형식어(예: 붙임, 보도자료), 저작권·이용약관 문구는 절대 넣지 마세요.
  넣을 것이 없으면 빈 배열로 두세요.
- faq는 시민이 실제로 궁금해할 질문과 본문 근거에 기반한 답으로 작성하되,
  이미 위 항목에서 답한 내용을 되풀이하지 말고 **새로 알려주는 것만** 담으세요.

응답 형식:
{{
  "what_changed": "무엇이 바뀌었는지 1-2문장 (구체적 수치 포함)",
  "who_is_affected": "누가 대상인지 1-2문장 (대상 범위·조건 중심)",
  "when_effective": "언제부터 적용되는지. 본문에 시점 근거가 없으면 빈 문자열",
  "key_points": ["핵심 포인트 1", "핵심 포인트 2", "핵심 포인트 3"],
  "background": "이 정책이 나온 배경·이유 1-2문장 (본문 근거 없으면 빈 문자열)",
  "eligibility": ["대상에 해당하는지 스스로 확인할 수 있는 조건들 (없으면 빈 배열)"],
  "how_to_apply": "신청 방법·창구·기간 등 (해당 없으면 null)",
  "faq": [
    {{"question": "예상 질문", "answer": "본문 근거에 기반한 답변"}}
  ],
  "glossary": [
    {{"term": "전문용어", "definition": "쉬운 풀이"}}
  ]
}}"""

# 용어 풀이 상한. 프롬프트로도 최대 5개를 지시하지만 모델이 초과하는 경우가 있어
# (관측 최대 43개) 발행 단계에서 한 번 더 자른다.
MAX_GLOSSARY = 5

# "언제부터"에 숫자가 하나도 없으면 시점 정보가 아니라 서술이다
# (예: "관련 대책들은 곧 발표될 예정입니다"). 카드로 띄울 값이 아니므로 버린다.
_HAS_DIGIT = re.compile(r"\d")


def _parse_json(text: str) -> dict:
    """Gemini 응답에서 JSON을 견고하게 추출한다.

    모델이 ```json ... ``` 코드펜스로 감싸거나 앞뒤에 설명을 붙이는 경우가 있어
    그대로 json.loads하면 실패한다. 펜스를 벗기고, 실패 시 첫 번째 {...} 블록을 추출한다.
    """
    cleaned = text.strip()
    # ```json ... ``` 또는 ``` ... ``` 펜스 제거
    fence = re.match(r"^```(?:json)?\s*(.*?)\s*```$", cleaned, re.DOTALL)
    if fence:
        cleaned = fence.group(1).strip()
    try:
        return json.loads(cleaned)
    except json.JSONDecodeError:
        match = re.search(r"\{.*\}", cleaned, re.DOTALL)
        if match:
            return json.loads(match.group(0))
        raise


def summarize_policy(
    title: str,
    text: str,
    model=None,
    published_at: str = "",
) -> PolicySummary:
    if model is None:
        # 키에 줄바꿈/공백이 섞이면 gRPC 인증 헤더가 깨져("Illegal header value")
        # 클라이언트가 무한 재시도하므로 반드시 strip 한다.
        genai.configure(api_key=os.environ["GEMINI_API_KEY"].strip())
        model = genai.GenerativeModel("gemini-2.5-flash")

    # 첨부 원문은 붙임 표(지역 목록·일정 등)가 뒤에 붙어 8,000자로는 잘렸다.
    # gemini-2.5-flash 컨텍스트·비용 여유가 커서 20,000자까지 살린다.
    truncated = text[:20000]
    # 발행일을 넘기지 않으면 본문의 "7.16." 같은 축약 날짜에 모델이 연도를 임의로
    # 채워 넣는다(발행 2026년 문서에 "2020년"이 적히는 사례가 14.5%였다).
    prompt = _PROMPT_TEMPLATE.format(
        title=title,
        text=truncated,
        published_at=(published_at or "알 수 없음")[:10],
    )

    # 2.5-flash는 호출당 지연이 길 수 있어 타임아웃을 둔다. 초과/실패 건은
    # 호출부(main.py)에서 seen에 넣지 않고 건너뛰어 다음 실행에서 재시도한다.
    response = model.generate_content(prompt, request_options={"timeout": 60})
    raw = _parse_json(response.text)

    # 보강 필드는 모델이 누락하거나 형식이 어긋날 수 있어 견고하게 받는다(없으면 기본값).
    def _flatten_str(v):
        # 모델이 문자열 필드를 dict/list로 반환하는 사례가 있다(예: when_effective를
        # {"의무 공시 시작": "2028년", ...}로). 객체가 그대로 발행되면 웹 프리렌더와
        # Android Gson 파싱이 깨지므로 사람이 읽을 수 있는 한 줄 문자열로 평탄화한다.
        if isinstance(v, dict):
            return ", ".join(f"{k}: {_flatten_str(x)}" for k, x in v.items())
        if isinstance(v, list):
            return ", ".join(_flatten_str(x) for x in v)
        return str(v).strip() if v is not None else ""

    def _str_list(v):
        return [_flatten_str(x) for x in v if _flatten_str(x)] if isinstance(v, list) else []

    def _qa_list(v, keys):
        out = []
        if isinstance(v, list):
            for x in v:
                if isinstance(x, dict) and x.get(keys[0]) and x.get(keys[1]):
                    out.append({keys[0]: str(x[keys[0]]).strip(), keys[1]: str(x[keys[1]]).strip()})
        return out

    def _when_effective(v):
        """시점 값. 숫자가 없으면 실제 시점 정보가 아니므로 빈 문자열로 둔다."""
        t = _flatten_str(v)
        return t if _HAS_DIGIT.search(t) else ""

    how_to_apply = raw.get("how_to_apply")
    if isinstance(how_to_apply, str):
        how_to_apply = how_to_apply.strip() or None
    else:
        how_to_apply = None

    return PolicySummary(
        what_changed=_flatten_str(raw["what_changed"]),
        who_is_affected=_flatten_str(raw["who_is_affected"]),
        when_effective=_when_effective(raw.get("when_effective")),
        key_points=_str_list(raw["key_points"]),
        background=str(raw.get("background", "")).strip(),
        eligibility=_str_list(raw.get("eligibility")),
        how_to_apply=how_to_apply,
        faq=_qa_list(raw.get("faq"), ("question", "answer")),
        glossary=_qa_list(raw.get("glossary"), ("term", "definition"))[:MAX_GLOSSARY],
    )
