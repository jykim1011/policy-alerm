from types import SimpleNamespace
from unittest.mock import MagicMock

import pytest

from pipeline.llm import ClaudeModel, LLMError


def _client_returning(stop_reason, text='{"a": 1}'):
    client = MagicMock()
    client.with_options.return_value.messages.create.return_value = SimpleNamespace(
        stop_reason=stop_reason,
        content=[SimpleNamespace(type="thinking"), SimpleNamespace(type="text", text=text)],
    )
    return client


def test_complete_returns_text_and_sends_schema():
    client = _client_returning("end_turn")
    schema = {"type": "object"}
    assert ClaudeModel(client=client).complete("p", schema=schema, timeout=30) == '{"a": 1}'
    client.with_options.assert_called_once_with(timeout=30)
    kwargs = client.with_options.return_value.messages.create.call_args.kwargs
    assert kwargs["output_config"] == {
        "effort": "low",
        "format": {"type": "json_schema", "schema": schema},
    }


@pytest.mark.parametrize("stop_reason", ["refusal", "max_tokens"])
def test_complete_raises_on_unusable_response(stop_reason):
    # 거절·잘림은 예외로 올려 호출부가 건너뛰고 다음 실행에서 재시도하게 한다.
    with pytest.raises(LLMError):
        ClaudeModel(client=_client_returning(stop_reason)).complete("p")
