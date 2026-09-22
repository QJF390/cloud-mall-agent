
from app.tools.base import ToolContext

async def get_my_orders(
    ctx: ToolContext,
    limit: int = 5,
    status: str = "",
) -> dict:
    ...

async def get_order_detail(
    ctx: ToolContext,
    order_id: int,
) -> dict:
    ...

async def get_order_items(
    ctx: ToolContext,
    order_ids: list[int],
) -> dict:
    ...

def _require_login(ctx: ToolContext) -> dict | None:
    ...

def _mask_sensitive(order: dict) -> dict:
    ...
