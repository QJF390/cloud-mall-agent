import asyncio, json, re, httpx

# 最后一个是故意的「超纲问题」：用来验证拒答链路 —— 前端要能拿到 reason 并显示提示
QUESTIONS = [
    "七天无理由退货怎么算",     # 知识库：政策（应走 RAG，有引用）
    "满多少包邮",               # 知识库：政策（曾因 COMMERCIAL_HINTS 误拒答，回归用）
    "有没有帆布袋",             # 商品库：有货，应真实召回并给出价格/链接
    "有没有银质戒指",           # 商品库：有货，走品类词「饰品」召回
    "你们店里有陶瓷茶杯吗",     # 商品库：确实没有，应体面说没有（不是「系统故障」）
    "帮我写一首关于春天的诗",   # 超纲：验证拒答链路与前端提示
]

async def ask(q: str) -> None:
    print(f"\n=== Q: {q}")
    got = {"citations": None, "done": None, "text": ""}
    async with httpx.AsyncClient(timeout=90) as c:
        async with c.stream(
            "POST", "http://localhost:8000/ai/chat",
            json={"session_id": "diag-es-0001", "message": q},
        ) as r:
            print("http", r.status_code)
            buf = ""
            async for piece in r.aiter_text():
                buf += piece
                while re.search(r"\r?\n\r?\n", buf):
                    block, buf = re.split(r"\r?\n\r?\n", buf, maxsplit=1)
                    ev, data = "message", ""
                    for line in block.splitlines():
                        if line.startswith("event:"):
                            ev = line[6:].strip()
                        elif line.startswith("data:"):
                            data += line[5:].strip()
                    if not data:
                        continue
                    try:
                        payload = json.loads(data)
                    except Exception:
                        continue
                    if ev == "citations":
                        got["citations"] = payload
                    elif ev == "done":
                        got["done"] = payload
                    elif isinstance(payload.get("chunk"), str):
                        got["text"] += payload["chunk"]

    cit = got["citations"] or {}
    docs = cit.get("docs") or []
    print("citations.docs =", len(docs))
    for d in docs[:3]:
        print("   -", str(d.get("title"))[:40])
    print("done =", json.dumps(got["done"], ensure_ascii=False))
    print("answer =", got["text"][:200].replace("\n", " "))

async def main():
    for q in QUESTIONS:
        try:
            await ask(q)
        except Exception as exc:
            print("ERROR", type(exc).__name__, exc)

asyncio.run(main())
