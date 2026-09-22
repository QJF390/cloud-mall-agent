
from dataclasses import dataclass, field
from typing import Any

@dataclass
class AgentTrace:

    trace_id: str = ""
    request_id: str = ""
    session_id: str = ""
    user_id: int | None = None
    intent: str = ""
    node_sequence: list[str] = field(default_factory=list)
    step_count: int = 0
    retrieved_ids: list[str] = field(default_factory=list)
    retrieval_score: float = 0.0
    reject_reason: str | None = None
    tool_calls: list[dict] = field(default_factory=list)
    llm_calls: list[dict] = field(default_factory=list)
    answer_len: int = 0
    cited: list[str] = field(default_factory=list)
    confidence: float = 0.0
    verified: bool = True
    verify_issues: list[str] = field(default_factory=list)
    error: str | None = None
    latency_ms: dict = field(default_factory=dict)
    first_token_ms: int = 0

    def to_log_line(self) -> str:
        ...

def compute_metrics(traces: list[AgentTrace]) -> dict:
    ...

def load_golden_set(path: str = "ai-service/tests/golden_set.jsonl") -> list[dict]:
    ...

def run_regression(golden_set: list[dict], agent_fn: Any) -> dict:
    ...

def log_event(event: str, trace: AgentTrace, **extra: Any) -> None:
    ...
