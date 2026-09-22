
import logging
from typing import Any

import httpx

from app.core.config import get_settings

logger = logging.getLogger(__name__)

class HttpReranker:

    def __init__(
        self,
        base_url: str | None = None,
        model: str | None = None,
        timeout: float | None = None,
    ) -> None:
        settings = get_settings()
        self.base_url = (base_url or settings.rerank_base_url).rstrip("/")
        self.model = model or settings.rerank_model
        self.timeout = timeout if timeout is not None else settings.rerank_timeout_seconds
        self._client = httpx.AsyncClient(timeout=self.timeout)

    def is_enabled(self) -> bool:
        return bool(self.base_url)

    async def rerank(self, query: str, docs: list[str]) -> list[float]:
        if not self.is_enabled() or not docs:
            return []

        payload = {
            "model": self.model,
            "query": query,
            "documents": docs,
            "top_n": len(docs),
        }
        response = await self._client.post(
            f"{self.base_url}/rerank",
            json=payload,
            headers={"Authorization": f"Bearer {get_settings().effective_llm_api_key}"},
        )
        response.raise_for_status()
        results: Any = response.json().get("results", []) or []
        results = sorted(results, key=lambda r: r.get("index", 0))
        return [float(r.get("relevance_score", 0.0)) for r in results]

    async def close(self) -> None:
        await self._client.aclose()
