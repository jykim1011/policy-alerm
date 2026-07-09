from unittest.mock import MagicMock
from pipeline.summarizer import summarize_policy
from pipeline.models import PolicySummary

MOCK_RESPONSE_JSON = """{
  "what_changed": "청약 가점 우대 폭 확대",
  "who_is_affected": "무주택 기간 3년 이상 세대주",
  "when_effective": "2026년 7월 1일부터",
  "key_points": ["가점제 비율 상향", "특별공급 소득 기준 완화"]
}"""

def test_summarize_policy_returns_policy_summary():
    mock_model = MagicMock()
    mock_model.generate_content.return_value.text = MOCK_RESPONSE_JSON
    result = summarize_policy("청약 제도 개편", "긴 정책 내용...", model=mock_model)
    assert isinstance(result, PolicySummary)
    assert result.what_changed == "청약 가점 우대 폭 확대"
    assert len(result.key_points) == 2

def test_summarize_policy_handles_markdown_code_fence():
    """Gemini가 ```json 펜스로 감싸 응답해도 파싱되어야 한다."""
    mock_model = MagicMock()
    mock_model.generate_content.return_value.text = "```json\n" + MOCK_RESPONSE_JSON + "\n```"
    result = summarize_policy("청약 제도 개편", "내용", model=mock_model)
    assert result.what_changed == "청약 가점 우대 폭 확대"


def test_summarize_policy_handles_surrounding_text():
    """JSON 앞뒤에 설명이 붙어도 {...} 블록을 추출해야 한다."""
    mock_model = MagicMock()
    mock_model.generate_content.return_value.text = (
        "다음은 분석 결과입니다:\n" + MOCK_RESPONSE_JSON + "\n이상입니다."
    )
    result = summarize_policy("청약 제도 개편", "내용", model=mock_model)
    assert len(result.key_points) == 2


def test_summarize_policy_truncates_long_text():
    # 첨부 원문은 붙임 표까지 포함해 길다. 20,000자까지는 살려서
    # 뒷부분 붙임(지역 목록·일정표)이 잘리지 않게 한다.
    mock_model = MagicMock()
    mock_model.generate_content.return_value.text = MOCK_RESPONSE_JSON
    long_text = "가" * 40000
    summarize_policy("제목", long_text, model=mock_model)
    prompt = mock_model.generate_content.call_args.args[0]
    assert len(prompt) < 25000  # 상한 초과분은 잘림
    assert "가" * 20000 in prompt  # 20,000자까지는 보존
