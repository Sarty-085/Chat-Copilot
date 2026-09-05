import json
import re
from typing import Any, Dict, List, Optional
import httpx


def extract_json_array(raw_text: str) -> List[str]:
    """Extracts a JSON list of strings from model output, handling fences or preamble."""
    cleaned = raw_text.strip()
    # Match markdown code block
    fence_match = re.search(r"```(?:json)?\s*(\[.*?\])\s*```", cleaned, re.DOTALL)
    if fence_match:
        try:
            return json.loads(fence_match.group(1))
        except Exception:
            pass

    # Match raw bracketed array
    bracket_match = re.search(r"(\[.*?\])", cleaned, re.DOTALL)
    if bracket_match:
        try:
            parsed = json.loads(bracket_match.group(1))
            if isinstance(parsed, list):
                return [str(item) for item in parsed]
        except Exception:
            pass

    # Fallback: split by lines
    lines = [l.strip().lstrip("1234567890.-*\"' ") for l in cleaned.splitlines() if l.strip()]
    return lines[:3]


class OllamaClient:
    """Client for local Ollama / llama.cpp server."""

    def __init__(self, base_url: str = "http://localhost:11434", default_model: str = "llama3.2:3b"):
        self.base_url = base_url.rstrip("/")
        self.default_model = default_model

    async def chat_completion(
        self,
        messages: List[Dict[str, str]],
        model: Optional[str] = None,
        temperature: float = 0.7,
        max_tokens: int = 150,
    ) -> str:
        target_model = model or self.default_model
        payload = {
            "model": target_model,
            "messages": messages,
            "stream": False,
            "options": {
                "temperature": temperature,
                "num_predict": max_tokens,
            }
        }
        async with httpx.AsyncClient(timeout=30.0) as client:
            res = await client.post(f"{self.base_url}/api/chat", json=payload)
            res.raise_for_status()
            data = res.json()
            return data.get("message", {}).get("content", "")

    async def generate_suggestions(
        self,
        messages: List[Dict[str, str]],
        model: Optional[str] = None,
    ) -> List[str]:
        raw_output = await self.chat_completion(messages, model=model, temperature=0.75)
        suggestions = extract_json_array(raw_output)
        # Ensure we always return at least 1-3 chips
        if not suggestions:
            suggestions = ["Got it!", "Sounds good", "Will check and let you know"]
        return suggestions[:3]

    async def ping(self) -> Dict[str, Any]:
        """Tests connection to local Ollama service."""
        try:
            async with httpx.AsyncClient(timeout=3.0) as client:
                import time
                t0 = time.time()
                res = await client.get(f"{self.base_url}/api/tags")
                elapsed_ms = (time.time() - t0) * 1000
                if res.status_code == 200:
                    models = [m.get("name") for m in res.json().get("models", [])]
                    return {"status": "ok", "latency_ms": round(elapsed_ms, 1), "models": models}
        except Exception as e:
            return {"status": "error", "error": str(e)}
        return {"status": "error", "error": "Unknown error"}
