
import asyncio
import json
import logging
import re
import time
from collections import OrderedDict
from typing import Any

logger = logging.getLogger(__name__)

DOC_FIELDS = ("docId", "content", "title", "source", "url")

SOURCE_FIELDS = [
    "docId", "title", "content", "source", "url",
    "status", "price", "stock", "category", "productId", "updatedAt",
]

FILTER_WHITELIST = {
    "priceMin", "priceMax", "category", "categoryId",
    "source", "status", "inStock",
}

POLICY_SOURCES = {"policy", "faq", "guide"}
COMMERCIAL_HINTS = ("多少钱", "价格", "便宜", "打折", "库存", "有货", "想买", "下单", "优惠券")

def _as_number(value: Any) -> "float | None":
    if value is None or isinstance(value, bool):
        return None
    if isinstance(value, (int, float)):
        return float(value)
    match = re.search(r"-?\d+(?:\.\d+)?", str(value))
    # 抽不到返回 None，不抛异常 —— 让调用方决定丢弃还是报警
    return float(match.group()) if match else None

def sanitize_filters(raw: "dict | None") -> dict:
    cleaned: dict = {}
    for key, value in (raw or {}).items():
        if key not in FILTER_WHITELIST:
            logger.warning("[retriever] filters 非白名单字段被丢弃：%s=%r", key, value)
            continue
        if value is None or value == "":
            continue
        if key in ("priceMin", "priceMax"):
            number = _as_number(value)
            if number is not None:      # 抽不到 → 丢弃（退化成纯语义检索，可接受）
                cleaned[key] = number
        elif key == "inStock":
            cleaned[key] = bool(value)
        else:
            # str()：ES term 查询要求标量，dict/list 直接 400
            cleaned[key] = str(value)
    return cleaned

def build_filter_clauses(filters: "dict | None") -> "list[dict]":
    clauses: "list[dict]" = []
    if not filters:
        return clauses

    for key in ("source", "category", "categoryId"):
        if filters.get(key) is not None:
            clauses.append({"term": {key: filters[key]}})

    if filters.get("priceMin") is not None or filters.get("priceMax") is not None:
        range_body: dict = {}
        if filters.get("priceMin") is not None:
            range_body["gte"] = filters["priceMin"]
        if filters.get("priceMax") is not None:
            range_body["lte"] = filters["priceMax"]
        clauses.append({"range": {"price": range_body}})

    if filters.get("inStock"):
        clauses.append({"range": {"stock": {"gt": 0}}})

    explicit_status = filters.get("status") is not None
    querying_product = (
        filters.get("source") == "product"
        or filters.get("priceMin") is not None
        or filters.get("priceMax") is not None
        or bool(filters.get("inStock"))
    )
    if explicit_status:
        clauses.append({"term": {"status": int(filters["status"])}})
    elif querying_product:
        clauses.append({"term": {"status": 1}})
    return clauses

