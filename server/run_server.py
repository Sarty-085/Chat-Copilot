import socket
import uvicorn
from app.config import AppConfig


def get_local_ip() -> str:
    """Detects the primary outbound LAN IP address of this machine."""
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        # Use dummy connect to non-routable address to determine outbound interface
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"


if __name__ == "__main__":
    cfg = AppConfig.load()
    local_ip = get_local_ip()
    print("=" * 65)
    print("🚀 CHAT-COPILOT LOCAL INFERENCE & INGESTION SERVER")
    print("=" * 65)
    print(f"📡 Local LAN IP:      http://{local_ip}:{cfg.port}")
    print(f"📱 In Android App:    Enter '{local_ip}' as the Server IP in Settings")
    print(f"🤖 Ollama Service:    {cfg.ollama_url} (Model: {cfg.local_model})")
    print(f"🔍 Health Check:      http://{local_ip}:{cfg.port}/v1/health")
    print("=" * 65)
    print("Starting server on 0.0.0.0 (all interfaces)... Press Ctrl+C to stop.\n")

    uvicorn.run("app.main:app", host="0.0.0.0", port=cfg.port, reload=False)
