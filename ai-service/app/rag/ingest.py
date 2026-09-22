
import argparse
import asyncio
import hashlib
import json
import logging
import pathlib
import re
import time
from collections import Counter
from typing import Any

logger = logging.getLogger(__name__)

# 每个 chunk 必须有的字段（缺一个就会在检索侧静默降级）
REQUIRED_FIELDS = ("docId", "content", "title", "source", "url")

ALLOWED_SOURCES = ("product", "policy", "faq", "guide")

CHUNK_MAX_LEN = 400
CHUNK_OVERLAP = 60

# 批量参数
EMBED_BATCH_SIZE = 10
UPSERT_BATCH_SIZE = 200    # ES bulk 单批条数

SENSITIVE_PATTERNS = (
    "手机号", "身份证", "银行卡", "密码", "token", "openid", "收货地址",
)

PENDING_MARKER = "🟡 待确认"

# 语料根目录（ai-service/rag_data）。用相对本文件的路径而不是 cwd ——
# 脚本从任何目录启动都能跑对
RAG_DATA_DIR = str(pathlib.Path(__file__).resolve().parents[2] / "rag_data")

def iter_markdown_files(root: str) -> "list[Any]":
    root_path = pathlib.Path(root)

    if not root_path.exists():
        logger.error("[ingest] 语料根目录不存在：%s", root)
        return []

    paths = sorted(root_path.rglob("*.md"))

    before = len(paths)
    paths = [p for p in paths if p.name.lower() != "readme.md"]
    if len(paths) < before:
        logger.info("[ingest] 排除 %d 个 README.md（非语料）", before - len(paths))

    logger.info("[ingest] 扫描到 %d 个 md 文件", len(paths))
    return paths

def parse_front_matter(text: str) -> "tuple[dict, str]":
    if not text.startswith("---"):
        return {}, text
    parts = text.split("---", 2)
    if len(parts) < 3:
        logger.warning("[ingest] front-matter 未闭合（只找到 1 个 ---）")
        return {}, text.strip()
    front_matter, body = parts[1], parts[2]

    meta = {}
    for line in front_matter.strip().splitlines():
        line = line.strip()
        if not line or ":" not in line:
            continue
        key, value = line.split(":", 1)
        meta[key.strip()] = value.strip().strip("\"'")

    return meta, body.strip()

def load_markdown_corpus(root: str) -> "list[dict]":
    corpus = []
    doc_paths: "dict[str, list]" = {}

    for path in iter_markdown_files(root):
        try:
            text = path.read_text(encoding="utf-8")
            meta, body = parse_front_matter(text)
        except Exception as e:
            logger.warning("[ingest] 读取/解析失败，跳过 %s：%s", path, e)
            continue

        docId = meta.get("docId", "").strip()
        source = meta.get("source", "").strip()

        if not docId or not source:
            logger.warning("[ingest] 缺少 docId/source，跳过 %s（meta=%s）", path, meta)
            continue

        if source not in ALLOWED_SOURCES:
            logger.warning("[ingest] source 非法(%s)，跳过 %s；允许值=%s",
                           source, path, ALLOWED_SOURCES)
            continue

        corpus.append({
            "docId": docId,
            "source": source,
            "title": meta.get("title", ""),
            "url": meta.get("url", ""),
            "updatedAt": meta.get("updatedAt", ""),
            "version": meta.get("version", ""),
            "body": body,
            "path": str(path),
        })
        doc_paths.setdefault(docId, []).append(path)

    dups = {k: v for k, v in doc_paths.items() if len(v) > 1}
    for docId, paths in dups.items():
        logger.error("[ingest] docId 冲突：%s ← %s",
                     docId, " / ".join(str(p) for p in paths))
    if dups:
        # docId 冲突 → chunk _id 撞车 → 后一篇覆盖前一篇 → 丢文档，必须拦住
        raise ValueError(f"[ingest] {len(dups)} 个 docId 冲突，详见上方 ERROR 日志")

    logger.info("[ingest] 加载 %d 篇文档", len(corpus))
    return corpus

