"""一次性验证脚本 v2：对话 SSE（兼容 \r\n 行尾），验证完即删。"""
import json
import re
import urllib.request

req = urllib.request.Request(
    "http://localhost:8000/ai/chat",
    method="POST",
    data=json.dumps({"session_id": "diag_sess_2", "message": "你好，用一句话介绍你自己"}).encode(),
    headers={"Content-Type": "application/json"},
)
chunks = 0
text = ""
raw_all = b""
with urllib.request.urlopen(req, timeout=90) as resp:
    while True:
        raw = resp.read(1024)
        if not raw:
            break
        raw_all += raw

print("http body head:", raw_all[:300])
for block in re.split(rb"\r?\n\r?\n", raw_all):
    for line in block.decode("utf-8", "replace").splitlines():
        if line.startswith("data:"):
            try:
                payload = json.loads(line[5:].lstrip())
                if isinstance(payload.get("chunk"), str) and payload["chunk"]:
                    chunks += 1
                    text += payload["chunk"]
            except Exception:
                pass

print(f"chunks={chunks}")
print("reply:", text[:300])
