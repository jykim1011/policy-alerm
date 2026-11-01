"""Claude 호출 공통 래퍼.

summarizer·backfill 스크립트가 같은 방식으로 모델을 부르도록 한곳에 모은다.
호출부는 `complete(prompt, schema=..., timeout=...)`만 알면 되고, 테스트는 이 메서드를
MagicMock으로 대체한다.
"""
import os
from typing import Optional

import anthropic

MODEL = "claude-haiku-5-5"

# 생각 토큰도 max_tokens에 포함된다. 요약 JSON은 2천 토큰 안팎이라 여유 있게 잡는다.
MAX_TOKENS = 8000


class LLMError(RuntimeError):
    """응답을 쓸 수 없는 경우(거절, 길이 초과). 호출부는 건너뛰고 다음 실행에서 재시도한다."""


class ClaudeModel:
    def __init__(self, client: Optional[anthropic.Anthropic] = None, model: str = MODEL):
        # 키에 줄바꿈/공백이 섞이면 인증 헤더가 깨지므로 반드시 strip 한다.
        self._client = client or anthropic.Anthropic(
            api_key=os.environ["ANTHROPIC_API_KEY"].strip()
        )
        self._model = model

    def complete(self, prompt: str, schema: Optional[dict] = None, timeout: float = 60) -> str:
        # 요약·제목 생성은 깊은 추론이 필요 없어 effort를 낮춰 비용과 지연을 줄인다.
        output_config: dict = {"effort": "low"}
        if schema is not None:
            # 구조화 출력: 응답이 스키마에 맞는 JSON 텍스트로 보장된다(코드펜스·설명문 없음).
            output_config["format"] = {"type": "json_schema", "schema": schema}

        response = self._client.with_options(timeout=timeout).messages.create(
            model=self._model,
            max_tokens=MAX_TOKENS,
            messages=[{"role": "user", "content": prompt}],
            output_config=output_config,
        )
        if response.stop_reason == "refusal":
            raise LLMError("모델이 응답을 거절했다")
        if response.stop_reason == "max_tokens":
            raise LLMError("응답이 max_tokens에서 잘렸다")
        return "".join(b.text for b in response.content if b.type == "text")
