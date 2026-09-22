
import logging
import time
from typing import Any

import httpx

from app.core.config import get_settings
from app.tools.base import (
    ERR_INVALID_PARAM,
    ERR_NOT_FOUND,
    ERR_UNAUTHORIZED,
    ERR_UPSTREAM_ERROR,
    ERR_UPSTREAM_TIMEOUT,
    ToolContext,
    clamp_int,
    err_result,
    ok_result,
)

logger = logging.getLogger(__name__)

# 后端分类枚举（与 sql/schema.sql 里 t_product.category 的注释一致）
VALID_CATEGORIES = frozenset({"陶瓷", "绘画", "香薰", "饰品", "布艺", "木作", "其他"})

CATEGORY_SYNONYMS: dict[str, str] = {
    # 陶瓷
    "陶瓷": "陶瓷", "陶器": "陶瓷", "陶土": "陶瓷", "粗陶": "陶瓷", "瓷器": "陶瓷",
    "茶杯": "陶瓷", "咖啡杯": "陶瓷", "杯子": "陶瓷", "马克杯": "陶瓷",
    "碗": "陶瓷", "盘子": "陶瓷", "花瓶": "陶瓷",
    # 木作
    "木作": "木作", "木艺": "木作", "木工": "木作", "木头": "木作", "实木": "木作",
    "黑胡桃": "木作", "胡桃木": "木作", "木": "木作",
    "布艺": "布艺", "帆布": "布艺", "亚麻": "布艺", "棉麻": "布艺", "布": "布艺",
    "编织": "布艺", "刺绣": "布艺", "植物染": "布艺", "扎染": "布艺", "帆布袋": "布艺",
    # 绘画
    "绘画": "绘画", "油画": "绘画", "水彩": "绘画", "插画": "绘画", "素描": "绘画",
    "画": "绘画", "画作": "绘画",
    # 香薰
    "香薰": "香薰", "香氛": "香薰", "蜡烛": "香薰", "精油": "香薰", "熏香": "香薰",
    # 饰品
    "饰品": "饰品", "首饰": "饰品", "项链": "饰品", "耳环": "饰品", "耳钉": "饰品",
    "手链": "饰品", "戒指": "饰品", "胸针": "饰品",
}

# 模型可能给 size=10000，钳到这里（后端 MAX_PAGE_SIZE 也是有限制的）
MAX_SEARCH_SIZE = 20
# story 可能很长：AI 要的是"能讲清卖点"，不是全文（全文由前端展示）
MAX_STORY_CHARS = 200
# 一句话卖点的长度（进 context 的每条商品都要尽量短，否则 5 条就吃掉上千 token）
MAX_SELLING_POINT_CHARS = 60
# 前端商品详情路由：web-ui/src/router → /product/:id
PRODUCT_URL_PREFIX = "/product/"

# 分类缓存（list_categories 专用）：分类变化频率极低，缓存 10 分钟完全可接受
_CATEGORY_CACHE: dict[str, Any] = {"at": 0.0, "data": []}

async def search_products(
    ctx: ToolContext,
    keyword: str = "",
    category: str = "",
    price_min: float | None = None,
    price_max: float | None = None,
    page: int = 1,
    size: int = 5,
) -> dict:
    settings = get_settings()
    if not settings.tool_product_enabled:
        logger.info("[search_products] 商品工具已关闭（tool_product_enabled=False）")
        return ok_result([], count=0)

    size = clamp_int(size, 1, MAX_SEARCH_SIZE, 5)
    page = clamp_int(page, 1, 50, 1)
    kw = str(keyword or "").strip()[:60]

    cat = await _map_category(category)

    # ② 主搜索
    params: dict[str, Any] = {"keyword": kw, "page": page, "size": size}
    if cat:
        params["category"] = cat
    resp = await _call_product_api(ctx, "GET", "/product/search", params=params)
    if not resp.get("ok"):
        return resp
    products = resp.get("data") or []

    if cat and not products and kw:
        logger.info(
            "[search_products] 分类=%s 无结果，降级为不带分类重搜：keyword=%r", cat, kw
        )
        retry = await _call_product_api(
            ctx, "GET", "/product/search",
            params={"keyword": kw, "page": page, "size": size},
        )
        if retry.get("ok"):
            products = retry.get("data") or []

    items: list[dict] = []
    for raw in products:
        if not isinstance(raw, dict):
            continue
        if _as_int(raw.get("status")) != 1:
            logger.info(
                "[search_products] 过滤非上架商品：id=%s status=%s",
                raw.get("id"), raw.get("status"),
            )
            continue
        price = _as_float(raw.get("price"))
        if price is not None:
            if price_min is not None and price < float(price_min):
                continue
            if price_max is not None and price > float(price_max):
                continue
        items.append(_slim_product(raw))

    # ⑥ truncated 必须告诉模型：否则它拿到 5 条就说"只有 5 件符合"，
    #    而实际还有更多 —— 这是典型的**工具设计导致的幻觉**。
    return ok_result(items, count=len(items), truncated=len(products) >= size)

