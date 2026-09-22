
from app.tools.base import ToolContext

async def search_policy(
    ctx: ToolContext,
    query: str,
    top_k: int = 3,
) -> dict:
    ...

async def search_guide(
    ctx: ToolContext,
    query: str,
    top_k: int = 2,
) -> dict:
    ...

def out_of_scope_reply(topic_hint: str = "") -> dict:
    ...
