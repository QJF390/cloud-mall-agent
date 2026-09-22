
import asyncio
import logging

import httpx

from app.core.config import get_settings

logger = logging.getLogger(__name__)

# 百炼任务状态 → 本地状态。UNKNOWN/CANCELED 一律按 failed 处理，不能悬着。
_TERMINAL_OK = {"SUCCEEDED"}
_TERMINAL_FAIL = {"FAILED", "CANCELED", "UNKNOWN"}

class ImageProviderError(Exception):
    """生图外部依赖失败（提交失败 / 轮询超时 / 任务失败）。上层据此落 failed 状态。"""

class ImageProvider:
    def __init__(self):
        settings = get_settings()
        self.api_key = settings.effective_llm_api_key
        self.base_url = settings.dashscope_base_url.rstrip("/")
        self.model = settings.image_model
        self.size = settings.image_size
        self.style = settings.image_style
        self.poll_interval = settings.image_poll_interval_seconds
        self.poll_timeout = settings.image_poll_timeout_seconds
        self.client = httpx.AsyncClient(timeout=15.0)

    def _is_mock(self) -> bool:
        return not self.api_key or self.api_key == "your-api-key-here"

    def _headers(self) -> dict:
        return {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json",
            "X-DashScope-Async": "enable",
        }

    async def submit(self, positive_prompt: str, negative_prompt: str) -> str:
        if self._is_mock():
            # 骨架态：返回假 task_id，让整条 pipeline 可在无 key 环境下端到端跑通
            logger.info("[image-provider] mock 模式：跳过真实提交")
            return "mock-dashscope-task"

        body = {
            "model": self.model,
            "input": {
                "prompt": positive_prompt,
                "negative_prompt": negative_prompt,
            },
            "parameters": {
                "size": self.size,
                "n": 1,
                "style": self.style,
            },
        }
        try:
            resp = await self.client.post(
                f"{self.base_url}/services/aigc/text2image/image-synthesis",
                headers=self._headers(),
                json=body,
            )
            resp.raise_for_status()
            payload = resp.json()
            task_id = payload.get("output", {}).get("task_id")
            if not task_id:
                raise ImageProviderError(f"百炼响应缺少 task_id：{payload}")
            return task_id
        except ImageProviderError:
            raise
        except Exception as exc:  # noqa: BLE001
            raise ImageProviderError(f"生图任务提交失败：{exc}") from exc

    async def wait_for_result(self, remote_task_id: str) -> str:
        if self._is_mock():
            # mock 模式不产生真实 URL，pipeline 会检测并改走本地占位图
            return ""

        deadline = asyncio.get_event_loop().time() + self.poll_timeout
        url = f"{self.base_url}/tasks/{remote_task_id}"
        while True:
            if asyncio.get_event_loop().time() > deadline:
                raise ImageProviderError(
                    f"生图轮询超时（>{self.poll_timeout}s），remote_task_id={remote_task_id}"
                )
            try:
                resp = await self.client.get(url, headers=self._headers())
                resp.raise_for_status()
                output = resp.json().get("output", {})
            except Exception as exc:  # noqa: BLE001
                # 单次轮询网络抖动不该终结任务，记日志后继续重试
                logger.warning("[image-provider] 轮询请求失败，将继续重试：%s", exc)
                await asyncio.sleep(self.poll_interval)
                continue

            status = output.get("task_status", "")
            if status in _TERMINAL_OK:
                results = output.get("results") or []
                image_url = (results[0] or {}).get("url") if results else None
                if not image_url:
                    raise ImageProviderError("生图成功但响应中没有图片 URL")
                return image_url
            if status in _TERMINAL_FAIL:
                # code/message 是百炼给出的失败原因，必须透传给上层落库
                code = output.get("code", "UNKNOWN_CODE")
                message = output.get("message", "无失败详情")
                raise ImageProviderError(f"生图任务失败 [{status}/{code}]：{message}")

            await asyncio.sleep(self.poll_interval)

    async def download(self, image_url: str) -> bytes:
        """把百炼的临时 URL（24h 有效）下载到内存，交给存储层落盘。"""
        resp = await self.client.get(image_url)
        resp.raise_for_status()
        return resp.content

    async def close(self):
        await self.client.aclose()

_provider: "ImageProvider | None" = None

def get_image_provider() -> ImageProvider:
    global _provider
    if _provider is None:
        _provider = ImageProvider()
    return _provider
