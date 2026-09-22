
import logging
from typing import Any

import httpx

from app.core.config import get_settings

logger = logging.getLogger(__name__)

class ElasticsearchClient:
    """ES 客户端（httpx 版）。对外只暴露 search / count / close。"""

    def __init__(
        self,
        base_url: str | None = None,
        timeout: float | None = None,
        username: str | None = None,
        password: str | None = None,
    ) -> None:
        settings = get_settings()
        self.base_url = (base_url or settings.es_base_url).rstrip("/")
        self.timeout = timeout if timeout is not None else settings.es_timeout_seconds
        user = username or settings.es_username
        pwd = password or settings.es_password
        # auth=None 时不带 Authorization 头（本地裸 ES 常见）
        auth = (user, pwd) if user and pwd else None
        self._client = httpx.AsyncClient(timeout=self.timeout, auth=auth)

    async def search(self, index: str, body: dict) -> dict:
        """
        执行 _search，原样返回 ES 的响应 dict。

        参数说明（都是 ES 查询体里的，放这里注释便于对照）：
            index : 索引名（本项目用 rag_chunks）
            body  : 完整查询 DSL。由 retriever 构造，本方法不关心语义
        """
        response = await self._client.post(
            f"{self.base_url}/{index}/_search",
            json=body,
            headers={"Accept": "application/json"},
        )
        response.raise_for_status()
        return response.json()

    async def count(self, index: str) -> int:
        """索引文档数，用于 ingest 的幂等自检（跑两次 count 应该不变）。"""
        response = await self._client.post(f"{self.base_url}/{index}/_count")
        response.raise_for_status()
        return int(response.json().get("count", 0))

    async def index_exists(self, index: str) -> bool:
        response = await self._client.head(f"{self.base_url}/{index}")
        return response.status_code == 200

    async def create_index(self, index: str, body: dict) -> None:
        response = await self._client.put(f"{self.base_url}/{index}", json=body)
        response.raise_for_status()

    async def delete_index(self, index: str) -> None:
        response = await self._client.delete(f"{self.base_url}/{index}")
        response.raise_for_status()

    async def get_mapping(self, index: str) -> dict:
        response = await self._client.get(f"{self.base_url}/{index}/_mapping")
        response.raise_for_status()
        return response.json()

    async def bulk(self, index: str, ndjson: str, refresh: str = "wait_for") -> dict:
        response = await self._client.post(
            f"{self.base_url}/{index}/_bulk?refresh={refresh}",
            content=ndjson.encode("utf-8"),
            headers={"Content-Type": "application/x-ndjson"},
        )
        response.raise_for_status()
        return response.json()

    async def ping(self) -> bool:
        """健康检查：给 /_cluster/health 用，别在 __init__ 里调（会阻塞启动）。"""
        try:
            response = await self._client.get(f"{self.base_url}/_cluster/health")
            return response.status_code == 200
        except Exception as exc:  # noqa: BLE001
            logger.warning("[ES] 健康检查失败: %s", exc)
            return False

    async def close(self) -> None:
        await self._client.aclose()

def extract_hits(payload: dict) -> list[dict]:
    hits: Any = payload.get("hits", {}) or {}
    return list(hits.get("hits", []) or [])
