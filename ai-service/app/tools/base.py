
import logging
from dataclasses import dataclass
from typing import Any

logger = logging.getLogger(__name__)

ERR_UPSTREAM_TIMEOUT = "UPSTREAM_TIMEOUT"   # 后端超时
ERR_UPSTREAM_ERROR = "UPSTREAM_ERROR"       # 后端 5xx / 连接失败
ERR_UNAUTHORIZED = "UNAUTHORIZED"           # 未登录 / token 失效
ERR_NOT_FOUND = "NOT_FOUND"                 # 资源不存在（含"已下架"）
ERR_INVALID_PARAM = "INVALID_PARAM"         # 参数不合法
ERR_RATE_LIMITED = "RATE_LIMITED"           # 被限流

@dataclass(frozen=True)
class ToolContext:

    satoken: str = ""
    user_id: int | None = None
    request_id: str = ""
    session_id: str = ""

def ok_result(data: Any, **meta: Any) -> dict:
    # count 自动推导：工具调用方不该每次手工数一遍长度（数漏了就是 bug）
    if isinstance(data, list):
        count = len(data)
    elif data is None:
        count = 0
    else:
        count = 1
    merged: dict[str, Any] = {"count": count, "truncated": False}
    merged.update(meta)
    return {
        "ok": True,
        "data": data,
        "error_code": None,
        "message": None,
        "meta": merged,
    }

def err_result(error_code: str, message: str) -> dict:
    return {
        "ok": False,
        "data": None,
        "error_code": error_code,
        "message": message,
        "meta": {"count": 0, "truncated": False},
    }

async def resolve_user_id_from_token(satoken: str) -> int | None:
    ...

def clamp_int(value: int | None, low: int, high: int, default: int) -> int:
    if value is None:
        return default
    # 模型给的是 JSON，类型不可信：可能是字符串 "5"，也可能是 5.0
    try:
        num = int(value)
    except (TypeError, ValueError):
        logger.warning(
            "[clamp_int] 参数不是数字，回退默认值：value=%r type=%s default=%s",
            value, type(value).__name__, default,
        )
        return default
    if num < low:
        logger.warning("[clamp_int] 参数过小已钳制：%s → %s", num, low)
        return low
    if num > high:
        logger.warning("[clamp_int] 参数过大已钳制：%s → %s", num, high)
        return high
    return num

def ensure_enum(value: str | None, allowed: set[str], default: str | None) -> str | None:
    if value is None:
        return default
    text = str(value).strip()
    if not text:
        return default
    if text in allowed:
        return text
    # info 不用 warning：模型传了个没见过的分类是**常见且可自愈**的
    # （比如用户说"要陶土的"，模型就传 "陶土"），不是系统异常。
    # 但这正是日志该记的东西 —— 积累起来你就知道该往映射表里加哪些词。
    logger.info(
        "[ensure_enum] 值不在白名单，按 default 处理：value=%r allowed=%s",
        text, sorted(allowed),
    )
    return default

def build_tool_registry(ctx: ToolContext) -> list[dict]:
    ...
