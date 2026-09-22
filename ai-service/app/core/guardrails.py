
from dataclasses import dataclass, field
from typing import Any

@dataclass
class VerificationResult:

    passed: bool = True
    issues: list[str] = field(default_factory=list)
    facts: list[dict] = field(default_factory=list)
    severity: str = "ok"

def extract_facts(answer: str) -> list[dict]:
    ...

def verify_grounding(
    answer: str,
    context: str,
    tool_results: list[dict] | None = None,
) -> VerificationResult:
    ...

def build_rewrite_prompt(
    answer: str,
    issues: list[str],
    context: str,
    tool_results: list[dict] | None = None,
) -> str:
    ...

def wrap_untrusted(text: str, source: str = "资料") -> str:
    ...

def sanitize_output(answer: str) -> str:
    ...

def safe_fallback(reason: str) -> str:
    ...
