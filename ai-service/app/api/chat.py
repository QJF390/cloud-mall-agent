import json
import logging

from fastapi import APIRouter, Header
from sse_starlette.sse import EventSourceResponse

from app.chains.rag_chain import astream_rag
from app.models.chat import ChatRequest
from app.services.session import get_session_manager

router = APIRouter(prefix="/ai", tags=["ai-chat"])

logger = logging.getLogger(__name__)

session_mgr = get_session_manager()

async def _stream_answer(session_id: str, user_msg: str, satoken: str):
    # ① 读历史（session 层已保证 Redis 挂了返回 []，不会把故障升级成 500）
    history = await session_mgr.get_history(session_id)

    # ② 领域事件 → SSE 帧
    async for event in astream_rag(
        question=user_msg,
        session_id=session_id,
        satoken=satoken,
        history=history,
    ):
        # 显式 json.dumps：dict 直接交给 sse_starlette 会被二次序列化，
        # 前端拿到的会是一个 JSON 字符串而不是对象。
        yield {
            "event": event.get("event") or "message",
            "data": json.dumps(event.get("data") or {}, ensure_ascii=False),
        }

@router.post("/chat")
async def chat(
    req: ChatRequest,
    satoken: str = Header(default="", alias="satoken"),
):
    logger.info(
        "[chat] session=%s len=%d auth=%s",
        req.session_id,
        len(req.message),
        "login" if satoken else "guest",
    )
    return EventSourceResponse(
        _stream_answer(req.session_id, req.message, satoken),
        media_type="text/event-stream",
    )
