
import asyncio
import inspect
import logging
import re
import time
from functools import lru_cache
from typing import Any, AsyncIterator

from app.chains.prompts import (
    FALLBACK_NO_DOC,
    FALLBACK_OUT_OF_SCOPE,
    FALLBACK_TOOL_ERROR,
    build_messages,
    format_docs,
    render_system_prompt,
)
from app.core.llm_factory import get_chat_llm

logger = logging.getLogger(__name__)

# 送进 prompt 的资料条数（= 引用编号上限）
DEFAULT_TOP_K = 5

# 引用编号：只认 [1]~[99]，避免把正文里的 "[2024年]" 之类误判成引用
_CITE_RE = re.compile(r"\[(\d{1,2})\]")

try:  # pragma: no cover - 取决于运行环境
    from langchain_core.runnables import RunnableLambda as _RunnableLambda

    _HAS_LCEL = True
except Exception:  # noqa: BLE001
    _RunnableLambda = None
    _HAS_LCEL = False

class _Lambda:
    """自研最小 Runnable：接口形状与 LCEL 对齐（invoke/ainvoke/astream + `|`）。"""

    def __init__(self, func: Any):
        self._func = func

    async def ainvoke(self, value: Any, **_: Any) -> Any:
        result = self._func(value)
        if inspect.isawaitable(result):
            result = await result
        return result

    def invoke(self, value: Any, **_: Any) -> Any:
        result = self._func(value)
        if inspect.isawaitable(result):
            # 同步入口只在脚本/单测里用；生产链路全部走 ainvoke
            return asyncio.run(result)
        return result

    async def astream(self, value: Any, **_: Any) -> AsyncIterator[Any]:
        yield await self.ainvoke(value)

    def __or__(self, other: Any) -> "_Sequence":
        return _Sequence([self, other])

class _Sequence:
    """`a | b | c` 的产物：顺序执行，逐步产出中间态（与 LCEL 语义一致）。"""

    def __init__(self, steps: list[Any]):
        self._steps = list(steps)

    async def ainvoke(self, value: Any, **kwargs: Any) -> Any:
        current = value
        for step in self._steps:
            current = await step.ainvoke(current, **kwargs)
        return current

    def invoke(self, value: Any, **kwargs: Any) -> Any:
        current = value
        for step in self._steps:
            current = step.invoke(current, **kwargs)
        return current

    async def astream(self, value: Any, **kwargs: Any) -> AsyncIterator[Any]:
        current = value
        for step in self._steps:
            current = await step.ainvoke(current, **kwargs)
            yield current

    def __or__(self, other: Any) -> "_Sequence":
        return _Sequence(self._steps + [other])

def _runnable(func: Any) -> Any:
    """把 step 函数包成 Runnable：有 langchain-core 就用真的，没有就用兜底。"""
    if _HAS_LCEL:
        return _RunnableLambda(func)
    return _Lambda(func)

def build_retriever() -> Any:

    async def _invoke(query: Any) -> list[dict]:
        # 兼容两种入参：裸 query（LCEL 里 retriever 的惯例）或 dict（带 top_k/satoken）
        if isinstance(query, dict):
            q = str(query.get("question") or query.get("query") or "")
            k = int(query.get("top_k") or DEFAULT_TOP_K)
            token = str(query.get("satoken") or "")
        else:
            q, k, token = str(query or ""), DEFAULT_TOP_K, ""
        if not q.strip():
            return []
        return await _retrieve_docs(q, k, token)

    return _runnable(_invoke)

async def _retrieve_docs(
    question: str,
    top_k: int = DEFAULT_TOP_K,
    satoken: str = "",
) -> list[dict]:
    try:
        from app.rag.retriever import search_for_rag

        docs = await search_for_rag(question, top_k=top_k, satoken=satoken)
    except Exception:  # noqa: BLE001
        logger.exception("[rag_chain] 检索失败，降级为空结果：question=%r", question)
        return []
    return docs if isinstance(docs, list) else []