async def get_product_detail(
    ctx: ToolContext,
    product_id: int,
) -> dict:
    if not get_settings().tool_product_enabled:
        return err_result(ERR_UPSTREAM_ERROR, "商品服务暂未开启")

    pid = _as_int(product_id)
    if pid is None:
        return err_result(ERR_INVALID_PARAM, "商品 id 不合法")

    resp = await _call_product_api(ctx, "GET", f"/product/get/{pid}")
    if not resp.get("ok"):
        return resp

    raw = resp.get("data")
    if not isinstance(raw, dict) or not raw:
        return err_result(ERR_NOT_FOUND, "这个作品可能已下架或不存在")
    if _as_int(raw.get("status")) != 1:
        logger.info("[get_product_detail] 商品非上架，按 NOT_FOUND 处理：id=%s", pid)
        return err_result(ERR_NOT_FOUND, "这个作品可能已下架或不存在")

    return ok_result(_slim_product(raw, with_story=True), count=1)

async def check_stock(
    ctx: ToolContext,
    product_id: int,
) -> dict:
    if not get_settings().tool_product_enabled:
        return err_result(ERR_UPSTREAM_ERROR, "商品服务暂未开启")

    pid = _as_int(product_id)
    if pid is None:
        return err_result(ERR_INVALID_PARAM, "商品 id 不合法")

    resp = await _call_product_api(ctx, "GET", f"/product/get/{pid}")
    if not resp.get("ok"):
        return resp

    raw = resp.get("data")
    if not isinstance(raw, dict) or not raw:
        return err_result(ERR_NOT_FOUND, "这个作品可能已下架或不存在")

    stock = _as_int(raw.get("stock")) or 0
    return ok_result(
        {"product_id": pid, "stock": stock, "in_stock": stock > 0}, count=1
    )

async def list_categories(ctx: ToolContext) -> dict:
    settings = get_settings()
    if not settings.tool_product_enabled:
        return ok_result([], count=0)

    now = time.time()
    cached = _CATEGORY_CACHE.get("data") or []
    if cached and now - float(_CATEGORY_CACHE.get("at") or 0.0) < settings.tool_category_cache_ttl_seconds:
        logger.info("[list_categories] 命中缓存：%s", cached)
        return ok_result(list(cached), count=len(cached), cached=True)

    resp = await _call_product_api(ctx, "GET", "/product/categories")
    if not resp.get("ok"):
        return resp

    cats = [c.strip() for c in (resp.get("data") or []) if isinstance(c, str) and c.strip()]
    _CATEGORY_CACHE["at"] = now
    _CATEGORY_CACHE["data"] = cats
    return ok_result(cats, count=len(cats), cached=False)

