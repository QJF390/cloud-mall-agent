from pathlib import Path

import uvicorn
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles

from app.api import chat, image
from app.core.config import get_settings

settings = get_settings()

app = FastAPI(
    title="AI Service",
    description="Handicraft marketplace AI agent service (FastAPI skeleton)",
    version="0.1.0",
)

# CORS: allow web-ui / admin-ui dev servers
# In production, replace with exact origins
app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:5173", "http://localhost:5174"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(chat.router)
app.include_router(image.router)

# 生成图静态托管：目录不存在则先创建（首启时 uploads/ai-images 还没有任何文件）
# 路由挂在 /ai/image/files 下 → 开发环境会被 Vite 的 /ai 代理规则一并转发，
# 生产环境走网关 /ai/** 路由，前端无需改动。
_image_dir = Path(settings.image_upload_dir)
_image_dir.mkdir(parents=True, exist_ok=True)
app.mount("/ai/image/files", StaticFiles(directory=str(_image_dir)), name="ai-images")

@app.get("/health")
async def health():
    return {"status": "ok", "service": "ai-service"}

if __name__ == "__main__":
    uvicorn.run(
        "main:app",
        host=settings.ai_service_host,
        port=settings.ai_service_port,
        reload=True,
    )
