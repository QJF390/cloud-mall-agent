
import asyncio
import json
import logging
import re

from app.chains.prompts import (
    IMAGE_PROMPT_FALLBACK_TEMPLATE,
    IMAGE_STRUCTURE_SYSTEM_PROMPT_V1,
    IMAGE_STRUCTURE_USER_TEMPLATE,
    IMAGE_USAGE_HINTS,
)
from app.core.config import get_settings
from app.core.llm_factory import get_image_struct_llm
from app.services import image_safety
from app.services.image_provider import ImageProviderError, get_image_provider
from app.services.image_storage import make_placeholder_image, save_generated_image
from app.services.image_task_store import get_image_task_store

logger = logging.getLogger(__name__)

_JSON_RE = re.compile(r"\{.*\}", re.DOTALL)

async def run_image_task(task_id: str, description: str, usage_type: str) -> None:
    """入口：由 api 层用 asyncio.create_task 拉起。任何异常都终结为 failed。"""
    store = get_image_task_store()
    settings = get_settings()
    usage_hint = IMAGE_USAGE_HINTS.get(usage_type, IMAGE_USAGE_HINTS["reference"])

    try:
        # ── 1. Prompt 结构化 ─────────────────────────────────────────
        await store.update(task_id, status="processing", message="正在生成提示词…")
        positive, negative = await _structure_prompt(description, usage_hint)

        # ── 2. 内容安全（审结构化后的 prompt，比审原文更能拦谐音绕过）──
        verdict = await image_safety.check_with_llm(positive)
        if verdict.blocked:
            await store.update(
                task_id,
                status="failed",
                message="描述未通过内容安全审核，任务已终止（未产生生图费用）",
                error=f"[{verdict.stage}] {verdict.reason}",
            )
            return

        # ── 3 + 4. 提交百炼 → 轮询 ──────────────────────────────────
        await store.update(task_id, status="processing", message="正在生成图片…")
        provider = get_image_provider()
        remote_task_id = await provider.submit(positive, negative)
        image_url = await provider.wait_for_result(remote_task_id)

        # ── 5. 下载 → 水印 + 元数据 → 落盘 ──────────────────────────
        if image_url:
            raw_bytes = await provider.download(image_url)
        else:
            # mock 模式：无真实 URL，生成占位图保证链路可验证
            logger.info("[image-pipeline] mock 模式：生成占位图 task_id=%s", task_id)
            await asyncio.sleep(2)  # 模拟生图耗时，让前端能看到轮询过程
            raw_bytes = make_placeholder_image(task_id)

        stored_path = save_generated_image(
            image_bytes=raw_bytes,
            task_id=task_id,
            positive_prompt=positive,
            model_name=settings.image_model,
        )

        # ── 6. 出图审核钩子（当前为埋点占位）─────────────────────────
        await image_safety.audit_generated_image(task_id, raw_bytes)

        # ── 7. 成功落状态 ────────────────────────────────────────────
        await store.update(
            task_id,
            status="success",
            message="生成完成。AI 生成内容，仅供参考，请勿用作商品实物主图。",
            image_url=stored_path,
            positive_prompt=positive,
        )
        logger.info("[image-pipeline] 任务成功 task_id=%s -> %s", task_id, stored_path)

    except ImageProviderError as exc:
        logger.warning("[image-pipeline] 外部生图失败 task_id=%s：%s", task_id, exc)
        await store.update(
            task_id, status="failed", message="图片生成失败，请稍后重试", error=str(exc)
        )
    except Exception as exc:  # noqa: BLE001
        logger.exception("[image-pipeline] 任务异常 task_id=%s", task_id)
        await store.update(
            task_id, status="failed", message="服务内部错误，请稍后重试", error=str(exc)
        )

async def _structure_prompt(description: str, usage_hint: str) -> tuple[str, str]:
    fallback = IMAGE_PROMPT_FALLBACK_TEMPLATE.format(
        description=description, usage_hint=usage_hint
    )
    try:
        raw = await get_image_struct_llm().chat(
            [
                {"role": "system", "content": IMAGE_STRUCTURE_SYSTEM_PROMPT_V1},
                {
                    "role": "user",
                    "content": IMAGE_STRUCTURE_USER_TEMPLATE.format(
                        description=description, usage_hint=usage_hint
                    ),
                },
            ],
            response_format={"type": "json_object"},
        )
        match = _JSON_RE.search(raw)
        if not match:
            raise ValueError(f"结构化输出不是 JSON：{raw[:120]}")
        data = json.loads(match.group(0))
        positive = str(data.get("positive_prompt") or "").strip()
        negative = str(data.get("negative_prompt") or "").strip()
        if not positive:
            raise ValueError("结构化输出缺少 positive_prompt")
        logger.info("[image-pipeline] prompt 结构化成功：%s", positive[:80])
        return positive, negative
    except Exception as exc:  # noqa: BLE001
        logger.warning("[image-pipeline] prompt 结构化失败，走兜底模板：%s", exc)
        return fallback, ""