async def load_products_from_es(es: Any, index: str = "products") -> "list[dict]":
    body = {
        "query": {"match_all": {}},
        "size": 2000,
        "_source": ["id", "name", "category", "tags", "story", "description",
                    "price", "stock", "status", "updatedAt"],
    }
    try:
        response = await es.search(index, body)
    except Exception as exc:  # noqa: BLE001
        logger.warning("[ingest] 读取商品索引 %s 失败，商品语料跳过：%s", index, exc)
        return []
    from app.db.elasticsearch import extract_hits
    products = []
    for hit in extract_hits(response):
        source = hit.get("_source", {})
        source.setdefault("id", hit.get("_id"))
        products.append(source)
    logger.info("[ingest] 从 ES 读取 %d 个商品", len(products))
    return products

def clean_markdown(text: str) -> str:
    # 处理顺序：先删块级噪声，再处理标记符号，最后收空白
    # ① HTML 注释（含多行）—— 纯注释，进向量只会稀释语义
    text = re.sub(r"<!--.*?-->", "", text, flags=re.DOTALL)
    # ② 图片 ![alt](src) 整个删掉（向量模型看不懂图片链接）
    text = re.sub(r"!\[[^\]]*\]\([^)]*\)", "", text)
    pending_count = text.count(PENDING_MARKER)
    if pending_count:
        text = re.sub(
            rf"^\s*>\s*{re.escape(PENDING_MARKER)}.*$",
            "（该条规则以平台最新公告为准）",
            text,
            flags=re.MULTILINE,
        )
        logger.info("[ingest] 处理了 %d 处 %s 标记", pending_count, PENDING_MARKER)
    # ④ 粗体/斜体：去符号留文字（"**7 天**"的强调是给人看的，"7 天"是关键信息）
    text = re.sub(r"(\*\*|__)(.*?)\1", r"\2", text)
    # ⑤ 行内代码反引号：同理去符号留内容
    text = text.replace("`", "")
    # ⑥ 连续空行收敛 + 首尾空白（不 strip 会让 chunk 开头一堆换行，拉低 embedding 质量）
    text = re.sub(r"\n{3,}", "\n\n", text)
    text = text.strip()
    return text

# body 为 markdown 格式的字符串，level 为标题的级别，默认为 2
def split_by_heading(body: str, level: int = 2) -> "list[tuple[str, str]]":
    pattern = re.compile(rf"^#{{{level}}} (?!\#)(.+)$")
    sections: "list[tuple[str, str]]" = []
    current_heading = ""      # 第一个标题之前的引言段，heading 先留空
    current_lines: "list[str]" = []

    def _flush() -> None:
        section_text = "\n".join(current_lines).strip()
        if section_text:
            sections.append((current_heading.strip(), section_text))

    for line in body.splitlines():
        matched = pattern.match(line)
        if matched:
            _flush()
            current_heading = matched.group(1).strip()
            current_lines = []
        else:
            current_lines.append(line)
    _flush()

    if not sections and body.strip():
        return [("", body.strip())]

    return sections

def chunk_text(
    text: str,
    max_len: int = CHUNK_MAX_LEN,
    overlap: int = CHUNK_OVERLAP,
) -> "list[str]":
    text = text.strip()
    if len(text) <= max_len:
        return [text] if text else []

    sentences = re.findall(r"[^。！？!?\n]+[。！？!?\n]?", text)

    chunks: "list[str]" = []
    current: "list[str]" = []
    current_len = 0

    def _flush_current() -> None:
        nonlocal current, current_len
        piece = "".join(current).strip()
        if piece:
            chunks.append(piece)

    for sentence in sentences:
        while len(sentence) > max_len:
            _flush_current()
            chunks.append(sentence[:max_len])
            sentence = sentence[max_len:]
        if current and current_len + len(sentence) > max_len:
            _flush_current()
            tail: "list[str]" = []
            tail_len = 0
            for s in reversed(current):
                if tail_len + len(s) > overlap:
                    break
                tail.insert(0, s)
                tail_len += len(s)
            current = list(tail)
            current_len = tail_len
        current.append(sentence)
        current_len += len(sentence)

    _flush_current()
    return chunks

def enrich_with_context(chunk: dict, parent_title: str, section_title: str = "") -> dict:
    prefix = parent_title if not section_title else f"{parent_title} · {section_title}"
    new_chunk = dict(chunk)
    content = new_chunk.get("content", "")
    if not content.startswith("【"):     # 幂等：重复调用不会叠前缀
        new_chunk["content"] = f"【{prefix}】\n{content}"
    new_chunk["title"] = prefix
    return new_chunk