class HybridRetriever:

    def __init__(self, **deps: Any) -> None:
        self._es = deps.get("es_client")
        self._embedder = deps.get("embedder")
        self._reranker = deps.get("reranker")
        self._settings = deps.get("settings")

        if self._es is None:
            logger.warning("[HybridRetriever] es_client 未注入，两路召回将返回空")
        if self._embedder is None:
            logger.warning("[HybridRetriever] embedder 未注入，向量路不可用")
        if self._reranker is None:
            logger.warning("[HybridRetriever] reranker 未注入，将跳过重排")
        if self._settings is None:
            logger.warning("[HybridRetriever] settings 未注入，配置全部走默认值")

        self._rrf_k = self._cfg("rag_rrf_k", 60)
        self._reject_threshold = self._cfg("rag_reject_threshold", 0.3)
        # 未启用 reranker 时的备用阈值（RRF 量纲），见 should_reject 里的说明
        self._reject_threshold_rrf = self._cfg("rag_reject_threshold_rrf", 0.01)
        self._recall_top_k = self._cfg("rag_recall_top_k", 50)
        self._rerank_top_n = self._cfg("rag_rerank_top_n", 30)
        self._vector_min_len = self._cfg("rag_vector_min_len", 4)
        self._index = self._cfg("es_index_chunks", "rag_chunks")
        self._rerank_timeout = self._cfg("rerank_timeout_seconds", 1.5)

        # query embedding 的进程内 LRU（生产版：Redis 共享 + TTL，见 _embed_query_cached）
        self._embed_cache: "OrderedDict[str, list[float]]" = OrderedDict()
        self._embed_cache_size = 256

    def _cfg(self, name: str, default: Any) -> Any:
        if self._settings is None:
            return default
        # getattr 带默认 —— 配置项没加时也不会 AttributeError
        return getattr(self._settings, name, default)

    async def rewrite_query(
        self,
        query: str,
        history: "list[dict] | None" = None,
    ) -> dict:
        # 局部 import：rag 包被 import 时不拉起 llm 工厂（防循环依赖 + 延迟初始化）
        from app.core.llm_factory import get_router_llm

        history_filters = self._collect_history_filters(history)

        system_prompt = (
            "你是电商平台的查询改写器。把用户的口语问题改写成检索友好的表达，"
            "并抽取其中的结构化过滤条件。只输出 JSON，格式：\n"
            '{"semantic_query": "检索用表达", "filters": {}, "need_clarify": false}\n'
            "filters 只允许这些 key：priceMin, priceMax, category, categoryId, "
            "source, status, inStock。价格抽成数字。\n"
            "need_clarify 仅在问题为空或完全缺少主体、无法判断用户想要什么时为 true。"
        )
        user_payload: dict = {"query": query}
        if history_filters:
            user_payload["history_filters"] = history_filters
        messages = [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": json.dumps(user_payload, ensure_ascii=False)},
        ]
        try:
            raw = await get_router_llm().chat(
                messages, response_format={"type": "json_object"},
            )
            parsed = json.loads(raw)
            if not isinstance(parsed, dict):
                raise ValueError(f"模型输出形状不对：{type(parsed)}")
        except Exception as exc:  # noqa: BLE001
            logger.warning("[HybridRetriever] rewrite_query 失败，降级为原始 query：%s", exc)
            return {"semantic_query": query, "filters": {}, "need_clarify": False}

        filters = sanitize_filters(parsed.get("filters"))
        need_clarify = bool(parsed.get("need_clarify")) or len(query.strip()) < 2
        return {
            "semantic_query": str(parsed.get("semantic_query") or query),
            "filters": filters,
            "need_clarify": need_clarify,
        }

    @staticmethod
    def _collect_history_filters(history: "list[dict] | None") -> dict:
        if not history:
            return {}
        merged: dict = {}
        for item in history[-3:]:
            if not isinstance(item, dict):   # 防御式：历史可能是脏数据
                continue
            raw = item.get("filters")
            if isinstance(raw, dict):
                merged.update(sanitize_filters(raw))
        return merged

    async def bm25_search(self, query: str, filters: dict, top_k: int = 50) -> "list[dict]":
        if self._es is None or not query.strip():
            return []
        body = {
            "query": {
                "bool": {
                    "must": [{
                        "multi_match": {
                            "query": query,
                            "fields": ["title^3", "content"],
                            "type": "best_fields",
                            "minimum_should_match": "60%",
                        },
                    }],
                    "filter": build_filter_clauses(filters),
                },
            },
            "size": top_k,
            "_source": SOURCE_FIELDS,
        }
        try:
            response = await self._es.search(index=self._index, body=body)
        except Exception as exc:  # noqa: BLE001
            # ES 抖动 → 这一路降级为空，向量路还在跑（互不影响）
            logger.warning("[HybridRetriever] bm25_search 失败：%s", exc)
            return []
        from app.db.elasticsearch import extract_hits
        return [self._normalize_doc(h.get("_source", {}), h.get("_score"))
                for h in extract_hits(response)]

    async def vector_search(self, query: str, filters: dict, top_k: int = 50) -> "list[dict]":
        if self._es is None or self._embedder is None:
            return []
        if len(query.strip()) < self._vector_min_len:
            return []
        try:
            embedding = await self._embed_query_cached(query)
        except Exception as exc:  # noqa: BLE001
            logger.warning("[HybridRetriever] query embedding 失败，向量路降级：%s", exc)
            return []
        knn = {
            "field": "embedding",
            "query_vector": embedding,
            "k": top_k,
            "num_candidates": top_k * 4,
            "filter": build_filter_clauses(filters),
        }
        # 已经有 pre-filter 就不需要再留 query 字段：留了会引入二次打分，语义混乱
        body = {"knn": knn, "size": top_k, "_source": SOURCE_FIELDS}
        try:
            response = await self._es.search(index=self._index, body=body)
        except Exception as exc:  # noqa: BLE001
            logger.warning("[HybridRetriever] vector_search 失败：%s", exc)
            return []
        from app.db.elasticsearch import extract_hits
        return [self._normalize_doc(h.get("_source", {}), h.get("_score"))
                for h in extract_hits(response)]

    async def _embed_query_cached(self, query: str) -> "list[float]":
        cached = self._embed_cache.get(query)
        if cached is not None:
            self._embed_cache.move_to_end(query)
            return cached
        vector = await self._embedder.embed_query(query)
        if vector:
            self._embed_cache[query] = vector
            while len(self._embed_cache) > self._embed_cache_size:
                self._embed_cache.popitem(last=False)
        return vector

    def rrf_fuse(self, result_lists: "list[list[dict]]", k: int = 60) -> "list[dict]":
        fused: dict[str, dict] = {}
        for results in result_lists:
            for rank, doc in enumerate(results, start=1):
                doc_id = doc.get("docId")
                if not doc_id:
                    logger.warning("[HybridRetriever] rrf_fuse 丢弃无 docId 的结果：%r",
                                   doc.get("title", ""))
                    continue
                entry = fused.setdefault(doc_id, {
                    "doc": doc, "rrf_score": 0.0,
                    "best_rank": rank, "raw_score": float(doc.get("score") or 0.0),
                })
                entry["rrf_score"] += 1.0 / (k + rank)
                entry["best_rank"] = min(entry["best_rank"], rank)
                entry["raw_score"] = max(entry["raw_score"], float(doc.get("score") or 0.0))

        merged: "list[dict]" = []
        for entry in fused.values():
            doc = dict(entry["doc"])
            doc["rrf_score"] = entry["rrf_score"]
            doc["score"] = entry["rrf_score"]
            doc["raw_score"] = entry["raw_score"]
            doc["best_rank"] = entry["best_rank"]   # 二级排序要用，带回 doc
            merged.append(doc)
        merged.sort(key=lambda d: (-d["rrf_score"], d["best_rank"]))
        return merged

    async def rerank(
        self,
        query: str,
        docs: "list[dict]",
        top_n: int = 8,
        candidate_n: int = 30,
    ) -> "list[dict]":
        if not docs:
            return []
        if self._reranker is None or not self._reranker.is_enabled():
            return docs[:top_n]
        candidates = docs[:candidate_n]
        # 截断到 512 字：reranker 有长度限制，超长会报错或被服务端截
        texts = [d.get("content", "")[:512] for d in candidates]
        try:
            scores = await asyncio.wait_for(
                self._reranker.rerank(query, texts), timeout=self._rerank_timeout,
            )
        except Exception as exc:  # noqa: BLE001
            # 降级返回 RRF 顺序（RRF 排序本身也不差，质量损失可接受）
            logger.warning("[HybridRetriever] rerank 失败/超时，降级为 RRF 顺序：%s", exc)
            return docs[:top_n]
        if len(scores) != len(candidates):
            logger.warning("[HybridRetriever] rerank 分数条数(%d) != 候选(%d)，放弃重排",
                           len(scores), len(candidates))
            return docs[:top_n]
        reranked: "list[dict]" = []
        for doc, score in zip(candidates, scores):
            doc = dict(doc)
            doc["rerank_score"] = score
            doc["score"] = score
            reranked.append(doc)
        reranked.sort(key=lambda d: d["score"], reverse=True)
        return reranked[:top_n]

    def dedup(self, docs: "list[dict]") -> "list[dict]":
        best: dict[str, dict] = {}
        order: "list[str]" = []
        for doc in docs:
            doc_id = str(doc.get("docId") or "")
            product_id = str(doc.get("productId") or "")
            if doc_id:
                key = f"doc:{doc_id}"
            elif product_id:
                key = f"product:{product_id}"
            else:
                key = f"{doc.get('source', '')}:{doc.get('title', '')}"
            if key not in best:
                best[key] = doc
                order.append(key)
            elif float(doc.get("score") or 0) > float(best[key].get("score") or 0):
                best[key] = doc
        return [best[key] for key in order]

    def should_reject(self, docs: "list[dict]", query: "str | None" = None) -> "tuple[bool, str]":
        if not docs:
            return True, "no_doc"
        top_score = max(float(d.get("score") or 0.0) for d in docs)
        rerank_on = self._reranker is not None and self._reranker.is_enabled()
        threshold = self._reject_threshold if rerank_on else self._reject_threshold_rrf
        if top_score < threshold:
            logger.info(
                "[HybridRetriever] 拒答(low_score)：query=%r top_score=%.4f < %.2f "
                "(rerank=%s)", query, top_score, threshold, rerank_on,
            )
            return True, "low_score"
        if query and self._is_domain_mismatch(query, docs):
            logger.info("[HybridRetriever] 拒答(domain_mismatch)：query=%r", query)
            return True, "domain_mismatch"
        return False, ""

    @staticmethod
    def _is_domain_mismatch(query: str, docs: "list[dict]") -> bool:
        sources = {d.get("source", "") for d in docs}
        if not sources or not sources.issubset(POLICY_SOURCES):
            return False
        return any(hint in query for hint in COMMERCIAL_HINTS)

    async def search(
        self,
        query: str,
        top_k: int = 5,
        filters: "dict | None" = None,
        history: "list[dict] | None" = None,
    ) -> "list[dict]":
        started = time.perf_counter()
        try:
            rewritten = await self.rewrite_query(query, history)
            if rewritten.get("need_clarify"):
                logger.info(
                    "[HybridRetriever] need_clarify=True → 降级为原始 query 检索：%r",
                    query,
                )
                semantic_query = query
                # 改写不可信 → 也不采信它抽的 filters，只保留上层显式传入的
                merged_filters = dict(filters or {})
            else:
                semantic_query = rewritten["semantic_query"]
                merged_filters = {**rewritten.get("filters", {}), **(filters or {})}

            bm25_results, vector_results = await asyncio.gather(
                self.bm25_search(semantic_query, merged_filters, top_k=self._recall_top_k),
                self.vector_search(semantic_query, merged_filters, top_k=self._recall_top_k),
                return_exceptions=True,
            )
            bm25_docs, vector_docs = self._unpack_gather(
                [bm25_results, vector_results], "bm25", "vector",
            )

            fused = self.rrf_fuse([bm25_docs, vector_docs], k=self._rrf_k)
            reranked = await self.rerank(
                query, fused, top_n=max(top_k, 8), candidate_n=self._rerank_top_n,
            )
            final = self.dedup(reranked)[:top_k]
            for rank, doc in enumerate(final, start=1):
                doc["rank"] = rank
            logger.info(
                "[HybridRetriever] search done: query=%r bm25=%d vector=%d "
                "fused=%d final=%d %.0fms",
                query, len(bm25_docs), len(vector_docs), len(fused), len(final),
                (time.perf_counter() - started) * 1000,
            )
            return final
        except Exception:  # noqa: BLE001
            logger.exception("[HybridRetriever] search 链路异常，返回空结果")
            return []

    @staticmethod
    def _unpack_gather(results: list, *names: str) -> "list[list[dict]]":
        unpacked: "list[list[dict]]" = []
        for name, result in zip(names, results):
            if isinstance(result, BaseException):
                # gather(return_exceptions=True) 会把异常当结果返回 —— 在这里收窄成空列表
                logger.warning("[HybridRetriever] %s 召回路异常，降级为空：%r", name, result)
                unpacked.append([])
            elif isinstance(result, list):
                unpacked.append(result)
            else:
                # 防御式：类型不对也降级，不让上游炸
                logger.warning("[HybridRetriever] %s 召回路返回了意外类型 %s",
                               name, type(result))
                unpacked.append([])
        return unpacked

    @staticmethod
    def _normalize_doc(source: dict, score: float) -> dict:
        doc: dict = {field: str(source.get(field) or "") for field in DOC_FIELDS}
        doc["score"] = float(score or 0.0)
        for field in ("status", "price", "stock", "category", "productId", "updatedAt"):
            if source.get(field) is not None:
                doc[field] = source[field]
        return doc

_default_retriever: "HybridRetriever | None" = None

def get_default_retriever() -> HybridRetriever:
    global _default_retriever
    if _default_retriever is not None:
        return _default_retriever

    from app.core.config import get_settings
    from app.core.llm_factory import get_embeddings
    from app.db.elasticsearch import ElasticsearchClient
    from app.services.reranker import HttpReranker

    _default_retriever = HybridRetriever(
        es_client=ElasticsearchClient(),
        embedder=get_embeddings(),
        # 未配 rerank_base_url 时 is_enabled()=False，rerank 自动跳过
        reranker=HttpReranker(),
        settings=get_settings(),
    )
    return _default_retriever

async def search_for_rag(query: str, top_k: int = 5, satoken: str = "") -> "list[dict]":
    retriever = get_default_retriever()
    return await retriever.search(query, top_k=top_k)

