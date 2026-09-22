import asyncio
import uuid

from fastapi import APIRouter, Header, HTTPException

from app.models.image import (
    ImageGenerateRequest,
    ImageGenerateResponse,
    ImageTaskStatusResponse,
)
from app.services import image_safety
from app.services.image_pipeline import run_image_task
from app.services.image_task_store import get_image_task_store

router = APIRouter(prefix="/ai", tags=["ai-image"])

@router.post("/image/generate", response_model=ImageGenerateResponse)
async def generate_image(
    req: ImageGenerateRequest,
    satoken: str = Header(default="", alias="satoken"),
):
    # ── 内容安全第一段闸门：本地 blocklist，零成本同步拦截。
    #    违规描述在这里终结 → 零生图调用（省成本 + 违规内容不进模型链路）。
    verdict = image_safety.check_blocklist(req.description)
    if verdict.blocked:
        raise HTTPException(status_code=422, detail=verdict.reason)

    task_id = f"img_{uuid.uuid4().hex[:16]}"
    store = get_image_task_store()
    await store.create(task_id, description=req.description, usage_type=req.usage_type)

    # 后台执行完整 pipeline；create_task 的异常已在 pipeline 内部兜底终结为 failed
    asyncio.create_task(run_image_task(task_id, req.description, req.usage_type))

    return ImageGenerateResponse(
        task_id=task_id,
        status="pending",
        message="任务已受理，请使用 task_id 轮询 /ai/image/result/{task_id} 获取结果。",
    )

@router.get("/image/result/{task_id}", response_model=ImageTaskStatusResponse)
async def get_image_result(
    task_id: str,
    satoken: str = Header(default="", alias="satoken"),
):
    """轮询端点：前端每 2s 调一次，拿到 success/failed 即可停止。"""
    task = await get_image_task_store().get(task_id)
    if task is None:
        # 两种可能：task_id 不存在 / 已过期（TTL 1h）。对外统一 404，不区分。
        raise HTTPException(status_code=404, detail="任务不存在或已过期")
    return ImageTaskStatusResponse(
        task_id=task_id,
        status=task.get("status", "pending"),
        message=task.get("message", ""),
        image_url=task.get("image_url"),
        positive_prompt=task.get("positive_prompt"),
        usage_type=task.get("usage_type", "reference"),
        error=task.get("error"),
    )
