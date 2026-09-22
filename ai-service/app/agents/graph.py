
import asyncio
import json
import logging
from functools import lru_cache
from typing import Any

from langgraph.graph import END, START, StateGraph

from app.agents.nodes import (
    NODE_GENERATE,
    NODE_RETRIEVE,
    NODE_ROUTER,
    NODE_TOOL_CALL,
    NODE_VERIFY,
    generate_node,
    retrieve_node,
    router_node,
    route_after_retrieve,
    route_after_router,
)
from app.agents.state import AgentState, new_state, state_to_trace
from app.chains.prompts import FALLBACK_TOOL_ERROR

logger = logging.getLogger(__name__)

NODE_HUMAN_REVIEW = "human_review"  # 预留：第 6 步做 interrupt 时启用

DEFAULT_RECURSION_LIMIT = 10

def build_graph(
    enable_tools: bool = False,
    checkpointer: Any = None,
) -> Any:
    builder = StateGraph(AgentState)

    # ① 注册节点（增量构建：当前只注册最小图的 3 个）
    builder.add_node(NODE_ROUTER, router_node)
    builder.add_node(NODE_RETRIEVE, retrieve_node)
    builder.add_node(NODE_GENERATE, generate_node)

    # ② 入口：START → router
    #    （0.2.x 里 set_entry_point 与 add_edge(START, ...) 等价，用后者更直观）
    builder.add_edge(START, NODE_ROUTER)

    builder.add_conditional_edges(
        NODE_ROUTER,
        route_after_router,
        {
            NODE_RETRIEVE: NODE_RETRIEVE,
            # 第 4 步之前 tool_call 节点尚未注册 → **降级走检索**而不是报错：
            # 订单类问题当前最多"答得不理想"，而报错是"整个功能不可用"。
            NODE_TOOL_CALL: NODE_TOOL_CALL if enable_tools else NODE_RETRIEVE,
            END: END,
        },
    )

    # ④ retrieve 之后：空结果 / 拒答 → END，否则 → generate
    builder.add_conditional_edges(
        NODE_RETRIEVE,
        route_after_retrieve,
        {NODE_GENERATE: NODE_GENERATE, END: END},
    )

    # ⑤ generate → END（第 3 步会在这里插入 verify 节点）
    builder.add_edge(NODE_GENERATE, END)

    return builder.compile(checkpointer=checkpointer)

def get_agent(**deps: Any) -> Any:
    return _compiled(
        bool(deps.get("enable_tools", False)),
        bool(deps.get("enable_verify", False)),
    )

@lru_cache(maxsize=None)
def _compiled(enable_tools: bool, enable_verify: bool) -> Any:
    if enable_verify:
        logger.warning("[agent] verify 节点尚未接入（第 3 步），本次按最小图编译")
    return build_graph(enable_tools=enable_tools)

async def _prepare(
    session_id: str,
    user_message: str,
    satoken: str = "",
    request_id: str = "",
) -> "tuple[AgentState, dict]":
    user_id = await _resolve_user_id(satoken)
    history = await _load_history(session_id)
    state = new_state(
        session_id,
        user_message,
        history=history,
        request_id=request_id,
        user_id=user_id,
    )
    # thread_id = session_id → 接上 checkpointer 后自动多轮（第 5 步）
    config = {
        "configurable": {"thread_id": session_id},
        "recursion_limit": DEFAULT_RECURSION_LIMIT,
    }
    return state, config

async def _resolve_user_id(satoken: str) -> "int | None":
    if not satoken:
        return None
    try:
        from app.tools.base import resolve_user_id_from_token

        return await resolve_user_id_from_token(satoken)
    except Exception:  # noqa: BLE001
        logger.warning("[agent] 身份解析失败，按游客处理")
        return None

async def _load_history(session_id: str) -> list[dict]:
    if not session_id:
        return []
    try:
        from app.services.session import get_session_manager

        history = await get_session_manager().get_history(session_id)
    except Exception:  # noqa: BLE001
        logger.warning("[agent] 读取会话历史失败，按空历史继续")
        return []
    return history if isinstance(history, list) else []

