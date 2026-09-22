
import asyncio
import json
import logging
import re
from typing import Any

from langgraph.graph import END

from app.agents.state import AgentState, Intent
from app.chains.prompts import (
    FALLBACK_NO_DOC,
    FALLBACK_OUT_OF_SCOPE,
    FALLBACK_TOOL_ERROR,
    ROUTER_SYSTEM_PROMPT,
    ROUTER_USER_TEMPLATE,
    build_messages,
    format_docs,
    render_system_prompt,
)
from app.chains.rag_chain import (
    DEFAULT_TOP_K,
    repair_citations,
    retrieve_with_gate,
)
from app.core.llm_factory import get_chat_llm, get_router_llm

logger = logging.getLogger(__name__)

NODE_ROUTER = "router"
NODE_RETRIEVE = "retrieve"
NODE_TOOL_CALL = "tool_call"
NODE_GENERATE = "generate"
NODE_VERIFY = "verify"
NODE_HUMAN_REVIEW = "human_review"

# 路由置信度下限：低于它一律按超范围处理（宁可拒答，不要走错链路）
ROUTER_MIN_CONFIDENCE = 0.5

async def router_node(state: AgentState) -> dict:
    question = _last_user_message(state)

    blocked = _hit_keyword(question, _BLOCK_KEYWORDS)
    if blocked:
        logger.info("[router] 命中拦截词 %r → out_of_scope", blocked)
        return _out_of_scope_update(f"blocked:{blocked}")

    # ② 规则：确定性 + 零延迟 + 零成本，能覆盖多少就覆盖多少
    for intent, keywords in _INTENT_RULES:
        hit = _hit_keyword(question, keywords)
        if hit:
            logger.info("[router] 规则命中 %r → %s", hit, intent.value)
            return {"intent": intent.value, "intent_confidence": 1.0}

    # ③ 长尾才交给小模型（按 llm_factory 的分级，路由必须是小模型）
    intent, confidence = await _classify_by_llm(question, state)
    if (
        intent is None
        or intent is Intent.OUT_OF_SCOPE
        or confidence < ROUTER_MIN_CONFIDENCE
    ):
        return _out_of_scope_update(
            "unparsable" if intent is None else f"low_confidence:{confidence}"
        )
    return {"intent": intent.value, "intent_confidence": confidence}

_BLOCK_KEYWORDS: tuple[str, ...] = (
    # 诱导/违规：刷单刷评、代写
    "好评", "刷单", "刷评", "刷销量", "代写", "水军",
    # 医疗
    "治病", "疗效", "失眠", "处方", "养生", "偏方",
    # 金融法律
    "投资", "股票", "基金", "理财", "涨停", "起诉", "律师", "官司",
    # 其它高风险
    "论文", "作业", "考试答案", "政治", "破解", "越狱", "漏洞",
)

_INTENT_RULES: tuple[tuple[Intent, tuple[str, ...]], ...] = (
    (
        Intent.ORDER_QUERY,
        ("我的订单", "订单到哪", "物流", "快递", "运单", "发货了吗",
         "还没发货", "我买的", "退款进度", "退货进度", "订单状态"),
    ),
    (
        Intent.POLICY_QA,
        ("退货", "退款", "七天无理由", "包邮", "运费", "发票", "保修",
         "申诉", "处罚", "违规", "平台规则", "发货时效", "几天发货",
         "多久发货", "怎么售后"),
    ),
    (
        Intent.CREATOR_ASSIST,
        ("上架", "发布作品", "开店", "定价", "标题怎么写", "描述怎么写",
         "怎么填", "改价", "我的作品", "草稿", "素材要求"),
    ),
    (
        Intent.PRODUCT_GUIDE,
        ("推荐", "有什么", "想要", "找一款", "找找", "多少钱", "买",
         "礼物", "陶瓷", "手作", "手工", "杯子", "摆件", "预算"),
    ),
)

_JSON_RE = re.compile(r"\{[^{}]*\}", re.S)

def _hit_keyword(question: str, keywords: tuple[str, ...]) -> str:
    """返回第一个命中的关键词；没命中返回 ''（便于日志里直接打出来）。"""
    for word in keywords:
        if word and word in question:
            return word
    return ""

def _out_of_scope_update(reason: str) -> dict:
    logger.info("[router] 判定超范围 reason=%s", reason)
    return {
        "intent": Intent.OUT_OF_SCOPE.value,
        "intent_confidence": 1.0,
        "answer": FALLBACK_OUT_OF_SCOPE,
        "cited": [],
        "confidence": 1.0,
        "verified": True,
    }

