from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from typing import List, Optional
from pydantic import BaseModel

from .config import AppConfig
from .ingestion.whatsapp_parser import parse_whatsapp_text
from .ingestion.instagram_parser import parse_instagram_json
from .ingestion.turn_merger import merge_consecutive_messages, extract_exchange_pairs
from .memory.style_profiler import ContactStyleProfile, build_style_profile_from_turns
from .memory.storage import LocalStorage
from .memory.vector_store import VectorRetriever
from .llm.router import router as llm_router

app = FastAPI(title="ChatPilot Local Inference & Ingestion Server", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(llm_router)


class SettingsUpdate(BaseModel):
    backend_mode: Optional[str] = None
    local_model: Optional[str] = None
    ollama_url: Optional[str] = None
    cloud_provider: Optional[str] = None
    cloud_api_key: Optional[str] = None
    cloud_model: Optional[str] = None


@app.get("/api/settings")
def get_settings():
    cfg = AppConfig.load()
    return cfg.model_dump()


@app.post("/api/settings")
def update_settings(update: SettingsUpdate):
    cfg = AppConfig.load()
    data = update.model_dump(exclude_unset=True)
    for k, v in data.items():
        setattr(cfg, k, v)
    cfg.save()
    return {"status": "updated", "settings": cfg.model_dump()}


@app.post("/api/import/whatsapp")
async def import_whatsapp(
    file: UploadFile = File(...),
    user_name: Optional[str] = Form(None),
    contact_name: Optional[str] = Form(None),
    date_order_hint: Optional[str] = Form("DMY"),
):
    """Parses an exported WhatsApp .txt chat, computes the style profile, and indexes pairs."""
    content = await file.read()
    text = content.decode("utf-8", errors="replace")

    messages = parse_whatsapp_text(
        text,
        user_name=user_name,
        contact_name=contact_name,
        date_order_hint=date_order_hint,
    )
    if not messages:
        raise HTTPException(status_code=400, detail="No valid messages parsed from file.")

    detected_contact = contact_name or messages[0].contact_name
    turns = merge_consecutive_messages(messages)
    profile = build_style_profile_from_turns(turns, contact_name=detected_contact)
    pairs = extract_exchange_pairs(turns)

    # Save to storage
    cfg = AppConfig.load()
    storage = LocalStorage()
    storage.save_profile(profile)
    # Fast batch save all exchange pairs to SQLite immediately
    storage.save_exchange_pairs(pairs, embeddings=None)

    # Background non-blocking vector embedding for recent pairs
    retriever = VectorRetriever(storage, ollama_url=cfg.ollama_url, embed_model=cfg.embed_model)
    import asyncio
    asyncio.create_task(retriever.index_pairs_background(pairs[:50]))

    return {
        "status": "success",
        "contact_name": detected_contact,
        "platform": "whatsapp",
        "total_messages": len(messages),
        "total_turns": len(turns),
        "indexed_exchange_pairs": len(pairs),
        "style_profile": profile.model_dump(),
    }


@app.post("/api/import/instagram")
async def import_instagram(
    file: UploadFile = File(...),
    user_name: Optional[str] = Form(None),
    contact_name: Optional[str] = Form(None),
):
    """Parses an exported Instagram messages.json thread, computes the style profile, and indexes pairs."""
    content = await file.read()
    raw_json = content.decode("utf-8", errors="replace")

    messages = parse_instagram_json(
        raw_json,
        user_name=user_name,
        contact_name=contact_name,
    )
    if not messages:
        raise HTTPException(status_code=400, detail="No valid messages parsed from Instagram JSON.")

    detected_contact = contact_name or messages[0].contact_name
    turns = merge_consecutive_messages(messages)
    profile = build_style_profile_from_turns(turns, contact_name=detected_contact)
    pairs = extract_exchange_pairs(turns)

    cfg = AppConfig.load()
    storage = LocalStorage()
    storage.save_profile(profile)
    storage.save_exchange_pairs(pairs, embeddings=None)

    retriever = VectorRetriever(storage, ollama_url=cfg.ollama_url, embed_model=cfg.embed_model)
    import asyncio
    asyncio.create_task(retriever.index_pairs_background(pairs[:50]))

    return {
        "status": "success",
        "contact_name": detected_contact,
        "platform": "instagram",
        "total_messages": len(messages),
        "total_turns": len(turns),
        "indexed_exchange_pairs": len(pairs),
        "style_profile": profile.model_dump(),
    }


@app.get("/api/profiles")
def list_profiles():
    storage = LocalStorage()
    return storage.list_profiles()


@app.get("/api/profiles/{contact_name}")
def get_profile(contact_name: str):
    storage = LocalStorage()
    p = storage.get_profile(contact_name)
    if not p:
        raise HTTPException(status_code=404, detail=f"No profile found for contact '{contact_name}'.")
    return p


@app.put("/api/profiles/{contact_name}")
def update_profile(contact_name: str, profile: ContactStyleProfile):
    storage = LocalStorage()
    profile.contact_name = contact_name
    storage.save_profile(profile)
    return {"status": "saved", "profile": profile}