def split_markdown_doc(doc: dict) -> "list[dict]":
    cleaned = clean_markdown(doc["body"])
    sections = split_by_heading(cleaned, level=2)
    if not sections:
        logger.warning("[ingest] 文档切出 0 个 chunk：%s", doc.get("path"))
        return []

    chunks: "list[dict]" = []
    for index, (section_title, section_text) in enumerate(sections):
        # 超长小节二次切分（正常 `##` 小节直接就是一个 chunk）
        pieces = (chunk_text(section_text)
                  if len(section_text) > CHUNK_MAX_LEN else [section_text])
        for piece_index, piece in enumerate(pieces):
            enriched = enrich_with_context(
                {"content": piece},
                parent_title=doc["title"],
                section_title=(section_title
                               if len(pieces) == 1 else f"{section_title}（{piece_index + 1}）"),
            )
            suffix = f"#{index}" if len(pieces) == 1 else f"#{index}-{piece_index}"
            chunks.append({
                "docId": f"{doc['docId']}{suffix}",
                "content": enriched["content"],
                "title": enriched["title"],
                "source": doc["source"],
                "url": f"{doc['url']}#{index}",
                "updatedAt": doc.get("updatedAt", ""),
                "version": doc.get("version", ""),
            })
            if is_sensitive(chunks[-1]["content"]):
                logger.warning("[ingest] 疑似敏感内容 docId=%s path=%s",
                               chunks[-1]["docId"], doc.get("path"))
    return chunks

def build_product_chunks(products: "list[dict]") -> "list[dict]":
    chunks: "list[dict]" = []
    skipped = 0
    for product in products:
        if int(product.get("status") or 0) != 1:
            skipped += 1
            continue
        product_id = product.get("id", product.get("productId"))
        parts = [f"【{product.get('name', '')}】"]
        if product.get("category"):
            parts.append(f"分类：{product['category']}")
        if product.get("tags"):
            tags = product["tags"] if isinstance(product["tags"], str) else "、".join(product["tags"])
            parts.append(f"标签：{tags}")
        story = (product.get("story") or product.get("description") or "").strip()
        if story:
            parts.append(story[:300])
        chunks.append({
            "docId": f"product_{product_id}",
            "content": " ｜ ".join(parts),
            "title": product.get("name", ""),
            "source": "product",
            "url": f"/product/{product_id}",
            "productId": product_id,
            "category": product.get("category", ""),
            "status": product.get("status", 1),
            "price": product.get("price"),
            "stock": product.get("stock"),
            "updatedAt": product.get("updatedAt", ""),
        })
    logger.info("[ingest] 商品 chunk %d 条，过滤未上架 %d 条", len(chunks), skipped)
    return chunks

def is_sensitive(text: str) -> bool:
    # 定位：只告警不拦截。误判（正常文档含"密码"）→ 语料缺失 → 幻觉；
    # 漏判 → 敏感数据进无权限向量库 → 越权。真正的防线在入库前的数据筛选，
    # 这里是最后一道"事后可发现"的网
    for pattern in SENSITIVE_PATTERNS:
        if pattern in text:
            return True
    if re.search(r"1[3-9]\d{9}", text):
        return True
    return False

def build_embed_text(chunk: dict) -> str:
    embed_text = chunk.get("embed_text") or chunk.get("content", "")
    if not embed_text:
        return ""
    if len(embed_text) > 2000:
        logger.warning("[ingest] embed_text 超长被截断 docId=%s len=%d",
                       chunk.get("docId", "?"), len(embed_text))
        embed_text = embed_text[:2000]
    return embed_text

def content_hash(text: str) -> str:
    from app.core.config import get_settings
    model = get_settings().embedding_model
    return hashlib.md5(f"{model}:{text}".encode("utf-8")).hexdigest()

