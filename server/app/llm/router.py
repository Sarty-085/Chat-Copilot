import time
from typing import Dict, List, Optional
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

from ..config import AppConfig
from ..memory.storage import LocalStorage
from ..memory.vector_store import VectorRetriever
from .prompt_builder import build_suggestion_prompt
from .ollama_client import OllamaClient
from .cloud_client import CloudClient

router = APIRouter()


class SuggestionRequest(BaseModel):
    contact_name: str
    platform: str = "whatsapp"  # "whatsapp" | "instagram"
    recent_messages: List[Dict[str, any]]  # [{"speaker": "Alex", "text": "Are you coming tonight?", "is_user": False}]
    max_suggestions: int = 3


class SuggestionResponse(BaseModel):
    contact_name: str
    suggestions: List[str]
    backend: str
    latency_ms: float
    style_applied: bool


@router.post("/v1/suggestions", response_model=SuggestionResponse)
async def generate_suggestions(req: SuggestionRequest):
    start_time = time.time()
    config = AppConfig.load()
    storage = LocalStorage()
    retriever = VectorRetriever(storage, ollama_url=config.ollama_url, embed_model=config.embed_model)

    # 1. Fetch contact style profile
    profile = storage.get_profile(req.contact_name)

    # 2. Get last incoming message for RAG retrieval
    last_incoming = ""
    for msg in reversed(req.recent_messages):
        if not msg.get("is_user") and msg.get("speaker", "").lower() != "user":
            last_incoming = msg.get("text", "")
            break

    # 3. Retrieve relevant few-shot pairs
    few_shot = []
    if last_incoming:
        few_shot = await retriever.retrieve_relevant_exchanges(
            contact_name=req.contact_name,
            incoming_message=last_incoming,
            top_k=config.max_rag_examples,
        )

    # 4. Construct prompt
    prompt_messages = build_suggestion_prompt(
        contact_name=req.contact_name,
        platform=req.platform,
        recent_turns=req.recent_messages,
        style_profile=profile,
        few_shot_exchanges=few_shot,
    )

    # 5. Dispatch to active backend
    backend_name = config.backend_mode
    suggestions = []

    try:
        if config.backend_mode == "cloud" and config.cloud_api_key:
            client = CloudClient(
                provider=config.cloud_provider,
                api_key=config.cloud_api_key,
                model=config.cloud_model,
            )
            suggestions = await client.generate_suggestions(prompt_messages)
            backend_name = f"cloud ({config.cloud_provider})"
        else:
            client = OllamaClient(
                base_url=config.ollama_url,
                default_model=config.local_model,
            )
            suggestions = await client.generate_suggestions(prompt_messages)
            backend_name = f"local ({config.local_model})"
    except Exception as e:
        # Fallback to local Ollama if cloud failed or vice versa
        if config.backend_mode == "cloud":
            client = OllamaClient(base_url=config.ollama_url, default_model=config.local_model)
            suggestions = await client.generate_suggestions(prompt_messages)
            backend_name = f"fallback-local ({config.local_model})"
        else:
            raise HTTPException(status_code=500, detail=f"Inference failed: {str(e)}")

    elapsed = (time.time() - start_time) * 1000

    return SuggestionResponse(
        contact_name=req.contact_name,
        suggestions=suggestions[: req.max_suggestions],
        backend=backend_name,
        latency_ms=round(elapsed, 1),
        style_applied=profile is not None,
    )


@router.get("/v1/health")
async def health_check():
    config = AppConfig.load()
    client = OllamaClient(base_url=config.ollama_url)
    ollama_status = await client.ping()
    return {
        "status": "healthy",
        "backend_mode": config.backend_mode,
        "local_model": config.local_model,
        "ollama": ollama_status,
    }
