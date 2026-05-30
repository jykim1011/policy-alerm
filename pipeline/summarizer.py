import json
import os
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


def summarize_policy(
    title: str,
    text: str,
    model=None,
) -> PolicySummary:
    if model is None:
        genai.configure(api_key=os.environ["GEMINI_API_KEY"])
        model = genai.GenerativeModel("gemini-2.0-flash")

    truncated = text[:8000]
    prompt = _PROMPT_TEMPLATE.format(title=title, text=truncated)

    response = model.generate_content(prompt)
    raw = json.loads(response.text)
    return PolicySummary(
        what_changed=raw["what_changed"],
        who_is_affected=raw["who_is_affected"],
        when_effective=raw["when_effective"],
        key_points=raw["key_points"],
    )
