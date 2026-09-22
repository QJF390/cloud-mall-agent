from typing import Literal

from pydantic import BaseModel, Field

ImageTaskStatus = Literal["pending", "processing", "success", "failed"]

class ImageGenerateRequest(BaseModel):
    description: str = Field(..., min_length=1, max_length=2000)
    usage_type: str = Field(
        default="reference",
        pattern="^(reference|banner|preview)$",
        description="reference=创作灵感图, banner=装饰图, preview=定制预览图",
    )

class ImageGenerateResponse(BaseModel):
    task_id: str
    status: ImageTaskStatus = "pending"
    message: str

class ImageTaskStatusResponse(BaseModel):

    task_id: str
    status: ImageTaskStatus
    message: str = ""
    image_url: str | None = None
    # 结构化后的正向 prompt：回显给用户看，既是透明度也是可解释性
    positive_prompt: str | None = None
    usage_type: str = "reference"
    error: str | None = None
