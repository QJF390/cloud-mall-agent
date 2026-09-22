from pydantic import BaseModel, Field

class ChatMessage(BaseModel):
    role: str = Field(..., pattern="^(user|assistant|system)$")
    content: str = Field(..., min_length=1, max_length=8000)

class ChatRequest(BaseModel):
    session_id: str = Field(..., min_length=8)
    message: str = Field(..., min_length=1, max_length=4000)
