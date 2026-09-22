
import asyncio

import httpx

from app.core.config import get_settings

class EmbeddingsProvider:
    """批量/单条文本向量化。内部委托 OpenAI 兼容的 /embeddings 接口。"""

    # 单次请求最多带多少条文本。
    # 百炼 text-embedding-v3 单次上限较小，取 10 是保守值；
    # 换成别的服务商时按对方文档调整，但**不要**改成一（等于放弃批量）。
    MAX_BATCH = 10

    def __init__(
        self,
        model: str | None = None,
        dimensions: int | None = None,
        timeout: float | None = None,
        batch_size: int | None = None,
    ):
        settings = get_settings()
        self.api_key = settings.effective_llm_api_key
        self.base_url = settings.llm_base_url
        self.model = model or settings.embedding_model
        self.dimensions = dimensions or settings.embedding_dim
        self.timeout = timeout if timeout is not None else settings.llm_timeout_seconds
        self.batch_size = min(batch_size or self.MAX_BATCH, self.MAX_BATCH)
        self.client = httpx.AsyncClient(timeout=self.timeout)

    def _headers(self) -> dict:
        return {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json",
        }

    async def _embed_batch(self, texts: list[str]) -> list[list[float]]:
        body = {
            "model": self.model,
            "input": texts,
            "dimensions": self.dimensions,
            "encoding_format": "float",
        }
        # 失败重试（指数退避）——入库是批量任务，偶发 429/超时很常见，
        # 但必须设上限，否则一次网络抖动会变成无限重试烧钱。
        last_error: Exception | None = None
        for attempt in range(3):
            try:
                response = await self.client.post(
                    f"{self.base_url}/embeddings",
                    headers=self._headers(),
                    json=body,
                )
                response.raise_for_status()
                payload = response.json()
                # 按 index 排序，防止服务端乱序返回导致向量和文本错位
                items = sorted(payload["data"], key=lambda d: d["index"])
                return [item["embedding"] for item in items]
            except Exception as exc:  # noqa: BLE001
                last_error = exc
                if attempt < 2:
                    await asyncio.sleep(2**attempt)
        raise RuntimeError(f"embedding 调用失败（已重试 3 次）: {last_error}")

    async def embed_documents(self, texts: list[str]) -> list[list[float]]:
        if not texts:
            return []
        vectors: list[list[float]] = []
        for i in range(0, len(texts), self.batch_size):
            chunk = texts[i : i + self.batch_size]
            vectors.extend(await self._embed_batch(chunk))
        return vectors

    async def embed_query(self, text: str) -> list[float]:
        vectors = await self._embed_batch([text])
        return vectors[0]

    async def close(self) -> None:
        await self.client.aclose()
