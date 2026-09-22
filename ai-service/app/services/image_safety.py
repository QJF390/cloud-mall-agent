
import json
import logging
import re
from typing import NamedTuple

from app.core.llm_factory import get_router_llm
from app.chains.prompts import IMAGE_SAFETY_SYSTEM_PROMPT, IMAGE_SAFETY_USER_TEMPLATE

logger = logging.getLogger(__name__)

class ModerationResult(NamedTuple):
    blocked: bool
    reason: str = ""
    stage: str = ""  # blocklist / llm / image

_BLOCKLIST_PATTERNS: list[tuple[str, str]] = [
    (r"(色情|淫秽|裸体|nude|porn|naked)", "色情低俗"),
    (r"(暴力|血腥|砍杀|虐杀|gore|beheading|torture|violence|bloody|mutilat)", "暴力血腥"),
    (r"(枪|弹药|爆炸物|武器|assault rifle|grenade|explosive)", "武器"),
    (r"(毒品|冰毒|大麻|cocaine|heroin|methamphetamine)", "毒品"),
    (r"(赌博|赌场|gambling|casino)", "赌博"),
    (r"(邪教|恐怖袭击|terrorist attack|jihad)", "邪教与恐怖主义"),
    (r"(领导人|领袖)\s*(漫画|恶搞|Q版)", "涉政人物形象"),
    (r"(国旗|国徽)\s*(恶搞|侮辱|焚烧)", "涉政符号滥用"),
]

_SAFETY_JSON_RE = re.compile(r"\{.*\}", re.DOTALL)

def check_blocklist(text: str) -> ModerationResult:
    """第一段：本地关键词审核。同步、零成本，任何异常都不该发生在这里。"""
    for pattern, category in _BLOCKLIST_PATTERNS:
        if re.search(pattern, text, re.IGNORECASE):
            return ModerationResult(
                blocked=True, reason=f"描述涉及「{category}」，不予生成", stage="blocklist"
            )
    return ModerationResult(blocked=False)

async def check_with_llm(text: str) -> ModerationResult:
    try:
        raw = await get_router_llm().chat(
            [
                {"role": "system", "content": IMAGE_SAFETY_SYSTEM_PROMPT},
                {"role": "user", "content": IMAGE_SAFETY_USER_TEMPLATE.format(text=text)},
            ],
            response_format={"type": "json_object"},
        )
        match = _SAFETY_JSON_RE.search(raw)
        if not match:
            raise ValueError(f"LLM 审核输出不是 JSON：{raw[:120]}")
        verdict = json.loads(match.group(0))
        if verdict.get("safe") is False:
            return ModerationResult(
                blocked=True,
                reason=str(verdict.get("reason", "描述包含不适宜生成的内容")),
                stage="llm",
            )
        return ModerationResult(blocked=False)
    except Exception as exc:  # noqa: BLE001
        logger.warning("[image-safety] LLM 审核失败，按 blocklist 结果放行：%s", exc)
        return ModerationResult(blocked=False)

async def audit_generated_image(task_id: str, image_bytes: bytes) -> None:
    """
    第三段（占位钩子）：出图后审核。

    前置条件（都还不具备）：
        1. services/llm.py 支持多模态输入（image_url / base64 content）
        2. 或接入独立的图像审核 API
    当前只做埋点，保证接入点稳定 —— pipeline 不用改就能挂真实现。
    """
    logger.info(
        "[image-safety] 出图审核钩子（待接入多模态能力）：task_id=%s, bytes=%d",
        task_id,
        len(image_bytes),
    )
