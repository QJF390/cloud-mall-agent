import asyncio
import json
import os
from typing import AsyncIterator

import httpx

from app.core.config import get_settings

class LLMProvider:

    def __init__(
        self,
        model: str | None = None,
        temperature: float = 0.3,
        max_tokens: int | None = None,
        timeout: float | None = None,
    ):
        settings = get_settings()
        self.api_key = settings.effective_llm_api_key or os.getenv("LLM_API_KEY", "")
        self.base_url = settings.llm_base_url
        self.model = model or settings.llm_model
        self.temperature = temperature
        self.max_tokens = max_tokens
        self.timeout = timeout if timeout is not None else settings.llm_timeout_seconds
        self.client = httpx.AsyncClient(timeout=self.timeout)

    def _is_mock(self) -> bool:
        return not self.api_key or self.api_key == "your-api-key-here"

    def _headers(self) -> dict:
        return {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json",
        }

    def _build_body(
        self,
        messages: list[dict],
        stream: bool,
        response_format: dict | None = None,
    ) -> dict:
        body: dict = {
            "model": self.model,
            "messages": messages,
            "stream": stream,
            "temperature": self.temperature,
        }
        if self.max_tokens:
            body["max_tokens"] = self.max_tokens
        # response_format 仅非流式结构化输出时使用（OpenAI 兼容接口）
        if response_format and not stream:
            body["response_format"] = response_format
        return body

    async def stream_chat(self, messages: list[dict]) -> AsyncIterator[str]:
        """
        If no API key is configured, return a mock stream so the skeleton
        can be started and tested without real LLM cost.
        """
        if self._is_mock():
            mock_text = (
                "（这是骨架的 mock 回复，用于验证前后端流式链路）"
                "我收到了你的消息。当前还没有配置 LLM_API_KEY，"
                "在 ai-service/.env 里填入真实 key 并重启服务后，"
                "这里会换成真实模型的流式输出。"
            )
            # 逐字吐出并加一点延迟：模拟真实模型的流式节奏，方便前端验证打字机效果
            for char in mock_text:
                yield char
                await asyncio.sleep(0.02)
            return

        async with self.client.stream(
            "POST",
            f"{self.base_url}/chat/completions",
            headers=self._headers(),
            json=self._build_body(messages, stream=True),
        ) as response:
            response.raise_for_status()
            async for line in response.aiter_lines():
                line = line.strip()
                if not line or not line.startswith("data: "):
                    continue
                data = line[6:]
                if data == "[DONE]":
                    break
                try:
                    payload = json.loads(data)
                    delta = payload["choices"][0]["delta"].get("content", "")
                    if delta:
                        yield delta
                except Exception:
                    continue

    async def chat(
        self,
        messages: list[dict],
        response_format: dict | None = None,
    ) -> str:
        """
        一次性返回完整回答。

        什么时候用它而不是 stream_chat：
            · 意图路由（只要一个标签，没有流式必要）
            · 答后校验（判定任务）
            · 需要拿到完整 JSON 再解析的场景（流式 JSON 没法边收边解析）
        """
        if self._is_mock():
            return "（mock 回复：尚未配置 LLM_API_KEY）"

        response = await self.client.post(
            f"{self.base_url}/chat/completions",
            headers=self._headers(),
            json=self._build_body(messages, stream=False, response_format=response_format),
        )
        response.raise_for_status()
        payload = response.json()
        return payload["choices"][0]["message"]["content"] or ""

    async def close(self):
        await self.client.aclose()
