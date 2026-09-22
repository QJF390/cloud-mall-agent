
import io
import logging
import time
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

from app.core.config import get_settings

logger = logging.getLogger(__name__)

WATERMARK_TEXT = "AI 生成"
PNG_AI_METADATA = {
    "AI Generated": "true",
    "Generation Model": "",   # 运行时填充
    "Generation Task": "",    # 运行时填充 task_id
    "Generation Prompt": "",  # 运行时填充
    "Generated At": "",       # 运行时填充
}

def save_generated_image(
    image_bytes: bytes,
    task_id: str,
    positive_prompt: str,
    model_name: str,
) -> str:
    settings = get_settings()
    out_dir = Path(settings.image_upload_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    img = Image.open(io.BytesIO(image_bytes)).convert("RGBA")
    img = _apply_watermark(img)

    # 统一转 PNG 落盘：只有 PNG 的 tEXt chunk 能无损塞下中文 prompt
    buf = io.BytesIO()
    meta = PngInfoMeta(
        task_id=task_id,
        prompt=positive_prompt,
        model=model_name,
    )
    img.save(buf, format="PNG", pnginfo=meta.build())
    filename = f"{task_id}.png"
    (out_dir / filename).write_bytes(buf.getvalue())

    # 返回相对路径：host 由前端拼接（见 models/image.py 的字段注释）
    return f"/ai/image/files/{filename}"

def make_placeholder_image(task_id: str) -> bytes:
    """
    mock 模式（未配置生图 key）下的占位图：让整条链路无需真实成本即可端到端验证。
    """
    img = Image.new("RGBA", (768, 768), (244, 240, 233, 255))
    draw = ImageDraw.Draw(img)
    draw.rectangle([48, 48, 719, 719], outline=(180, 170, 158, 255), width=2)
    draw.text((80, 340), "AI 生成占位图", fill=(140, 128, 112, 255))
    draw.text((80, 380), f"mock task: {task_id}", fill=(160, 150, 138, 255))
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return buf.getvalue()

class PngInfoMeta:
    """把 AI 标识元数据组装成 PNG 的 tEXt chunk。"""

    def __init__(self, task_id: str, prompt: str, model: str):
        self.task_id = task_id
        self.prompt = prompt
        self.model = model

    def build(self):
        from PIL.PngImagePlugin import PngInfo

        info = PngInfo()
        meta = dict(PNG_AI_METADATA)
        meta["Generation Model"] = self.model
        meta["Generation Task"] = self.task_id
        meta["Generation Prompt"] = self.prompt[:500]
        meta["Generated At"] = str(int(time.time()))
        for key, value in meta.items():
            info.add_text(key, value)
        return info

def _load_font(size: int):
    candidates = [
        "C:/Windows/Fonts/msyh.ttc",
        "C:/Windows/Fonts/simhei.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    ]
    for path in candidates:
        try:
            return ImageFont.truetype(path, size)
        except OSError:
            continue
    return ImageFont.load_default()

def _apply_watermark(img: Image.Image) -> Image.Image:
    """右下角半透明圆角底 + 「AI 生成」白字。in-place 修改并返回原图。"""
    width, height = img.size
    font = _load_font(max(18, width // 24))

    overlay = Image.new("RGBA", img.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(overlay)
    bbox = draw.textbbox((0, 0), WATERMARK_TEXT, font=font)
    text_w, text_h = bbox[2] - bbox[0], bbox[3] - bbox[1]
    pad = max(8, width // 60)
    box_w, box_h = text_w + pad * 2, text_h + pad
    x1, y1 = width - box_w - pad, height - box_h - pad

    draw.rounded_rectangle(
        [x1, y1, x1 + box_w, y1 + box_h],
        radius=6,
        fill=(0, 0, 0, 110),  # 半透明黑底，深浅底色上都可读
    )
    draw.text((x1 + pad, y1 + pad // 2), WATERMARK_TEXT, font=font, fill=(255, 255, 255, 230))
    return Image.alpha_composite(img, overlay)