async def _call_product_api(
    ctx: ToolContext,
    method: str,
    path: str,
    **kwargs,
) -> dict:
    settings = get_settings()
    timeout = float(settings.tool_http_timeout_seconds)
    url = f"{settings.backend_gateway_base_url.rstrip('/')}{path}"

    headers: dict[str, str] = {"Accept": "application/json"}
    # ① 统一透传 satoken：漏一个地方就是 401，而且这种 bug 只在"某些工具"上出现，极难归因
    if ctx.satoken:
        headers["satoken"] = ctx.satoken

    started = time.perf_counter()
    try:
        async with httpx.AsyncClient(timeout=timeout) as client:
            response = await client.request(method, url, headers=headers, **kwargs)
    except httpx.TimeoutException:
        cost = int((time.perf_counter() - started) * 1000)
        logger.warning("[product_api] 超时 %sms：%s %s", cost, method, path)
        return err_result(ERR_UPSTREAM_TIMEOUT, "商品服务暂时没有响应")
    except httpx.HTTPError as exc:
        logger.warning("[product_api] 连接失败：%s %s err=%s", method, path, exc)
        return err_result(ERR_UPSTREAM_ERROR, "商品服务暂时不可用")
    except Exception:  # noqa: BLE001
        logger.exception("[product_api] 未预期异常：%s %s", method, path)
        return err_result(ERR_UPSTREAM_ERROR, "商品服务暂时不可用")

    cost = int((time.perf_counter() - started) * 1000)
    status = response.status_code

    # ② 鉴权：401/403 单独成类，让模型说"请先登录"而不是"服务不可用"
    if status in (401, 403):
        logger.info("[product_api] 未授权：%s %s status=%s", method, path, status)
        return err_result(ERR_UNAUTHORIZED, "需要先登录才能查看")
    if status == 404:
        return err_result(ERR_NOT_FOUND, "没有找到相关内容")
    if status >= 500:
        logger.warning("[product_api] 后端 5xx：%s %s status=%s", method, path, status)
        return err_result(ERR_UPSTREAM_ERROR, "商品服务暂时不可用")
    if status >= 400:
        logger.warning("[product_api] 后端 4xx：%s %s status=%s", method, path, status)
        return err_result(ERR_INVALID_PARAM, "请求没有被受理")

    try:
        body = response.json()
    except Exception:  # noqa: BLE001
        logger.warning("[product_api] 后端返回非 JSON：%s %s", method, path)
        return err_result(ERR_UPSTREAM_ERROR, "商品服务返回了异常内容")

    if not isinstance(body, dict):
        return err_result(ERR_UPSTREAM_ERROR, "商品服务返回了异常内容")
    code = _as_int(body.get("code")) or 0
    if code != 200:
        logger.warning(
            "[product_api] 后端业务失败：%s %s code=%s message=%r",
            method, path, code, body.get("message"),
        )
        if code in (401, 403):
            return err_result(ERR_UNAUTHORIZED, "需要先登录才能查看")
        if code == 404:
            return err_result(ERR_NOT_FOUND, "没有找到相关内容")
        return err_result(ERR_UPSTREAM_ERROR, "商品服务暂时不可用")

    logger.info("[product_api] ok %sms：%s %s", cost, method, path)
    return ok_result(body.get("data"))

async def _map_category(user_input: str) -> str | None:
    text = str(user_input or "").strip()
    if not text:
        return None

    # ① 本身就是合法枚举（模型直接给了 "陶瓷"）
    if text in VALID_CATEGORIES:
        return text
    # ② 同义词表精确命中
    hit = CATEGORY_SYNONYMS.get(text)
    if hit:
        return hit
    for word, cat in CATEGORY_SYNONYMS.items():
        if word in text:
            logger.info("[map_category] 包含匹配：%r → %s（命中词 %r）", text, cat, word)
            return cat

    logger.info("[map_category] 未命中映射表，按不过滤处理：%r", text)
    return None

def _as_int(value: Any) -> int | None:
    if value is None or isinstance(value, bool):
        return None
    try:
        return int(value)
    except (TypeError, ValueError):
        return None

def _as_float(value: Any) -> float | None:
    """转 float；失败返回 None。"""
    if value is None or isinstance(value, bool):
        return None
    try:
        return float(value)
    except (TypeError, ValueError):
        return None

def _text(value: Any) -> str:
    """转 str 并去空白；None → ''。"""
    return "" if value is None else str(value).strip()

def _clip(text: str, limit: int) -> str:
    """截断并留标记 —— 否则模型以为这就是完整原文，会照着半句话编。"""
    if not text:
        return ""
    if len(text) <= limit:
        return text
    return text[:limit] + "…"

def _slim_product(raw: dict, *, with_story: bool = False) -> dict:
    pid = _as_int(raw.get("id"))
    stock = _as_int(raw.get("stock")) or 0
    item: dict[str, Any] = {
        "id": pid,
        "name": _text(raw.get("name")),
        "price": _as_float(raw.get("price")),
        "stock": stock,
        "in_stock": stock > 0,
        "category": _text(raw.get("category")),
        "coverImage": _text(raw.get("coverImage")),
        # url 让用户可以点进去核对 —— 这是"降低幻觉伤害"最有效的产品设计
        "url": f"{PRODUCT_URL_PREFIX}{pid}" if pid is not None else "",
        "selling_point": _clip(
            _text(raw.get("description")) or _text(raw.get("story")),
            MAX_SELLING_POINT_CHARS,
        ),
    }
    if with_story:
        # story 可能很长 → 截断。AI 要的是"能讲清卖点"，全文由前端展示。
        item["story"] = _clip(_text(raw.get("story")), MAX_STORY_CHARS)
        item["tags"] = _text(raw.get("tags"))
    return item
