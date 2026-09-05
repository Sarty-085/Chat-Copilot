# Chat-Copilot 💬🤖
> **Personal AI Reply Assistant (Android + Local/Cloud LLM)**
> Learn how you genuinely talk to specific people from your exported chat history and suggest on-brand reply chips in a floating overlay with tap-to-send — **never sending anything autonomously**.

[![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20FastAPI-blue.svg)](https://github.com/Sarty-085/Chat-Copilot)
[![Inference](https://img.shields.io/badge/LLM-Llama--3.2--3B%20%7C%20Ollama-purple.svg)](https://ollama.ai)
[![Privacy](https://img.shields.io/badge/Privacy-100%25%20Local--First-green.svg)](#privacy--terms-of-service-compliance)
[![License](https://img.shields.io/badge/License-MIT-brightgreen.svg)](#license)

---

## 🌟 Overview

**Chat-Copilot** is a privacy-first personal reply assistant for **WhatsApp** and **Instagram DMs**. Instead of expensive GPU fine-tuning (QLoRA/LoRA) that requires high-end hardware, Chat-Copilot uses:

1. **Deterministic Style Profiling:** Derives quantitative metrics (formality score, emoji density, lowercase bias, average word length, common catchphrases) per contact from chat exports.
2. **Local Vector RAG Retrieval:** Embeds past `(Contact Message -> User Reply)` pairs into a local SQLite database using Ollama (`embeddinggemma`).
3. **Contextual Few-Shot Injection:** Injects the style profile and the most relevant historical exchanges alongside active conversation turns into a quantized local model (**Llama 3.2 3B** via Ollama) running at ~30–50 tokens/sec on CPU.
4. **Floating Android Overlay:** Intercepts incoming notifications via `NotificationListenerService`, displays a floating suggestion HUD, and populates the reply field using `RemoteInput` upon explicit tap.

> [!IMPORTANT]
> **No Autonomous Sending:** Chat-Copilot will **never** send a message without your explicit tap. It populates Android's `RemoteInput` pending intent buffer so you review the text and hit send yourself.

---

## 🏗️ Architecture & Hardware Constraints

Chat-Copilot is specifically designed to run efficiently on standard consumer laptops (e.g., **16GB RAM with no dedicated GPU**):

```
┌─────────────────────────────────────────────────────────────┐
│                       ANDROID PHONE                         │
│  (WhatsApp / Instagram Notification)                        │
│                           │                                 │
│             NotificationListenerService                     │
│                           │                                 │
│                           ▼                                 │
│        Floating Bubble Overlay (WindowManager)              │
│       ┌───────────────────────────────────────┐             │
│       │ "Alex Style Match: 98%"               │             │
│       │ [Chip 1] "yeah good to go"            │             │
│       │ [Chip 2] "i'm free what r u planning" │             │
│       │ [Chip 3] "yeah lol free for whatever" │             │
│       └───────────────────────────────────────┘             │
│                           │ Tap Suggestion                  │
│                           ▼                                 │
│          RemoteInput Helper (Populates Input)               │
│                           │ User hits Send manually         │
└───────────────────────────┬─────────────────────────────────┘
                            │ HTTP (Local Wi-Fi / REST)
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                  LAPTOP INFERENCE SERVER                    │
│                        (FastAPI)                            │
│                                                             │
│  ┌──────────────────────┐      ┌─────────────────────────┐  │
│  │   Data Ingestion     │      │   Contact Style Memory  │  │
│  │  WhatsApp .txt Regex │ ───► │   - Formality index     │  │
│  │  Instagram JSON      │      │   - Emoji frequency     │  │
│  │  Turn Concatenation  │      │   - SQLite Vector Pairs │  │
│  └──────────────────────┘      └────────────┬────────────┘  │
│                                             │               │
│                                             ▼               │
│  ┌───────────────────────────────────────────────────────┐  │
│  │              Prompt Assembler & RAG                   │  │
│  │  System Persona + Retrieved Pairs + Live Turns        │  │
│  └──────────────────────────┬────────────────────────────┘  │
│                             │                               │
│              ┌──────────────┴──────────────┐                │
│              ▼                             ▼                │
│     [Local Default]                 [Cloud Fallback]        │
│    Ollama (Llama 3.2 3B)          Gemini / OpenAI / Claude  │
└─────────────────────────────────────────────────────────────┘
```

---

## 📁 Repository Structure

```
Chat-Copilot/
├── server/                                # Python FastAPI Backend
│   ├── app/
│   │   ├── main.py                        # FastAPI endpoints (/v1/suggestions, /api/import/*)
│   │   ├── config.py                      # Persistent configuration (Local/Cloud switcher)
│   │   ├── ingestion/
│   │   │   ├── base.py                    # UnifiedMessage, UnifiedTurn, ExchangePair schemas
│   │   │   ├── whatsapp_parser.py         # WhatsApp export parser (multiline, 12h/24h, system filtering)
│   │   │   ├── instagram_parser.py        # Instagram JSON parser (reactions, shares, mojibake fix)
│   │   │   └── turn_merger.py             # Consecutive turn merger & pair extraction
│   │   ├── memory/
│   │   │   ├── style_profiler.py          # Metric calculations & persona prompt generator
│   │   │   ├── storage.py                 # SQLite storage (contact_profiles, exchange_pairs)
│   │   │   └── vector_store.py            # Cosine similarity retriever with Ollama embeddings
│   │   └── llm/
│   │       ├── prompt_builder.py          # Assembles style rules + RAG examples + context
│   │       ├── ollama_client.py           # Local Ollama client (3-chip extraction)
│   │       ├── cloud_client.py            # Gemini/OpenAI/Claude cloud fallback adapter
│   │       └── router.py                  # API endpoints (/v1/suggestions, /v1/health)
│   ├── requirements.txt
│   ├── run_server.py                      # Starts server on 0.0.0.0:8000
│   ├── run_tests.py                       # Ingestion & style profiler test suite
│   └── test_e2e_pipeline.py               # End-to-end dry run verification script
│
├── android/                               # Native Android Client (Kotlin + Jetpack Compose)
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── AndroidManifest.xml        # NotificationListener & SYSTEM_ALERT_WINDOW permissions
│   │   │   └── java/com/chatpilot/app/
│   │   │       ├── MainActivity.kt        # Bottom-navigation scaffold
│   │   │       ├── data/
│   │   │       │   ├── ApiClient.kt       # OkHttp bridge to laptop server
│   │   │       │   └── RemoteInputHelper.kt # Injects selected suggestion into notification reply
│   │   │       ├── service/
│   │   │       │   ├── ReplyNotificationListenerService.kt # Intercepts WhatsApp/Instagram
│   │   │       │   └── FloatingBubbleService.kt            # Overlay WindowManager service
│   │   │       └── ui/
│   │   │           ├── theme/             # Obsidian Tactile dark-mode design tokens
│   │   │           └── screens/
│   │   │               ├── OnboardingImportScreen.kt # WhatsApp/Instagram file importer
│   │   │               ├── ContactProfileScreen.kt   # Style metrics & editable tone notes
│   │   │               ├── SettingsScreen.kt         # Local/Cloud switcher & ping test
│   │   │               └── PermissionsScreen.kt      # Step-by-step permission setup guide
│   │   └── build.gradle.kts
│   └── settings.gradle.kts
│
├── data/                                  # Sample test datasets for dry runs
│   ├── test_whatsapp_chat.txt             # Sample WhatsApp export conversation
│   └── test_instagram_chat.json           # Sample Instagram export thread
└── docs/
    └── design_dna.md                      # UI Design DNA extracted from Stitch MCP
```

---

## 🚀 Getting Started

### Prerequisites

1. **Python 3.10+**
2. **Ollama** installed on your laptop ([ollama.ai](https://ollama.ai))
3. **Android Studio** (if building the Android APK) or an Android device (Android 8.0 / API 26+)

---

### Step 1: Set Up Local Inference with Ollama

1. Start the Ollama application or service on your laptop.
2. Pull the default 3B conversational model and embedding model:
   ```bash
   ollama pull llama3.2:3b
   ollama pull embeddinggemma:latest
   ```

---

### Step 2: Set Up and Run the Python Backend

1. Navigate to the server folder:
   ```bash
   cd server
   ```

2. Install the required Python dependencies:
   ```bash
   pip install -r requirements.txt
   ```

3. Run the automated parser and style tests:
   ```bash
   python run_tests.py
   ```

4. Run the end-to-end dry run verification:
   ```bash
   python test_e2e_pipeline.py
   ```
   *This parses sample WhatsApp and Instagram conversations, generates Alex's style profile, indexes exchange pairs, and runs a live query through Llama 3.2 3B!*

5. Start the FastAPI server:
   ```bash
   python run_server.py
   ```
   *The server binds to `0.0.0.0:8000`. You can test connectivity by opening `http://localhost:8000/v1/health`.*

---

### Step 3: Export Your Chat History

#### WhatsApp (.txt):
1. Open WhatsApp on your phone.
2. Open a chat with the contact you want Chat-Copilot to learn.
3. Tap **⋮ (Menu)** $\rightarrow$ **More** $\rightarrow$ **Export Chat**.
4. Choose **Without Media**. Save or transfer the `.txt` file to your laptop or phone.

#### Instagram (`messages.json`):
1. Open Instagram $\rightarrow$ **Settings** $\rightarrow$ **Accounts Center** $\rightarrow$ **Your information and permissions** $\rightarrow$ **Download your information**.
2. Select **Messages**, choose **JSON** format, and download.
3. Extract the `messages.json` or `message_1.json` file for the desired thread.

---

### Step 4: Sideload & Configure the Android App

1. Open the `android/` directory in Android Studio.
2. Connect your Android phone (e.g. Vivo Y56 5G or any device with Android 8.0+) via USB or Wi-Fi Debugging.
3. Build and install the app (`Run 'app'`).
4. On your phone, open **ChatPilot**:
   - **Setup Tab:**
     - Tap **Enable Access** for **Notification Access** (allows reading WhatsApp/Instagram notification actions).
     - Tap **Allow Overlay** for **Display Over Other Apps** (allows rendering the floating suggestion bubble).
   - **Settings Tab:**
     - Enter your laptop's local Wi-Fi IP address (e.g. `192.168.1.X`, port `8000`).
     - Tap **Test Connection (Ping)**. You should see `✓ Connected! Model: llama3.2:3b`.
   - **Import Tab:**
     - Select your exported WhatsApp `.txt` or Instagram `.json` file to ingest turns and compute the contact's style DNA.
   - **Profile Tab:**
     - View and fine-tune computed formality, emoji density, and custom tone notes (e.g. "Keep replies under 6 words, banter allowed").

---

## 💡 How It Works in Action

1. A friend (e.g. "Alex") sends you a message: *"yo are you free this weekend?"*
2. `ReplyNotificationListenerService` intercepts the notification and extracts:
   - Sender name: `Alex`
   - Platform: `whatsapp`
   - Message text: `"yo are you free this weekend?"`
   - The notification's quick-reply `RemoteInput` and `PendingIntent`.
3. The Android client calls your laptop server (`/v1/suggestions`).
4. The server:
   - Pulls Alex's computed style profile (*casual, lowercase bias, emoji density 0.71/turn*).
   - Retrieves the top 5 semantically similar historical exchanges with Alex from SQLite.
   - Queries **Llama 3.2 3B** on your laptop.
5. In ~1.5–2.5 seconds, a floating tactile bubble appears above your chat with 3 tailored chips:
   - Chip 1: `"yeah good to go"`
   - Chip 2: `"i'm free what r u planning"`
   - Chip 3: `"yeah lol free for whatever"`
6. Tap any chip $\rightarrow$ `RemoteInputHelper` automatically populates the text into the messaging app's reply input.
7. **You review the text and hit send yourself.**

---

## 📡 API Reference

### `POST /v1/suggestions`
Generate 3 style-mimicking suggestions for an incoming message.

**Request:**
```json
{
  "contact_name": "Alex",
  "platform": "whatsapp",
  "recent_messages": [
    { "speaker": "Alex", "text": "yo are you free this weekend?", "is_user": false }
  ],
  "max_suggestions": 3
}
```

**Response:**
```json
{
  "contact_name": "Alex",
  "suggestions": [
    "yeah good to go",
    "i'm free what r u planning",
    "yeah lol free for whatever"
  ],
  "backend": "local (llama3.2:3b)",
  "latency_ms": 2180.4,
  "style_applied": true
}
```

### `GET /v1/health`
Check connectivity and status of the local Ollama instance.

---

## 🔒 Privacy & Terms of Service Compliance

- **Zero Cloud Leaks by Default:** Chat history parsing, vector embeddings, and LLM inference remain entirely on your local laptop.
- **No Autonomous Sending:** Prevents accidental replies, hallucinations, or bot-like behavior. Every reply requires explicit human intent.
- **No Unofficial APIs:** Chat-Copilot relies entirely on Android's standard public OS APIs (`NotificationListenerService` and `RemoteInput`). It does **not** use reverse-engineered WhatsApp/Meta web APIs or headless WhatsApp instances, ensuring 0% account ban risk.
- **Personal Sideloading Only:** Because Google Play heavily restricts apps requesting both `NotificationListenerService` and `SYSTEM_ALERT_WINDOW`, this project is designed for personal sideloading.

---

## 📄 License

MIT License. Designed for personal use.
