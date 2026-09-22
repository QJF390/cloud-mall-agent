import json
import logging

import redis.asyncio as redis

from app.core.config import get_settings

settings = get_settings()
logger = logging.getLogger(__name__)

class SessionManager:
    """
    Conversation history stored in Redis.
    Reuses the same Redis instance already running for Sa-Token sessions.
    """

    KEY_PREFIX = "ai:session"

    def __init__(self):
        self._redis = redis.Redis(
            host=settings.redis_host,
            port=settings.redis_port,
            db=settings.redis_db,
            decode_responses=True,
        )

    def _key(self, session_id: str) -> str:
        return f"{self.KEY_PREFIX}:{session_id}"

    async def get_history(self, session_id: str) -> list[dict]:
        try:
            raw = await self._redis.lrange(
                self._key(session_id), -settings.max_history_per_session, -1
            )
        except Exception:  # noqa: BLE001
            logger.warning("[session] 读取会话历史失败，按空历史继续")
            return []
        history: list[dict] = []
        for item in raw:
            try:
                msg = json.loads(item)
            except ValueError:
                continue
            if isinstance(msg, dict):
                history.append(msg)
        return history

    async def append_messages(
        self, session_id: str, messages: list[dict]
    ) -> bool:
        try:
            pipe = self._redis.pipeline()
            for msg in messages:
                pipe.rpush(
                    self._key(session_id), json.dumps(msg, ensure_ascii=False)
                )
            pipe.expire(self._key(session_id), 86400)  # 24h TTL
            await pipe.execute()
        except Exception:  # noqa: BLE001
            logger.warning("[session] 写入会话历史失败（不影响本次回答）")
            return False
        return True

_session_manager: "SessionManager | None" = None

def get_session_manager() -> SessionManager:
    global _session_manager
    if _session_manager is None:
        _session_manager = SessionManager()
    return _session_manager