async def _append_history(session_id: str, user_message: str, answer: str) -> None:
    """写历史。失败只记日志 —— 写不进去不影响本次已经生成的回答。"""
    if not session_id or not user_message or not answer:
        return
    try:
        from app.services.session import get_session_manager

        await get_session_manager().append_messages(
            session_id,
            [
                {"role": "user", "content": user_message},
                {"role": "assistant", "content": answer},
            ],
        )
    except Exception:  # noqa: BLE001
        logger.warning("[agent] 写入会话历史失败（不影响本次回答）")

def _error_result(state: AgentState) -> dict:
    return {
        "answer": FALLBACK_TOOL_ERROR,
        "cited": [],
        "confidence": 0.0,
        "need_human": False,
        "error": "agent_error",
        "intent": "",
        "request_id": str(state.get("request_id") or ""),
    }

async def run_agent(
    session_id: str,
    user_message: str,
    satoken: str = "",
    request_id: str = "",
) -> dict:
    state, config = await _prepare(session_id, user_message, satoken, request_id)
    graph = get_agent()

    try:
        result = await graph.ainvoke(state, config)
    except asyncio.CancelledError:
        raise
    except Exception:  # noqa: BLE001
        logger.exception(
            "[agent] 图执行失败 request_id=%s", state.get("request_id") or ""
        )
        return _error_result(state)

    logger.info(
        "[agent] trace %s",
        json.dumps(state_to_trace(result), ensure_ascii=False),
    )
    answer = str(result.get("answer") or "")
    await _append_history(session_id, user_message, answer)

    return {
        "answer": answer,
        "cited": list(result.get("cited") or []),
        "confidence": float(result.get("confidence") or 0.0),
        "need_human": bool(result.get("need_human")),
        "error": result.get("error"),
        "intent": str(result.get("intent") or ""),
        "verified": bool(result.get("verified")),
        "request_id": str(result.get("request_id") or ""),
    }

async def stream_agent(
    session_id: str,
    user_message: str,
    satoken: str = "",
    request_id: str = "",
):
    state, config = await _prepare(session_id, user_message, satoken, request_id)
    graph = get_agent()

    answer = ""
    cited: list[str] = []
    confidence = 0.0
    intent = ""

    try:
        async for event in graph.astream(state, config, stream_mode="updates"):
            if not isinstance(event, dict):
                continue
            for node_name, delta in event.items():
                if not isinstance(delta, dict):
                    continue
                yield {
                    "event": "step",
                    "data": {
                        "node": str(node_name),
                        "intent": str(delta.get("intent") or ""),
                        "request_id": str(state.get("request_id") or ""),
                    },
                }
                # 无论哪个节点写定了 answer，都以最后一次为准
                # （超范围在 router 写、拒答在 retrieve 写、正常在 generate 写）
                if delta.get("answer"):
                    answer = str(delta["answer"])
                if delta.get("cited"):
                    cited = [str(c) for c in delta["cited"]]
                if delta.get("confidence") is not None:
                    confidence = float(delta["confidence"] or 0.0)
                if delta.get("intent"):
                    intent = str(delta["intent"])
    except asyncio.CancelledError:
        # 前端断开 → 原样抛出，让框架取消任务，不留僵尸生成
        raise
    except Exception:  # noqa: BLE001
        logger.exception(
            "[agent] 流式执行失败 request_id=%s", state.get("request_id") or ""
        )
        answer = FALLBACK_TOOL_ERROR

    yield {"event": "message", "data": {"chunk": answer or FALLBACK_TOOL_ERROR}}
    yield {"event": "citations", "data": {"cited": cited}}
    yield {
        "event": "done",
        "data": {
            "status": "ok",
            "confidence": confidence,
            "intent": intent,
            "verified": False,
            "request_id": str(state.get("request_id") or ""),
        },
    }

    await _append_history(session_id, user_message, answer)