async def embed_chunks(
    chunks: "list[dict]",
    batch_size: int = EMBED_BATCH_SIZE,
    use_cache: bool = True,
) -> "list[dict]":
    # 局部 import：ingest 是离线脚本，不该在 import 阶段把 config/httpx 全拉起来
    from app.core.config import get_settings
    from app.core.llm_factory import get_embeddings

    settings = get_settings()
    embedder = get_embeddings()
    started = time.perf_counter()

    # Q6 缓存：进程内 dict（生产版应换 Redis：多实例共享 + TTL —— 先跑通再说）
    cache: dict = getattr(embed_chunks, "_cache", None) or {}
    embed_chunks._cache = cache  # noqa: SLF001

    # "索引错位"的解法：保留 (chunk, text) 配对再过滤空串，回填时一一对应
    pairs = [(c, build_embed_text(c)) for c in chunks]
    pairs = [(c, t) for c, t in pairs if t]
    pending = [(c, t) for c, t in pairs if content_hash(t) not in cache]
    cached_hits = len(pairs) - len(pending)

    failed: "list[str]" = []
    call_count = 0
    batch_size = min(batch_size, 10)
    for i in range(0, len(pending), batch_size):
        batch = pending[i : i + batch_size]
        texts = [t for _, t in batch]
        try:
            call_count += 1
            vectors = await embedder.embed_documents(texts)
        except Exception as exc:  # noqa: BLE001
            logger.error("[ingest] embedding 批次失败（%d 条，docId=%s...）：%s",
                         len(batch), batch[0][0].get("docId", "?"), exc)
            failed.extend(c.get("docId", "?") for c, _ in batch)
            continue
        if len(vectors) != len(texts):
            raise RuntimeError(
                f"embedding 返回条数({len(vectors)}) != 输入条数({len(texts)})")
        for (chunk, text), vector in zip(batch, vectors):
            if len(vector) != settings.embedding_dim:
                raise RuntimeError(
                    f"embedding 维度({len(vector)}) != config.embedding_dim"
                    f"({settings.embedding_dim})，先核对模型实际输出再建 mapping"
                )
            chunk["embedding"] = vector
            chunk["embedding_model"] = settings.embedding_model
            cache[content_hash(text)] = vector

    elapsed = time.perf_counter() - started
    logger.info(
        "[ingest] embedding 完成：总 %d 条 | 缓存命中 %d | 实际调用 %d 批 | "
        "失败 %d | 耗时 %.1fs",
        len(pairs), cached_hits, call_count, len(failed), elapsed,
    )
    if failed:
        logger.warning("[ingest] 以下 chunk 没算出向量（可重跑补齐）：%s", failed[:20])
    return chunks

def build_mapping(dims: int) -> dict:
    text_field = {"type": "text", "analyzer": "ik_smart"}

    return {
        "settings": {"number_of_shards": 1, "number_of_replicas": 0},
        "mappings": {
            "properties": {
                # docId：keyword —— RRF 归并 key / 去重 / upsert 幂等 _id，绝不分词
                "docId": {"type": "keyword"},
                # content：text —— BM25 倒排 + 拼 prompt 的正文
                "content": text_field,
                # title：text —— BM25 加权字段（title^3）
                "title": text_field,
                # source：keyword —— 召回路径选择 / 域不匹配判定（term 过滤）
                "source": {"type": "keyword"},
                # url：keyword —— 只做展示和跳转，不参与检索，分词毫无意义
                "url": {"type": "keyword"},
                "embedding": {
                    "type": "dense_vector",
                    "dims": dims,
                    "index": True,
                    "similarity": "cosine",
                },
                # 换模型后靠它发现新旧向量混用（"有结果但语义全错"的根源）
                "embedding_model": {"type": "keyword"},
                # price/stock：数值型 —— 存成 text 就没法做 range 过滤了
                "price": {"type": "float"},
                "stock": {"type": "integer"},
                "status": {"type": "integer"},
                "category": {"type": "keyword"},
                "productId": {"type": "keyword"},
                "updatedAt": {"type": "date"},
            }
        },
    }

async def ensure_index(es: Any, index: str, dims: int, recreate: bool = False) -> None:
    exists = await es.index_exists(index)
    if exists and recreate:
        logger.warning("[ingest] --recreate：删除索引 %s 并重建（数据将全部丢失）", index)
        await es.delete_index(index)
        exists = False
    if not exists:
        await es.create_index(index, build_mapping(dims))
        logger.info("[ingest] 已创建索引 %s（dims=%d）", index, dims)
        return
    mapping = await es.get_mapping(index)
    try:
        actual_dims = mapping[index]["mappings"]["properties"]["embedding"]["dims"]
        if actual_dims != dims:
            raise RuntimeError(
                f"索引 {index} 的 embedding dims={actual_dims}，与期望 {dims} 不一致。"
                "ES 不支持改字段类型，请用 --recreate 重建索引"
            )
    except KeyError:
        logger.warning("[ingest] 无法从 mapping 读取 embedding.dims，跳过一致性检查")

