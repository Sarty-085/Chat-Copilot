from typing import Dict, List, Optional
import httpx
from .ollama_client import extract_json_array


class CloudClient:
    """Unified cloud adapter for Gemini, OpenAI, and Anthropic."""

    def __init__(self, provider: str = "gemini", api_key: Optional[str] = None, model: str = "gemini-2.5-flash"):
        self.provider = provider.lower()
        self.api_key = api_key
        self.model = model

    async def generate_suggestions(self, messages: List[Dict[str, str]]) -> List[str]:
        if not self.api_key:
            raise ValueError(f"Cloud API key for provider '{self.provider}' is not set.")

        if self.provider == "gemini":
            return await self._call_gemini(messages)
        elif self.provider in ["openai", "anthropic"]:
            return await self._call_openai_compatible(messages)
        else:
            raise ValueError(f"Unsupported cloud provider: {self.provider}")

    async def _call_gemini(self, messages: List[Dict[str, str]]) -> List[str]:
        # Google Gemini Generative Language API
        url = f"https://generativelanguage.googleapis.com/v1beta/models/{self.model}:generateContent?key={self.api_key}"
        # Convert standard messages to Gemini contents
        contents = []
        for m in messages:
            role = "user" if m["role"] in ["user", "system"] else "model"
            contents.append({"role": role, "parts": [{"text": m["content"]}]})

        payload = {
            "contents": contents,
            "generationConfig": {
                "temperature": 0.7,
                "responseMimeType": "application/json",
            }
        }
        async with httpx.AsyncClient(timeout=30.0) as client:
            res = await client.post(url, json=payload)
            res.raise_for_status()
            data = res.json()
            candidates = data.get("candidates", [])
            if candidates:
                text = candidates[0].get("content", {}).get("parts", [{}])[0].get("text", "")
                return extract_json_array(text)[:3]
            return ["Sounds good!", "I will get back to you", "Cool, thanks!"]

    async def _call_openai_compatible(self, messages: List[Dict[str, str]]) -> List[str]:
        url = "https://api.openai.com/v1/chat/completions"
        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json",
        }
        payload = {
            "model": self.model or "gpt-4o-mini",
            "messages": messages,
            "temperature": 0.7,
        }
        async with httpx.AsyncClient(timeout=30.0) as client:
            res = await client.post(url, json=payload, headers=headers)
            res.raise_for_status()
            data = res.json()
            text = data["choices"][0]["message"]["content"]
            return extract_json_array(text)[:3]
