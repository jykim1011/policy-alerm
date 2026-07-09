import json
import os
import re
from typing import Optional
import google.generativeai as genai
from pipeline.models import PolicySummary

_PROMPT_TEMPLATE = """당신은 정부 보도자료를 일반 시민이 이해하기 쉽게 풀어주는 정책 해설가입니다.
다음 정책 문서를 분석하여 JSON 형식으로만 응답하세요. 설명 없이 JSON만 출력하세요.

제목: {title}

내용:
{text}

작성 지침:
- 쉬운 우리말로, 공무원 용어를 풀어서 설명하세요.
- **문서 본문에 근거가 없는 내용은 절대 지어내지 마세요.** 근거가 없으면 빈 배열([]) 또는 null로 두세요.
- faq는 시민이 실제로 궁금해할 질문과 본문 근거에 기반한 답으로 작성하세요.
- glossary는 본문에 등장한 어려운 행정·전문 용어만 쉽게 풀이하세요.

응답 형식:
{{
  "what_changed": "무엇이 바뀌었는지 1-2문장 (구체적 수치 포함)",
  "who_is_affected": "누가 대상인지 1-2문장",
  "when_effective": "언제부터 적용되는지",
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
) -> PolicySummary:
    if model is None:
        # 키에 줄바꿈/공백이 섞이면 gRPC 인증 헤더가 깨져("Illegal header value")
        # 클라이언트가 무한 재시도하므로 반드시 strip 한다.
        genai.configure(api_key=os.environ["GEMINI_API_KEY"].strip())
        model = genai.GenerativeModel("gemini-2.5-flash")

    # 첨부 원문은 붙임 표(지역 목록·일정 등)가 뒤에 붙어 8,000자로는 잘렸다.
    # gemini-2.5-flash 컨텍스트·비용 여유가 커서 20,000자까지 살린다.
    truncated = text[:20000]
    prompt = _PROMPT_TEMPLATE.format(title=title, text=truncated)

    # 2.5-flash는 호출당 지연이 길 수 있어 타임아웃을 둔다. 초과/실패 건은
    # 호출부(main.py)에서 seen에 넣지 않고 건너뛰어 다음 실행에서 재시도한다.
    response = model.generate_content(prompt, request_options={"timeout": 60})
    raw = _parse_json(response.text)

    # 보강 필드는 모델이 누락하거나 형식이 어긋날 수 있어 견고하게 받는다(없으면 기본값).
    def _str_list(v):
        return [str(x).strip() for x in v if str(x).strip()] if isinstance(v, list) else []

    def _qa_list(v, keys):
        out = []
        if isinstance(v, list):
            for x in v:
                if isinstance(x, dict) and x.get(keys[0]) and x.get(keys[1]):
                    out.append({keys[0]: str(x[keys[0]]).strip(), keys[1]: str(x[keys[1]]).strip()})
        return out

    how_to_apply = raw.get("how_to_apply")
    if isinstance(how_to_apply, str):
        how_to_apply = how_to_apply.strip() or None
    else:
        how_to_apply = None

    return PolicySummary(
        what_changed=raw["what_changed"],
        who_is_affected=raw["who_is_affected"],
        when_effective=raw["when_effective"],
        key_points=raw["key_points"],
        background=str(raw.get("background", "")).strip(),
        eligibility=_str_list(raw.get("eligibility")),
        how_to_apply=how_to_apply,
        faq=_qa_list(raw.get("faq"), ("question", "answer")),
        glossary=_qa_list(raw.get("glossary"), ("term", "definition")),
    )