async def _invoke_retriever(
    retriever: Any, question: str, top_k: int, satoken: str
) -> list[dict]:
    """
    调用 retriever（默认走 _retrieve_docs；测试时可注入 mock）。

    注入物兼容三种形态：Runnable（有 ainvoke）/ 同步可调用 / 异步可调用。
    """
    if retriever is None:
        return await _retrieve_docs(question, top_k, satoken)
    try:
        if hasattr(retriever, "ainvoke"):
            result = retriever.ainvoke(
                {"question": question, "top_k": top_k, "satoken": satoken}
            )
        else:
            result = retriever(question, top_k)
        if inspect.isawaitable(result):
            result = await result
    except Exception:  # noqa: BLE001
        logger.exception("[rag_chain] 注入的 retriever 调用失败，降级为空结果")
        return []
    return result if isinstance(result, list) else []

def _gate(question: str, docs: list[dict]) -> "tuple[bool, str]":
    try:
        from app.rag.retriever import get_default_retriever

        return get_default_retriever().should_reject(docs, question)
    except Exception:  # noqa: BLE001
        logger.exception("[rag_chain] 门控判定失败，放行（降级为不拒答）")
        return False, ""

def _gated(payload: dict, reason: str, fallback: str) -> dict:
    """被短路时的统一返回：docs 清空 + gated=True，下游据此**不调模型**。"""
    return {
        **payload,
        "docs": [],
        "context": "",
        "gated": True,
        "gate_reason": reason,
        "fallback": fallback,
    }

PRODUCT_FALLBACK_BLOCKLIST = (
    "退货", "退款", "换货", "运费", "包邮", "发货", "物流", "快递",
    "售后", "发票", "保修", "投诉", "举报", "违规", "侵权", "版权",
    "入驻", "开店", "保证金", "结算", "提现",
)

_QUERY_LEAD_WORDS = (  # 句首：寒暄与主谓
    "请问", "麻烦", "帮我", "我想", "我要", "我想要", "给我", "你们", "你的", "你家",
    "店里", "店铺", "这里", "有没有", "有没有卖", "有", "想", "看看", "找找", "推荐",
)
_QUERY_TAIL_WORDS = (  # 句尾：语气与谓词
    "吗", "呢", "吧", "啊", "呀", "嘛", "有没有", "有没有卖", "有", "卖", "买的",
    "推荐", "看看", "的", "了", "吗呢",
)
_QUERY_MID_WORDS = (  # 中间：动词、量词与指代词，直接删除
    "帮我", "我想", "给我", "推荐一下", "推荐", "看一下", "看看", "找一下", "找找",
    "有没有", "想买", "想要", "买一个", "买点", "买", "一些", "一点", "一个", "几款",
    "什么", "哪些", "哪个", "那种",
    "这款", "这个", "那个", "这种", "还能", "可以", "能", "个",
)
# 检索词太短就没区分度（"布"能匹配半个库），太长说明没洗干净
_QUERY_MIN_LEN = 2
_QUERY_MAX_LEN = 20

async def _product_queries(question: str) -> list[str]:
    text = str(question or "").strip()
    if not text:
        return []

    # ① 统一标点为空格：中英文标点混排会让后面的剥离全部失配
    chars: list[str] = []
    for ch in text:
        if ch.isalnum():
            chars.append(ch)
        else:
            chars.append(" ")
    cleaned = "".join(chars)
    # 压缩连续空格（中文没有空格，这里主要是英文问句）
    compact = " ".join(cleaned.split())

    # ② 中间词删除 → 句首剥离 → 句尾剥离（顺序不能反：先删中间再剥两端，
    #    否则 "你们有陶瓷杯吗" 里的 "你们" 会被中间词规则先删掉，逻辑仍等价但更脆）
    for word in _QUERY_MID_WORDS:
        compact = compact.replace(word, " ")
    compact = " ".join(compact.split())

    # 句首/句尾剥离：循环剥离直到不再变化（"请问你们有…吗呢" 这种叠词）
    changed = True
    while changed:
        changed = False
        for word in _QUERY_LEAD_WORDS:
            if compact.startswith(word) and len(compact) > len(word):
                compact = compact[len(word):].lstrip()
                changed = True
        for word in _QUERY_TAIL_WORDS:
            if compact.endswith(word) and len(compact) > len(word):
                compact = compact[: -len(word)].rstrip()
                changed = True
        compact = " ".join(compact.split())

    queries: list[str] = []

    def _add(candidate: str) -> None:
        candidate = candidate.strip()
        if _QUERY_MIN_LEN <= len(candidate) <= _QUERY_MAX_LEN and candidate not in queries:
            queries.append(candidate)

    _add(compact.replace(" ", ""))  # 中文检索词内部不该留空格

    try:
        from app.tools.product_tools import _map_category  # noqa: PLC0415

        category = await _map_category(compact.replace(" ", ""))
        if category:
            _add(category)
    except Exception:  # noqa: BLE001
        logger.debug("[rag_chain] 品类词抽取失败，只用核心短语", exc_info=True)

    # ④ 全被剥光了（"有吗"）→ 退回原句，宁可召回宽也不要 0 候选
    if not queries:
        raw = " ".join(text.split())
        if len(raw) >= _QUERY_MIN_LEN:
            queries.append(raw[:_QUERY_MAX_LEN])

    logger.info("[rag_chain] 问句 %r → 商品检索候选 %s", text, queries)
    return queries[:2]

