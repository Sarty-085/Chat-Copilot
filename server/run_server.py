import uvicorn
from app.config import AppConfig

if __name__ == "__main__":
    cfg = AppConfig.load()
    print(f"Starting ChatPilot Inference & Ingestion Server on 0.0.0.0:{cfg.port}...")
    print(f"Ollama Target: {cfg.ollama_url} (Default Model: {cfg.local_model})")
    uvicorn.run("app.main:app", host="0.0.0.0", port=cfg.port, reload=False)
