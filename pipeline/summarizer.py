import json
import os
import re
from typing import Optional
import google.generativeai as genai
from pipeline.models import PolicySummary

_PROMPT_TEMPLATE = """다음 정책 문서를 분석하여 JSON 형식으로만 응답하세요. 설명 없이 JSON만 출력하세요.

제목: {title}

내용:
{text}

응답 형식:
{{
  "what_changed": "무엇이 바뀌었는지 1-2문장 (구체적 수치 포함)",
  "who_is_affected": "누가 대상인지 1-2문장",
  "when_effective": "언제부터 적용되는지",
  "key_points": ["핵심 포인트 1", "핵심 포인트 2", "핵심 포인트 3"]
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
        genai.configure(api_key=os.environ["GEMINI_API_KEY"])
        model = genai.GenerativeModel("gemini-2.5-flash")

    truncated = text[:8000]
    prompt = _PROMPT_TEMPLATE.format(title=title, text=truncated)

    # 2.5-flash는 호출당 지연이 길 수 있어 타임아웃을 둔다. 초과/실패 건은
    # 호출부(main.py)에서 seen에 넣지 않고 건너뛰어 다음 실행에서 재시도한다.
    response = model.generate_content(prompt, request_options={"timeout": 40})
    raw = _parse_json(response.text)
    return PolicySummary(
        what_changed=raw["what_changed"],
        who_is_affected=raw["who_is_affected"],
        when_effective=raw["when_effective"],
        key_points=raw["key_points"],
    )
