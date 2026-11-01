import pytest


@pytest.fixture(autouse=True)
def _no_real_llm_calls(monkeypatch):
    # 테스트에서 실제 Claude API를 부르면 선불 크레딧이 빠진다. 키를 지워 두면
    # 목(mock)을 빠뜨린 테스트가 과금 대신 KeyError로 바로 실패한다.
    monkeypatch.delenv("ANTHROPIC_API_KEY", raising=False)
