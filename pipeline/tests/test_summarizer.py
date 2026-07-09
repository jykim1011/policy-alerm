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


def test_summarize_policy_flattens_object_when_effective():
    """모델이 when_effective를 문자열 대신 중첩 객체로 반환해도 문자열로 평탄화한다.

    실제 사례: 기후에너지환경부-2026-07-08-b8eabfa6 — 객체가 그대로 발행되어
    웹 프리렌더("Objects are not valid as a React child")와 Android Gson 파싱이 깨졌다.
    """
    mock_model = MagicMock()
    mock_model.generate_content.return_value.text = """{
      "what_changed": "지속가능성 공시 의무화",
      "who_is_affected": "코스피 상장사",
      "when_effective": {
        "의무 공시 시작": "2028년",
        "스코프3 공시 유예": {"10조원 이상": "2031년", "5조원 이상": "2032년"}
      },
      "key_points": ["포인트1", {"잘못된": "항목"}]
    }"""
    result = summarize_policy("공시 제도", "내용", model=mock_model)
    assert isinstance(result.when_effective, str)
    assert "의무 공시 시작: 2028년" in result.when_effective
    assert "10조원 이상: 2031년" in result.when_effective
    assert all(isinstance(p, str) for p in result.key_points)
    assert result.key_points[0] == "포인트1"


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
