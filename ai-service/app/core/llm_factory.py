
from functools import lru_cache
from typing import Any

from app.core.config import get_settings
from app.services.embeddings import EmbeddingsProvider
from app.services.llm import LLMProvider

def get_router_llm() -> Any:
    return _router_llm_singleton()

@lru_cache(maxsize=None)
def _router_llm_singleton() -> LLMProvider:
    """进程内单例：复用同一个 httpx 连接池，避免每次调用重建 TCP 连接。"""
    settings = get_settings()
    return LLMProvider(
        model=settings.llm_model_router,
        temperature=0.0,
        max_tokens=128,    # 分类只需要一个标签 + 置信度
        timeout=3.0,       # 路由是入口，不能拖
    )

def get_chat_llm(streaming: bool = True) -> Any:
    # streaming 只是给调用方的语义提示：同一个 Provider 两种能力都有
    # （stream_chat / chat）。保留这个参数是为了让上层代码能自解释。
    return _chat_llm_singleton()

@lru_cache(maxsize=None)
def _chat_llm_singleton() -> LLMProvider:
    settings = get_settings()
    return LLMProvider(
        model=settings.llm_model_chat,
        temperature=0.3,                    # 导购要的是稳定，不是创造力
        max_tokens=settings.llm_max_tokens,  # 限制长度：输出越长越容易漂
        timeout=settings.llm_timeout_seconds,
    )

def get_verify_llm() -> Any:
    return _verify_llm_singleton()

@lru_cache(maxsize=None)
def _verify_llm_singleton() -> LLMProvider:
    settings = get_settings()
    return LLMProvider(
        model=settings.llm_model_verify,
        temperature=0.0,   # 校验是判定任务，必须可复现
        max_tokens=256,    # 只要输出"是否忠实 + 哪句有问题"
        timeout=settings.llm_timeout_seconds,
    )

def get_image_struct_llm() -> Any:
    return _image_struct_llm_singleton()

@lru_cache(maxsize=None)
def _image_struct_llm_singleton() -> LLMProvider:
    settings = get_settings()
    return LLMProvider(
        model=settings.llm_model_router,  # 复用 turbo 量级：省钱，且改写任务不需要大模型
        temperature=0.3,
        max_tokens=512,
        timeout=6.0,
    )

def get_embeddings() -> Any:
    return _embeddings_singleton()

@lru_cache(maxsize=None)
def _embeddings_singleton() -> EmbeddingsProvider:
    settings = get_settings()
    return EmbeddingsProvider(
        model=settings.embedding_model,
        dimensions=settings.embedding_dim,
    )

def get_model_info() -> dict:
    settings = get_settings()
    return {
        "router": settings.llm_model_router,
        "chat": settings.llm_model_chat,
        "verify": settings.llm_model_verify,
        "embedding": settings.embedding_model,
        "embedding_dim": settings.embedding_dim,
        "image": settings.image_model,
        "base_url": settings.llm_base_url,
        "timeout_seconds": settings.llm_timeout_seconds,
        "api_key_configured": bool(settings.effective_llm_api_key),
    }
