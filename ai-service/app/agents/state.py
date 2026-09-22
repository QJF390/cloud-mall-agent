
import logging
import time
from enum import Enum
from typing import Annotated, Any, TypedDict
from uuid import uuid4

from app.core.config import get_settings

logger = logging.getLogger(__name__)

class Intent(str, Enum):

    # 找作品/导购：逛市集、挑商品、比价、问详情。
    # 边界：不含"已下单之后"的问题（那是 ORDER_QUERY），不含"我要卖东西"（CREATOR_ASSIST）
    PRODUCT_GUIDE = "product_guide"

    # 平台规则问答：退货/退款、发货时效、处罚申诉、创作者规范。
    # 边界：规则类答案必须有引用编号；含具体数值（天/比例）时优先由 tool 给出
    POLICY_QA = "policy_qa"

    ORDER_QUERY = "order_query"

    # 创作者辅助：上架草稿、标题/描述润色、定价建议。
    # 边界：只产出草稿与建议，不代为执行写操作（上架/改价/删除）
    CREATOR_ASSIST = "creator_assist"

    OUT_OF_SCOPE = "out_of_scope"

    @classmethod
    def parse(cls, value: Any, default: "Intent | None" = None) -> "Intent | None":
        if isinstance(value, cls):
            return value
        raw = str(value or "").strip().lower()
        if not raw:
            return default
        # 容忍 "Intent.PRODUCT_GUIDE" 这种带前缀的输出
        raw = raw.split(".")[-1] if raw.startswith("intent.") else raw
        for member in cls:
            if member.value == raw or member.name.lower() == raw:
                return member
        logger.warning("[Intent] 无法识别的路由结果，降级：%r", value)
        return default

def _merge_messages(left: Any, right: Any) -> list[dict]:
    merged: list[dict] = list(left) if isinstance(left, list) else []
    if not right:
        return merged

    incoming = right if isinstance(right, list) else [right]
    index_by_id = {
        msg["id"]: i
        for i, msg in enumerate(merged)
        if isinstance(msg, dict) and msg.get("id") is not None
    }
    for msg in incoming:
        if not isinstance(msg, dict):
            merged.append(msg)
            continue
        msg_id = msg.get("id")
        if msg_id is not None and msg_id in index_by_id:
            merged[index_by_id[msg_id]] = msg  # 同 id → 覆盖
        else:
            merged.append(msg)
            if msg_id is not None:
                index_by_id[msg_id] = len(merged) - 1
    return merged

def _append_list(left: Any, right: Any) -> list:
    base = list(left) if isinstance(left, list) else []
    if not right:
        return base
    return [*base, *(right if isinstance(right, list) else [right])]

def _merge_dict(left: Any, right: Any) -> dict:
    merged = dict(left) if isinstance(left, dict) else {}
    if not isinstance(right, dict):
        return merged
    for key, value in right.items():
        if value is None:
            continue
        merged[key] = value
    return merged

class AgentState(TypedDict, total=False):

    # ---- 输入侧（由入口填入，图内尽量只读）----
    messages: Annotated[list[dict], _merge_messages]   # 追加（含同 id 覆盖）
    user_id: "int | None"
    session_id: str                                    # checkpointer 用的会话标识
    request_id: str                                    # 链路追踪 ID
    started_at: float                                  # 进图时刻，算 latency 用

    # ---- 路由与检索 ----
    intent: str                                        # 存 Intent 的 **value**（可序列化）
    intent_confidence: float                           # 路由置信度，低置信 → 走保守链路
    filters: Annotated[dict, _merge_dict]              # 结构化查询条件（跨轮累积）
    retrieved: list[dict]                              # 每轮检索结果，**覆盖**（新一轮替换旧的）
    retrieval_score: float                             # 最高分，门控判断用

    # ---- 工具与循环 ----
    tool_calls: Annotated[list[dict], _append_list]    # 追加：审计流水 + 防重复调用
    tool_results: Annotated[list[dict], _append_list]  # 追加：已裁剪的工具结果
    step_count: int                                    # 循环计数（配合 recursion_limit）

    # ---- 输出与校验 ----
    answer: str
    cited: list[str]  # **覆盖**：引用编号只对应本轮 retrieved，追加会串号
    confidence: float
    verified: bool
    verify_notes: Annotated[list[str], _append_list]   # 追加：多次校验的问题要累积
    need_human: bool
    error: "str | None"

