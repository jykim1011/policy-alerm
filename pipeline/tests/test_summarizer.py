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


# ── 발행일 기준 날짜 해석 / 용어 상한 / 빈 시점 처리 ──

def _model_returning(payload):
    from unittest.mock import MagicMock
    import json as _json
    m = MagicMock()
    base = {
        "what_changed": "바뀐 내용",
        "who_is_affected": "대상",
        "when_effective": "2026년 7월 1일부터",
        "key_points": ["포인트"],
    }
    base.update(payload)
    m.generate_content.return_value.text = _json.dumps(base, ensure_ascii=False)
    return m


def test_prompt_includes_published_at():
    """본문의 축약 날짜에 모델이 연도를 지어내지 않도록 발행일을 프롬프트에 넣는다."""
    m = _model_returning({})
    summarize_policy("제목", "본문", model=m, published_at="2026-07-16T18:51:12+09:00")
    prompt = m.generate_content.call_args[0][0]
    assert "2026-07-16" in prompt
    assert "발행일" in prompt


def test_prompt_published_at_defaults_when_missing():
    m = _model_returning({})
    summarize_policy("제목", "본문", model=m)
    prompt = m.generate_content.call_args[0][0]
    assert "알 수 없음" in prompt


def test_glossary_is_capped():
    """모델이 상한을 어겨도(관측 최대 43개) 발행 단계에서 자른다."""
    many = [{"term": f"용어{i}", "definition": f"풀이{i}"} for i in range(20)]
    result = summarize_policy("제목", "본문", model=_model_returning({"glossary": many}))
    assert len(result.glossary) == 5


def test_when_effective_dropped_when_no_date():
    """'곧 발표될 예정' 처럼 시점이 없는 서술은 카드로 띄울 값이 아니다."""
    m = _model_returning({"when_effective": "관련 대책들은 곧 발표될 예정입니다."})
    assert summarize_policy("제목", "본문", model=m).when_effective == ""


def test_when_effective_kept_when_it_has_a_date():
    m = _model_returning({"when_effective": "2026년 9월 1일부터"})
    assert summarize_policy("제목", "본문", model=m).when_effective == "2026년 9월 1일부터"