async def _product_fallback_docs(question: str, satoken: str) -> list[dict]:
    q = str(question or "").strip()
    if not q:
        return []
    if any(word in q for word in PRODUCT_FALLBACK_BLOCKLIST):
        logger.info("[rag_chain] 政策类问题，跳过商品降级：%r", q)
        return []

    products: list[dict] = []
    try:
        # 局部 import：商品工具依赖 httpx 与配置，import 期不该被拉起
        from app.tools.base import ToolContext
        from app.tools.product_tools import search_products

        ctx = ToolContext(satoken=satoken or "")
        for keyword in await _product_queries(q):
            try:
                result = await search_products(ctx, keyword=keyword, size=DEFAULT_TOP_K)
            except Exception:  # noqa: BLE001
                logger.exception("[rag_chain] 商品降级异常：keyword=%r", keyword)
                continue

            if not isinstance(result, dict) or not result.get("ok"):
                logger.info(
                    "[rag_chain] 商品降级未成功：keyword=%r error_code=%s",
                    keyword, (result or {}).get("error_code"),
                )
                continue

            hit = result.get("data") or []
            if hit:
                logger.info("[rag_chain] 商品降级命中：keyword=%r → %d 条", keyword, len(hit))
                products = hit
                break
            logger.info("[rag_chain] 商品降级无结果：keyword=%r", keyword)
    except Exception:  # noqa: BLE001
        # 兜住 _product_queries 本身的异常（它内部已 try，这里是双保险）
        logger.exception("[rag_chain] 商品降级检索异常，按 no_doc 处理：%r", q)
        return []

    if not products:
        return []

    docs: list[dict] = []
    for index, item in enumerate(products):
        if not isinstance(item, dict):
            continue
        docs.append(
            {
                # docId 用 "product-<id>"：前端引用列表据此展示，也便于与 chunk 区分
                "docId": f"product-{item.get('id')}",
                # productId 是 format_docs 去重的**最高优先级**键（同一商品只留一条）
                "productId": str(item.get("id") or ""),
                "title": str(item.get("name") or ""),
                "source": "product",
                "url": str(item.get("url") or ""),
                "content": _product_doc_content(item),
                "score": round(1.0 - index * 0.01, 4),
            }
        )
    logger.info("[rag_chain] 商品降级命中 %d 条：%r", len(docs), q)
    return docs

def _product_doc_content(item: dict) -> str:
    segments: list[str] = []
    name = str(item.get("name") or "").strip()
    if name:
        segments.append(f"商品名：{name}")
    category = str(item.get("category") or "").strip()
    if category:
        segments.append(f"分类：{category}")
    price = item.get("price")
    if price is not None:
        # %g 去掉多余的 .0（¥89 比 ¥89.0 更像人话）
        segments.append(f"价格：¥{price:g}" if isinstance(price, (int, float)) else f"价格：{price}")
    stock = item.get("stock")
    if stock is not None:
        segments.append("库存：暂时无货" if not item.get("in_stock") else f"库存：{stock} 件")
    point = str(item.get("selling_point") or "").strip()
    if point:
        segments.append(point)
    return "；".join(segments)