async def _classify_by_llm(
    question: str, state: AgentState
) -> "tuple[Intent | None, float]":
    if not question:
        return None, 0.0
    messages = build_messages(
        ROUTER_SYSTEM_PROMPT, [], ROUTER_USER_TEMPLATE.format(question=question), ""
    )
    try:
        raw = await get_router_llm().chat(messages)
    except Exception:  # noqa: BLE001
        logger.warning("[router] 路由模型调用失败，按超范围处理")
        return None, 0.0

    match = _JSON_RE.search(str(raw or ""))
    if not match:
        logger.warning("[router] 路由输出不是 JSON：%r", raw)
        return None, 0.0
    try:
        payload = json.loads(match.group(0))
    except ValueError:
        logger.warning("[router] 路由 JSON 解析失败：%r", match.group(0))
        return None, 0.0

    intent = Intent.parse(payload.get("intent"))
    try:
        confidence = float(payload.get("confidence") or 0.0)
    except (TypeError, ValueError):
        confidence = 0.0
    confidence = max(0.0, min(1.0, confidence))
    logger.info(
        "[router] 模型路由 request_id=%s → %s (%.2f)",
        state.get("request_id") or "", intent, confidence,
    )
    return intent, confidence

async def retrieve_node(state: AgentState) -> dict:
    question = _last_user_message(state)

    docs, reason = await retrieve_with_gate(question, DEFAULT_TOP_K)

    if reason:
        fallback = (
            FALLBACK_OUT_OF_SCOPE if reason == "domain_mismatch" else FALLBACK_NO_DOC
        )
        logger.info("[retrieve] 短路 reason=%s", reason)
        return {
            "retrieved": [],
            "retrieval_score": 0.0,
            "answer": fallback,
            "cited": [],
            "confidence": 1.0,
            "verified": True,
        }

    return {"retrieved": docs, "retrieval_score": _top_score(docs)}

def _top_score(docs: list[dict]) -> float:
    best = 0.0
    for doc in docs:
        if not isinstance(doc, dict):
            continue
        try:
            best = max(best, float(doc.get("score") or 0.0))
        except (TypeError, ValueError):
            continue
    return best

async def tool_call_node(state: AgentState) -> dict:
    ...

async def generate_node(state: AgentState) -> dict:
    if str(state.get("answer") or "").strip():
        return {}

    docs = [d for d in (state.get("retrieved") or []) if isinstance(d, dict)]
    question = _last_user_message(state)
    # 历史 = 除本轮之外的消息（本轮 user 由 build_messages 单独拼到最靠近问题的位置）
    history = list(state.get("messages") or [])[:-1]

    messages = build_messages(
        render_system_prompt(state.get("intent") or ""),
        history,
        question,
        format_docs(docs),
    )
    try:
        answer = await get_chat_llm().chat(messages)
    except asyncio.CancelledError:
        # 前端断开 → 原样抛出，让上层取消任务（否则留下"没人读还在烧钱"的僵尸任务）
        raise
    except Exception:  # noqa: BLE001
        logger.exception("[generate] 生成失败，返回兜底话术")
        return {
            "answer": FALLBACK_TOOL_ERROR,
            "cited": [],
            "confidence": 0.0,
            "verified": False,
            "error": "llm_error",
        }

    # 引用抽取复用 chains 的那份实现（编号规则全局只能有一套）
    answer, cited = repair_citations(str(answer or ""), len(docs))
    return {
        "answer": answer,
        "cited": cited,
        "confidence": _heuristic_confidence(answer, docs, cited),
        "verified": False,
    }

def _heuristic_confidence(answer: str, docs: list[dict], cited: list[str]) -> float:
    if not answer:
        return 0.0
    if not docs:
        return 0.3
    return 0.85 if cited else 0.6

async def verify_node(state: AgentState) -> dict:
    ...

def route_after_router(state: AgentState) -> str:
    intent = Intent.parse(state.get("intent"))

    if intent is None:
        logger.warning("[route] intent 无法识别，直接结束：%r", state.get("intent"))
        return END

    # ② 超范围 → 直接结束（router 已写好 answer，retrieve/generate 都不该被调用）
    if intent is Intent.OUT_OF_SCOPE:
        return END

    # ③ 需要实时数据的两类 → 工具（第 4 步接入 tool_call 节点后生效）
    if intent in (Intent.ORDER_QUERY, Intent.CREATOR_ASSIST):
        return NODE_TOOL_CALL

    # ④ 其余 → 先检索
    return NODE_RETRIEVE

def route_after_retrieve(state: AgentState) -> str:

    # ① 已有终态答案（检索空 / 门控拒答已写好 answer）→ 结束，不进 generate
    if str(state.get("answer") or "").strip() and not state.get("retrieved"):
        return END

    # ② 检索报错 → 也是终态（error 已写进 state，由上层返回兜底）
    if state.get("error"):
        return END

    return NODE_GENERATE

def _last_user_message(state: AgentState) -> str:
    for msg in reversed(list(state.get("messages") or [])):
        if isinstance(msg, dict) and msg.get("role") == "user":
            return str(msg.get("content") or "")
    return ""

def route_after_tool_call(state: AgentState) -> str:
    ...

def route_after_verify(state: AgentState) -> str:
    ...
