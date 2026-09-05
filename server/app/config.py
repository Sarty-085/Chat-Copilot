import json
from pathlib import Path
from typing import Optional
from pydantic import BaseModel

CONFIG_FILE = Path("data/settings.json")


class AppConfig(BaseModel):
    backend_mode: str = "local"  # "local" | "cloud"
    local_model: str = "llama3.2:3b"
    ollama_url: str = "http://localhost:11434"
    embed_model: str = "embeddinggemma:latest"
    cloud_provider: str = "gemini"  # "gemini" | "openai" | "anthropic"
    cloud_api_key: Optional[str] = None
    cloud_model: str = "gemini-2.5-flash"
    max_rag_examples: int = 5
    port: int = 8000

    @classmethod
    def load(cls) -> "AppConfig":
        if CONFIG_FILE.exists():
            try:
                with open(CONFIG_FILE, "r", encoding="utf-8") as f:
                    return cls(**json.load(f))
            except Exception:
                pass
        cfg = cls()
        cfg.save()
        return cfg

    def save(self):
        CONFIG_FILE.parent.mkdir(parents=True, exist_ok=True)
        with open(CONFIG_FILE, "w", encoding="utf-8") as f:
            json.dump(self.model_dump(), f, indent=2)