def new_state(
    session_id: str,
    user_message: str,
    history: list[dict] | None = None,
    request_id: str = "",
    user_id: int | None = None,
) -> AgentState:
    limit = max(1, int(get_settings().max_history_per_session))
    messages = _clean_history(history, limit)
    messages.append({"role": "user", "content": user_message or ""})

    return AgentState(
        session_id=session_id,
        request_id=request_id or uuid4().hex[:12],
        started_at=time.perf_counter(),
        user_id=user_id,
        messages=messages,
        # ---- 路由与检索 ----
        intent="",
        intent_confidence=0.0,
        filters={},
        retrieved=[],
        retrieval_score=0.0,
        # ---- 工具与循环 ----
        tool_calls=[],
        tool_results=[],
        step_count=0,
        # ---- 输出与校验 ----
        answer="",
        cited=[],
        confidence=0.0,
        verified=False,
        verify_notes=[],
        need_human=False,
        error=None,
    )

def _clean_history(history: list[dict] | None, limit: int) -> list[dict]:
    cleaned: list[dict] = []
    for msg in history or []:
        if not isinstance(msg, dict):
            continue
        role = msg.get("role")
        content = msg.get("content")
        if role not in ("user", "assistant"):
            continue
        if not isinstance(content, str) or not content.strip():
            continue
        cleaned.append({"role": role, "content": content})
    return cleaned[-limit:]

def state_to_trace(state: AgentState) -> dict:
    if not isinstance(state, dict):
        return {}

    retrieved = state.get("retrieved") or []
    # ① 只记 id —— 有了 docId 就能去 ES 反查原文，日志里不必留副本
    retrieved_ids: list[str] = []
    for doc in retrieved:
        if not isinstance(doc, dict):
            continue
        doc_id = doc.get("docId") or doc.get("doc_id") or doc.get("id")
        if doc_id:
            retrieved_ids.append(str(doc_id))

    # ② 工具名 + 参数**摘要**（参数本身可能有用户输入的地址/昵称）
    tool_calls = []
    for call in state.get("tool_calls") or []:
        if not isinstance(call, dict):
            continue
        name = call.get("name") or call.get("tool") or "unknown"
        tool_calls.append(
            {"name": str(name), "args": _summarize_args(call.get("args"))}
        )

    started = state.get("started_at")
    latency_ms = int((time.perf_counter() - started) * 1000) if started else None

    return {
        "request_id": state.get("request_id") or "",
        "session_id": state.get("session_id") or "",
        "user_id": state.get("user_id"),
        "intent": state.get("intent") or "",
        "intent_confidence": round(float(state.get("intent_confidence") or 0.0), 3),
        "step_count": int(state.get("step_count") or 0),
        "retrieved_count": len(retrieved),
        "retrieved_ids": retrieved_ids,
        "retrieval_score": round(float(state.get("retrieval_score") or 0.0), 4),
        "filters": dict(state.get("filters") or {}),  # 结构化条件无 PII，可留
        "tool_calls": tool_calls,
        "tool_results_count": len(state.get("tool_results") or []),
        "cited": list(state.get("cited") or []),
        "answer_len": len(state.get("answer") or ""),  # 长度而非内容
        "confidence": round(float(state.get("confidence") or 0.0), 3),
        "verified": bool(state.get("verified")),
        "verify_notes": list(state.get("verify_notes") or []),
        "need_human": bool(state.get("need_human")),
        "error": state.get("error"),
        "latency_ms": latency_ms,
    }

def _summarize_args(args: Any) -> dict:
    if not isinstance(args, dict):
        return {}
    summary: dict[str, Any] = {}
    for key, value in args.items():
        name = str(key)
        if value is None or isinstance(value, (int, float, bool)):
            summary[name] = value
        elif isinstance(value, str):
            summary[name] = f"str(len={len(value)})"
        elif isinstance(value, (list, tuple, dict)):
            summary[name] = f"{type(value).__name__}(len={len(value)})"
        else:
            summary[name] = type(value).__name__
    return summary