async def bulk_upsert(es: Any, index: str, chunks: "list[dict]") -> "tuple[int, int]":
    valid: "list[dict]" = []
    for chunk in chunks:
        absent = [field for field in REQUIRED_FIELDS if not chunk.get(field)]
        if absent:
            logger.error("[ingest] chunk 缺必填字段 %s，跳过写入：docId=%s",
                         absent, chunk.get("docId", "?"))
            continue
        valid.append(chunk)

    success = failed = 0
    for i in range(0, len(valid), UPSERT_BATCH_SIZE):
        batch = valid[i : i + UPSERT_BATCH_SIZE]
        lines: "list[str]" = []
        for chunk in batch:
            action = {"index": {"_index": index, "_id": chunk["docId"]}}
            lines.append(json.dumps(action, ensure_ascii=False))
            # 写入字段白名单：按 mapping 来，别把内部字段漏出去
            doc = {key: chunk[key] for key in (
                "docId", "content", "title", "source", "url", "updatedAt",
                "embedding", "embedding_model", "price", "stock",
                "status", "category", "productId",
            ) if key in chunk}
            lines.append(json.dumps(doc, ensure_ascii=False))
        ndjson = "\n".join(lines) + "\n"
        response = await es.bulk(index, ndjson)
        for item in response.get("items", []):
            result = item.get("index", {})
            if 200 <= int(result.get("status") or 500) < 300:
                success += 1
            else:
                failed += 1
                logger.error("[ingest] 写入失败 docId=%s error=%s",
                             result.get("_id"), result.get("error"))
    logger.info("[ingest] bulk_upsert：成功 %d，失败 %d（待写入 %d 条）",
                success, failed, len(valid))
    return success, failed

GOLDEN_SET: "list[tuple[str, str]]" = [
    ("几天发货", "48 小时"),
    ("定制商品可以退吗", "定制商品"),
    ("什么时候打款给卖家", "确认收货"),
    ("七天无理由怎么算", "签收"),
    ("质量问题退换货运费谁承担", "卖家承担"),
    ("满多少包邮", "99 元"),
]

async def verify_ingest(
    sample_queries: "list[str]",
    expected: "list[str]",
    top_k: int = 5,
) -> dict:
    from app.core.config import get_settings
    from app.db.elasticsearch import ElasticsearchClient, extract_hits

    settings = get_settings()
    es = ElasticsearchClient(timeout=5.0)
    def _norm(value: str) -> str:
        return re.sub(r"[\s，。：；、！？,.;:!?\-()（）【】《》\"']", "", str(value))

    details: "list[dict]" = []
    hit_count = 0
    try:
        for query, keyword in zip(sample_queries, expected):
            body = {
                "query": {"multi_match": {"query": query, "fields": ["title^3", "content"]}},
                "size": top_k,
                "_source": ["docId", "title", "content", "source", "url"],
            }
            response = await es.search(settings.es_index_chunks, body)
            hits = extract_hits(response)
            hit_rank = 0
            hit_title = ""
            for rank, hit in enumerate(hits, start=1):   # rank 从 1，与 RRF 保持一致
                source = hit.get("_source", {})
                haystack = _norm(source.get("content", "")) + _norm(source.get("title", ""))
                if _norm(keyword) in haystack:
                    hit_rank = rank
                    hit_title = source.get("title", "")
                    break
            hit_count += 1 if hit_rank else 0
            details.append({"query": query, "expected": keyword,
                            "hit": bool(hit_rank), "rank": hit_rank, "title": hit_title})
    finally:
        await es.close()

    recall = hit_count / len(sample_queries) if sample_queries else 0.0
    for detail in details:
        # 未命中的单独标出 —— 10 条里 8 条命中，要一眼看到那 2 条，否则等于没打印
        mark = "OK" if detail["hit"] else "未命中 → 排查顺序：①语料有没有 ②切分碎没碎 ③向量路 ④BM25/分词器"
        logger.info("[verify] query=%r 期望[%s] rank=%s title=%r %s",
                    detail["query"], detail["expected"],
                    detail["rank"] or "-", detail["title"], mark)
    logger.info("[verify] Recall@%d = %.2f（达标线 0.6，不达标别往下写 Agent）", top_k, recall)
    return {"recall@5": recall, "details": details}

