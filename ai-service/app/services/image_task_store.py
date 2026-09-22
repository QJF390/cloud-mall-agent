
import json
import logging
import time
from typing import Any

import redis.asyncio as redis

from app.core.config import get_settings

settings = get_settings()
logger = logging.getLogger(__name__)

_KEY_PREFIX = "ai:image:task"

class ImageTaskStore:
    def __init__(self):
        self._redis = redis.Redis(
            host=settings.redis_host,
            port=settings.redis_port,
            db=settings.redis_db,
            decode_responses=True,
        )
        # 兜底存储：仅在本进程内有效（Redis 不可用时保证功能不断）
        self._fallback: dict[str, dict[str, Any]] = {}

    def _key(self, task_id: str) -> str:
        return f"{_KEY_PREFIX}:{task_id}"

    async def create(
        self,
        task_id: str,
        description: str,
        usage_type: str,
    ) -> None:
        await self._put(
            task_id,
            {
                "task_id": task_id,
                "status": "pending",
                "message": "任务已受理",
                "description": description,
                "usage_type": usage_type,
                "image_url": None,
                "positive_prompt": None,
                "error": None,
                "created_at": time.time(),
            },
        )

    async def update(self, task_id: str, **fields: Any) -> None:
        current = await self.get(task_id)
        if current is None:
            logger.warning("[image-task] 更新不存在的任务：%s", task_id)
            return
        current.update(fields)
        current["updated_at"] = time.time()
        await self._put(task_id, current)

    async def get(self, task_id: str) -> dict[str, Any] | None:
        try:
            raw = await self._redis.get(self._key(task_id))
            if raw:
                return json.loads(raw)
        except Exception:  # noqa: BLE001
            pass
        return self._fallback.get(task_id)

    async def _put(self, task_id: str, data: dict[str, Any]) -> None:
        payload = json.dumps(data, ensure_ascii=False)
        self._fallback[task_id] = data  # 先写兜底，再尽力写 Redis
        try:
            await self._redis.set(
                self._key(task_id), payload, ex=settings.image_task_ttl_seconds
            )
        except Exception:  # noqa: BLE001
            logger.warning("[image-task] Redis 写入失败，退化为进程内存存储")

_store: "ImageTaskStore | None" = None

def get_image_task_store() -> ImageTaskStore:
    global _store
    if _store is None:
        _store = ImageTaskStore()
    return _store