async def retrieve_with_gate(
    question: str,
    top_k: int = DEFAULT_TOP_K,
    satoken: str = "",
    retriever: Any = None,
) -> "tuple[list[dict], str]":
    q = str(question or "").strip()
    if not q:
        return [], "empty_query"

    docs = await _invoke_retriever(retriever, q, int(top_k or DEFAULT_TOP_K), satoken)
    if not docs:
        # RAG（知识库）召回空 → 降级去商品库搜一次。
        # 知识库只有规则/保养语料，商品在 MySQL/ES 里，只查一个必然漏一半。
        docs = await _product_fallback_docs(q, satoken)
        if docs:
            return docs, ""
        return [], "no_doc"

    rejected, reason = _gate(q, docs)
    if rejected:
        product_docs = await _product_fallback_docs(q, satoken)
        if product_docs:
            return product_docs, ""
        return [], reason or "low_score"
    return docs, ""

async def _step_retrieve(payload: dict, retriever: Any = None) -> dict:
    question = str(payload.get("question") or "").strip()
    top_k = int(payload.get("top_k") or DEFAULT_TOP_K)
    satoken = str(payload.get("satoken") or "")

    docs, reason = await retrieve_with_gate(question, top_k, satoken, retriever)
    if reason:
        logger.info("[rag_chain] 短路 reason=%s，不调用模型：%r", reason, question)
        fallback = (
            FALLBACK_OUT_OF_SCOPE if reason == "domain_mismatch" else FALLBACK_NO_DOC
        )
        return _gated(payload, reason, fallback)

    return {
        **payload,
        "docs": docs,
        "context": format_docs(docs),
        "gated": False,
        "gate_reason": "",
        "fallback": "",
    }

def _step_prompt(payload: dict) -> dict:
    """② 拼 prompt（门控命中时**直接跳过**，不生成 messages）。"""
    if payload.get("gated"):
        return payload
    messages = build_messages(
        render_system_prompt(payload.get("intent") or ""),
        payload.get("history") or [],
        str(payload.get("question") or ""),
        payload.get("context") or "",
    )
    return {**payload, "messages": messages}

async def _step_llm(payload: dict) -> dict:
    if payload.get("gated"):
        return {
            **payload,
            "answer": payload.get("fallback") or FALLBACK_NO_DOC,
        }
    try:
        llm = get_chat_llm()
        answer = await llm.chat(payload.get("messages") or [])
    except Exception:  # noqa: BLE001
        # 超时/鉴权失败/供应商 500 都走这里 —— 用户看到的是人话，不是 traceback
        logger.exception("[rag_chain] 模型调用失败，返回兜底话术")
        return {**payload, "answer": FALLBACK_TOOL_ERROR}
    return {**payload, "answer": answer or ""}

def _step_parse(payload: dict) -> dict:
    """④ 解析：抽取引用编号 + 输出裁剪（对外只暴露约定好的字段）。"""
    answer = str(payload.get("answer") or "")
    docs = payload.get("docs") or []
    cited: list[str] = []
    if not payload.get("gated"):
        answer, cited = repair_citations(answer, len(docs))
        answer = _post_check(answer, payload.get("context") or "", docs)
    return {
        "answer": answer,
        "cited": cited,
        "docs": docs,
        "context": payload.get("context") or "",
        "gated": bool(payload.get("gated")),
        "gate_reason": payload.get("gate_reason") or "",
        "fallback": payload.get("fallback") or "",
        "intent": payload.get("intent") or "",
        "question": payload.get("question") or "",
    }

def repair_citations(answer: str, doc_count: int) -> "tuple[str, list[str]]":
    if not answer or doc_count <= 0:
        return answer, []

    cited: list[str] = []

    def _replace(match: "re.Match[str]") -> str:
        index = int(match.group(1))
        if 1 <= index <= doc_count:
            token = f"[{index}]"
            if token not in cited:
                cited.append(token)
            return token
        return ""

    repaired = _CITE_RE.sub(_replace, answer)
    # 删掉编号后可能留下连续空格 / " 。" 这类排版残迹
    repaired = re.sub(r"[ \t]{2,}", " ", repaired).replace(" 。", "。")
    return repaired.strip(), cited