async def check_idempotent(es: Any, index: str) -> "tuple[int, int]":
    total = await es.count(index)
    body = {"size": 0, "aggs": {"dup_doc_ids": {"terms": {"field": "docId", "size": 10000}}}}
    response = await es.search(index, body)
    buckets = response.get("aggregations", {}).get("dup_doc_ids", {}).get("buckets", [])
    dup = sum(1 for bucket in buckets if bucket.get("doc_count", 0) > 1)
    missing_body = {"query": {"bool": {"must_not": {"exists": {"field": "embedding"}}}},
                    "size": 0, "track_total_hits": True}
    missing_resp = await es.search(index, missing_body)
    missing = int(missing_resp.get("hits", {}).get("total", {}).get("value", 0))
    logger.info("[ingest] 幂等自检：count=%d 重复docId=%d 缺embedding=%d", total, dup, missing)
    if dup > 0:
        logger.error("[ingest] 发现 %d 个重复 docId！docId 生成逻辑不稳定", dup)
    return total, dup

async def run_pipeline(
    stage: str = "all",
    dry_run: bool = False,
    recreate: bool = False,
) -> None:
    from app.core.config import get_settings
    from app.db.elasticsearch import ElasticsearchClient

    settings = get_settings()
    index = settings.es_index_chunks
    # 离线脚本超时放宽到 30s（在线检索的 0.8s 是问答链路约束，这里不适用）
    es = ElasticsearchClient(timeout=30.0)
    pipeline_started = time.perf_counter()
    try:
        corpus = load_markdown_corpus(RAG_DATA_DIR)
        if stage == "load":
            logger.info("[ingest] --stage load：共 %d 篇文档", len(corpus))
            return

        chunks: "list[dict]" = []
        for doc in corpus:
            chunks.extend(split_markdown_doc(doc))
        # 商品路：拉不到就跳过（policy 语料照样入库）
        try:
            products = await load_products_from_es(es)
            chunks.extend(build_product_chunks(products))
        except Exception as exc:  # noqa: BLE001
            logger.warning("[ingest] 商品语料加载失败，跳过（不影响 policy）：%s", exc)

        source_dist = Counter(c["source"] for c in chunks)
        logger.info("[ingest] 切分完成：总 chunk %d，分布 %s，耗时 %.1fs",
                    len(chunks), dict(source_dist), time.perf_counter() - pipeline_started)

        if stage == "split" or dry_run:
            for chunk in chunks[:3]:
                logger.info("[dry-run] 样例 chunk：%s",
                            json.dumps(chunk, ensure_ascii=False, default=str)[:600])
            return

        await embed_chunks(chunks)

        await ensure_index(es, index, settings.embedding_dim, recreate=recreate)
        await bulk_upsert(es, index, chunks)

        await check_idempotent(es, index)
        result = await verify_ingest(
            [q for q, _ in GOLDEN_SET], [k for _, k in GOLDEN_SET],
        )
        recall = float(result.get("recall@5", 0.0))
        logger.info("[ingest] 全流程完成，总耗时 %.1fs", time.perf_counter() - pipeline_started)
        if recall < 0.6:
            logger.error("[ingest] Recall@5=%.2f < 0.6，以非 0 退出（CI 可拦截）", recall)
            raise SystemExit(1)
    finally:
        await es.close()

def main() -> None:
    parser = argparse.ArgumentParser(description="RAG 语料入库流水线")
    parser.add_argument(
        "--stage", default="all", choices=["load", "split", "embed", "upsert", "all"],
        help="只跑某一阶段（调切分策略时用 split，省钱省时）",
    )
    parser.add_argument("--dry-run", action="store_true",
                        help="只切分不写库，先看切出来什么样")
    parser.add_argument("--recreate", action="store_true",
                        help="删除并重建索引（数据全丢，慎用）")
    args = parser.parse_args()

    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
    )
    asyncio.run(run_pipeline(stage=args.stage, dry_run=args.dry_run, recreate=args.recreate))

if __name__ == "__main__":
    main()

