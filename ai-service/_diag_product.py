
import asyncio
import json

from app.tools.base import ToolContext, clamp_int, err_result, ok_result
from app.tools import product_tools as pt

def show(title: str, obj) -> None:
    print(f"\n=== {title}")
    print(json.dumps(obj, ensure_ascii=False, indent=2)[:1500])

async def main() -> None:
    ctx = ToolContext(satoken="")

    # ---------- ① 纯函数 ----------
    print("=== _map_category 映射")
    for word in ["陶瓷", "陶器", "陶瓷杯", "木作", "黑胡桃", "布艺", "帆布袋",
                 "香薰", "项链", "绘画", "随便一个词"]:
        print(f"  {word:<8} -> {await pt._map_category(word)}")

    print("\n=== clamp_int 钳制（size 上限 20）")
    for value in [None, 5, 9999, "8", "abc", -3]:
        print(f"  {value!r:<8} -> {clamp_int(value, 1, pt.MAX_SEARCH_SIZE, 5)}")

    print("\n=== 统一返回结构（同形状）")
    print("  ok :", list(ok_result([1, 2], count=2).keys()))
    print("  err:", list(err_result("NOT_FOUND", "没有").keys()))

    # ---------- ② / ③ 真实调用 ----------
    for keyword in ["陶瓷", "杯子", "帆布"]:
        result = await pt.search_products(ctx, keyword=keyword, size=5)
        show(f"search_products(keyword={keyword!r})", result)

    show("list_categories()", await pt.list_categories(ctx))
    show("list_categories() 第二次（应命中缓存）", await pt.list_categories(ctx))
    show("get_product_detail(id=1)", await pt.get_product_detail(ctx, 1))
    show("check_stock(id=1)", await pt.check_stock(ctx, 1))
    show("get_product_detail(id=999999) 不存在", await pt.get_product_detail(ctx, 999999))

if __name__ == "__main__":
    asyncio.run(main())