def _post_check(answer: str, context: str, docs: list[dict]) -> str:
    if not answer:
        return answer
    try:
        from app.core.guardrails import verify_grounding

        result = verify_grounding(answer, context)
    except Exception:  # noqa: BLE001
        logger.debug("[rag_chain] 答后校验不可用，跳过")
        return answer

    if result is None or getattr(result, "passed", True):
        return answer
    if getattr(result, "severity", "soft") == "hard":
        logger.warning(
            "[rag_chain] 答后校验 hard 失败，降级为兜底：%s",
            getattr(result, "issues", []),
        )
        return FALLBACK_NO_DOC
    return answer

@lru_cache(maxsize=None)
def _session_mgr() -> Any:
    from app.services import session as session_mod

    factory = getattr(session_mod, "get_session_manager", None)
    return factory() if callable(factory) else session_mod.SessionManager()

async def _append_history(session_id: str, user_msg: str, answer: str) -> None:
    if not session_id or not user_msg:
        return
    try:
        await _session_mgr().append_messages(
            session_id,
            [
                {"role": "user", "content": user_msg},
                {"role": "assistant", "content": answer},
            ],
        )
    except Exception:  # noqa: BLE001
        logger.warning("[rag_chain] 会话历史写入失败（不影响本次回答）")

def build_rag_chain(retriever: Any = None) -> Any:
    # retriever 可注入（单测里塞一个返回固定 docs 的假检索器）
    async def _retrieve(payload: dict) -> dict:
        return await _step_retrieve(payload, retriever)

    return (
        _runnable(_retrieve)
        | _runnable(_step_prompt)
        | _runnable(_step_llm)
        | _runnable(_step_parse)
    )

async def astream_rag(
    question: str,
    session_id: str,
    satoken: str = "",
    history: list[dict] | None = None,
    intent: str = "",
    top_k: int = DEFAULT_TOP_K,
) -> AsyncIterator[dict]:
    started = time.perf_counter()
    question_text = (question or "").strip()
    history = history or []
    docs: list[dict] = []
    answer = ""
    reason = ""

    try:
        if not question_text:
            reason = "empty_query"
        else:
            docs, reason = await retrieve_with_gate(
                question_text, int(top_k or DEFAULT_TOP_K), satoken
            )

        if reason:
            # 检索空 / 门控命中 → 直接吐兜底话术，**一次模型都不调**
            answer = (
                FALLBACK_OUT_OF_SCOPE if reason == "domain_mismatch" else FALLBACK_NO_DOC
            )
            yield {"event": "message", "data": {"chunk": answer}}
        else:
            context = format_docs(docs)
            messages = build_messages(
                render_system_prompt(intent), history, question_text, context
            )
            parts: list[str] = []
            try:
                llm = get_chat_llm()
                async for chunk in llm.stream_chat(messages):
                    if not chunk:
                        continue
                    parts.append(chunk)
                    yield {"event": "message", "data": {"chunk": chunk}}
            except asyncio.CancelledError:
                raise
            except Exception:  # noqa: BLE001
                logger.exception("[rag_chain] 生成阶段异常，改为吐兜底话术")
                parts = [FALLBACK_TOOL_ERROR]
                yield {"event": "message", "data": {"chunk": FALLBACK_TOOL_ERROR}}

            answer = _post_check("".join(parts), context, docs)
    except asyncio.CancelledError:
        raise
    except Exception:  # noqa: BLE001
        logger.exception("[rag_chain] 流式链路异常")
        answer = FALLBACK_TOOL_ERROR
        yield {"event": "message", "data": {"chunk": FALLBACK_TOOL_ERROR}}

    # 方案 A：正文先流式吐完，最后单独发引用事件
    #（流式过程中拿不到完整 JSON，边收边解析必错 —— 这是"流式与结构化的冲突"）
    answer, cited = repair_citations(answer or FALLBACK_TOOL_ERROR, len(docs))
    yield {"event": "citations", "data": {"cited": cited, "docs": docs}}
    yield {
        "event": "done",
        "data": {
            "status": "empty" if reason else "ok",
            "reason": reason,
            "elapsed_ms": int((time.perf_counter() - started) * 1000),
        },
    }

    if question_text and answer:
        await _append_history(session_id, question_text, answer)
