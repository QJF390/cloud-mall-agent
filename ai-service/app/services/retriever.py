class VectorRetriever:

    async def search(self, query: str, top_k: int = 3) -> list[str]:
        # TODO: Phase 2 - implement ES multi_match + dense_vector kNN
        #       and reranker on top.
        # For now, return empty so the LLM relies on system prompt + tools.
        return []
