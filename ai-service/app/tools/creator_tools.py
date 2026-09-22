
from app.tools.base import ToolContext

async def draft_product_info(
    ctx: ToolContext,
    keywords: str,
    category: str = "",
    tone: str = "",
) -> dict:
    ...

async def suggest_tags(
    ctx: ToolContext,
    description: str,
    name: str = "",
    max_tags: int = 8,
) -> dict:
    ...

async def price_statistics(
    ctx: ToolContext,
    category: str,
    own_price: float | None = None,
) -> dict:
    ...

def build_edit_link(
    ctx: ToolContext,
    product_id: int,
    prefill: dict | None = None,
) -> dict:
    ...
