import math
import httpx
from typing import List, Optional
from ..ingestion.base import ExchangePair
from .storage import LocalStorage


def cosine_similarity(vec_a: List[float], vec_b: List[float]) -> float:
    """Computes cosine similarity between two vectors."""
    dot = sum(a * b for a, b in zip(vec_a, vec_b))
    norm_a = math.sqrt(sum(a * a for a in vec_a))
    norm_b = math.sqrt(sum(b * b for b in vec_b))
    if norm_a == 0 or norm_b == 0:
        return 0.0
    return dot / (norm_a * norm_b)


class VectorRetriever:
    """
    Manages vector embeddings and top-k retrieval of historical exchange pairs.
    Primary embedding engine: local Ollama (`embeddinggemma:latest`).
    Fallback: token Jaccard / lexical similarity when offline.
    """

    def __init__(self, storage: LocalStorage, ollama_url: str = "http://localhost:11434", embed_model: str = "embeddinggemma:latest"):
        self.storage = storage
        self.ollama_url = ollama_url
        self.embed_model = embed_model

    async def get_embedding(self, text: str) -> Optional[List[float]]:
        """Requests embedding vector from local Ollama service."""
        try:
            async with httpx.AsyncClient(timeout=10.0) as client:
                res = await client.post(
                    f"{self.ollama_url}/api/embeddings",
                    json={"model": self.embed_model, "prompt": text},
                )
                if res.status_code == 200:
                    return res.json().get("embedding")
        except Exception:
            pass
        return None

    async def index_pairs(self, pairs: List[ExchangePair]):
        """Generates embeddings for incoming messages and saves to SQLite."""
        embeddings = []
        for pair in pairs:
            emb = await self.get_embedding(pair.incoming_text)
            embeddings.append(emb)
        self.storage.save_exchange_pairs(pairs, embeddings)

    async def retrieve_relevant_exchanges(
        self,
        contact_name: str,
        incoming_message: str,
        top_k: int = 5,
    ) -> List[dict]:
        """
        Retrieves top_k most relevant past exchanges for this contact.
        Combines vector similarity and lexical matching.
        """
        stored = self.storage.get_exchange_pairs(contact_name)
        if not stored:
            return []

        query_emb = await self.get_embedding(incoming_message)
        scored_pairs = []

        query_words = set(incoming_message.lower().split())

        for item in stored:
            score = 0.0
            item_emb = item.get("embedding")
            if query_emb and item_emb:
                score = cosine_similarity(query_emb, item_emb)
            else:
                # Lexical Jaccard fallback
                target_words = set(item["incoming_text"].lower().split())
                intersection = query_words.intersection(target_words)
                union = query_words.union(target_words)
                score = len(intersection) / len(union) if union else 0.0

            scored_pairs.append((score, item))

        scored_pairs.sort(key=lambda x: x[0], reverse=True)
        return [pair for _, pair in scored_pairs[:top_k]]
