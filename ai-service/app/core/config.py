from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    """All environment-based configs live here."""

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    # Service
    ai_service_port: int = 8000
    ai_service_host: str = "0.0.0.0"

    # Redis (reuse existing project Redis)
    redis_host: str = "localhost"
    redis_port: int = 6379
    redis_db: int = 0

    # LLM (OpenAI-compatible API, e.g. DashScope / OpenAI / DeepSeek)
    llm_api_key: str = ""
    llm_base_url: str = "https://dashscope.aliyuncs.com/compatible-mode/v1"
    llm_model: str = "qwen-plus"
    # 百炼原生 key：pydantic-settings 会自动读同名环境变量 DASHSCOPE_API_KEY，
    # 用于兜底（IDE / 系统里已经配过 key 的情况），见下方 effective_llm_api_key。
    dashscope_api_key: str = ""

    llm_model_router: str = "qwen-turbo"   # 意图路由 / 查询改写
    llm_model_chat: str = "qwen-plus"      # 最终生成（面向用户，质量优先）
    llm_model_verify: str = "qwen-turbo"   # 答后校验（判定任务，temperature=0）
    llm_timeout_seconds: float = 8.0
    llm_max_tokens: int = 800              # 限制输出长度（越长越容易漂）

    embedding_model: str = "text-embedding-v3"
    embedding_dim: int = 1024

    # 图片生成（走百炼独立的异步接口，不是 /chat/completions）
    image_model: str = "wanx-v1"
    image_size: str = "1024*1024"
    image_style: str = "<auto>"
    # 百炼原生（非 compatible-mode）接口根路径：生图提交/轮询走这里
    dashscope_base_url: str = "https://dashscope.aliyuncs.com/api/v1"
    # 轮询节奏与总超时：生图单张 5~30s，轮询太密浪费、太疏拖慢首图
    image_poll_interval_seconds: float = 2.0
    image_poll_timeout_seconds: float = 120.0
    # 生成图落地目录（相对 ai-service 工作目录），由 main.py 挂静态路由对外暴露
    image_upload_dir: str = "uploads/ai-images"
    # 任务记录在 Redis 的 TTL（临时 URL 有效期 24h，本地文件长期保留，记录过期即可）
    image_task_ttl_seconds: int = 3600

    # Elasticsearch（RAG 在线检索：BM25 + kNN）
    es_base_url: str = "http://localhost:9200"
    es_username: str = ""
    es_password: str = ""
    es_index_chunks: str = "rag_chunks"
    es_timeout_seconds: float = 0.8

    # RAG 在线检索（这些数字都要用 golden set 调，别拍脑袋）
    rag_recall_top_k: int = 50      # 单路召回条数（BM25 一路、向量一路，各 50）
    rag_rerank_top_n: int = 30      # 送进 reranker 的最大条数（cross-encoder 很贵）
    rag_rrf_k: int = 60             # RRF 常数，见 retriever.rrf_fuse
    rag_reject_threshold: float = 0.3   # 门控阈值：按 reranker 分数量纲定（0~1）
    rag_reject_threshold_rrf: float = 0.01
    rag_vector_min_len: int = 4     # query 短于这个字数不走向量（纯向量对短 query 很差）

    # Reranker（cross-encoder，可选）。不配 rerank_base_url 就自动跳过重排。
    rerank_base_url: str = ""
    rerank_model: str = "bge-reranker-v2-m3"
    rerank_timeout_seconds: float = 1.5

    backend_gateway_base_url: str = "http://localhost:8080"
    tool_http_timeout_seconds: float = 1.5
    # 工具总开关：后端没起来 / 联调到别的东西时，一键关掉比每次都等超时干净
    tool_product_enabled: bool = True
    # 分类列表缓存：分类变化频率极低，缓存 10 分钟完全可接受，
    # 好处是"有哪些分类"这种问题不必每次都打一次后端
    tool_category_cache_ttl_seconds: int = 600

    # Safety
    max_history_per_session: int = 20
    sse_timeout_seconds: int = 60

    @property
    def effective_llm_api_key(self) -> str:
        return self.llm_api_key or self.dashscope_api_key

@lru_cache
def get_settings() -> Settings:
    return Settings()
