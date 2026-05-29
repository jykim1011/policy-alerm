from unittest.mock import MagicMock, patch
from pipeline.summarizer import summarize_policy
from pipeline.models import PolicySummary

MOCK_RESPONSE_JSON = """{
  "what_changed": "청약 가점 우대 폭 확대",
  "who_is_affected": "무주택 기간 3년 이상 세대주",
  "when_effective": "2026년 7월 1일부터",
  "key_points": ["가점제 비율 상향", "특별공급 소득 기준 완화"]
}"""

def test_summarize_policy_returns_policy_summary():
    mock_client = MagicMock()
    mock_client.messages.create.return_value.content = [
        MagicMock(text=MOCK_RESPONSE_JSON)
    ]
    result = summarize_policy("청약 제도 개편", "긴 정책 내용...", client=mock_client)
    assert isinstance(result, PolicySummary)
    assert result.what_changed == "청약 가점 우대 폭 확대"
    assert len(result.key_points) == 2

def test_summarize_policy_truncates_long_text():
    mock_client = MagicMock()
    mock_client.messages.create.return_value.content = [
        MagicMock(text=MOCK_RESPONSE_JSON)
    ]
    long_text = "가" * 20000
    summarize_policy("제목", long_text, client=mock_client)
    call_args = mock_client.messages.create.call_args
    prompt = call_args.kwargs["messages"][0]["content"]
    assert len(prompt) < 12000  # 텍스트가 잘렸는지 확인
