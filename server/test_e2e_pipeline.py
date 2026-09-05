import asyncio
import sys
from pathlib import Path

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
from app.ingestion.whatsapp_parser import parse_whatsapp_text
from app.ingestion.instagram_parser import parse_instagram_json
from app.ingestion.turn_merger import merge_consecutive_messages, extract_exchange_pairs
from app.memory.style_profiler import build_style_profile_from_turns
from app.memory.storage import LocalStorage
from app.memory.vector_store import VectorRetriever
from app.llm.prompt_builder import build_suggestion_prompt
from app.llm.ollama_client import OllamaClient


async def run_e2e_dry_run():
    print("=" * 60)
    print("CHATPILOT END-TO-END PIPELINE DRY RUN")
    print("=" * 60)

    # 1. Ingest WhatsApp sample
    wa_path = Path("../data/test_whatsapp_chat.txt")
    if not wa_path.exists():
        wa_path = Path("data/test_whatsapp_chat.txt")
    print(f"\n[Step 1] Parsing WhatsApp export: {wa_path}")
    with open(wa_path, "r", encoding="utf-8") as f:
        wa_text = f.read()

    wa_messages = parse_whatsapp_text(wa_text, user_name="You", contact_name="Alex")
    print(f" -> Parsed {len(wa_messages)} individual WhatsApp messages.")

    # 2. Ingest Instagram sample
    ig_path = Path("../data/test_instagram_chat.json")
    if not ig_path.exists():
        ig_path = Path("data/test_instagram_chat.json")
    print(f"\n[Step 2] Parsing Instagram export: {ig_path}")
    with open(ig_path, "r", encoding="utf-8") as f:
        ig_json = f.read()

    ig_messages = parse_instagram_json(ig_json, user_name="You", contact_name="Alex")
    print(f" -> Parsed {len(ig_messages)} individual Instagram messages.")

    # 3. Merge & Turn Concatenation
    all_messages = wa_messages + ig_messages
    turns = merge_consecutive_messages(all_messages)
    print(f"\n[Step 3] Merging consecutive messages into turns:")
    print(f" -> Total unified turns: {len(turns)}")

    # 4. Compute Style Profile
    profile = build_style_profile_from_turns(turns, contact_name="Alex")
    print("\n[Step 4] Computed Contact Style Profile for Alex:")
    print(f" -> Formality Score: {profile.formality_score} (0.0=Casual, 1.0=Formal)")
    print(f" -> Emoji Density: {profile.emoji_density} emojis/turn (Top: {' '.join(profile.top_emojis)})")
    print(f" -> Average reply: ~{profile.avg_words_per_reply} words (~{profile.avg_chars_per_reply} chars)")
    print(f" -> Common phrases: {profile.common_phrases}")
    print(f" -> System prompt snippet:\n{profile.to_system_prompt_snippet()}")

    # 5. Extract Exchange Pairs & Store
    pairs = extract_exchange_pairs(turns)
    print(f"\n[Step 5] Extracted {len(pairs)} historical exchange pairs.")
    for i, p in enumerate(pairs, 1):
        print(f"   [{i}] Incoming: '{p.incoming_text}' --> Reply: '{p.reply_text}'")

    test_db = Path("data/test_e2e.db")
    storage = LocalStorage(db_path=test_db)
    storage.save_profile(profile)

    retriever = VectorRetriever(storage, embed_model="embeddinggemma:latest")
    await retriever.index_pairs(pairs)
    print(" -> Saved profile and exchange pairs to SQLite.")

    # 6. Simulate incoming message & RAG Retrieval
    incoming_query = "yo are you free this weekend?"
    print(f"\n[Step 6] Incoming live message: \"{incoming_query}\"")
    retrieved = await retriever.retrieve_relevant_exchanges("Alex", incoming_query, top_k=3)
    print(f" -> Retrieved {len(retrieved)} most relevant past exchanges:")
    for r in retrieved:
        print(f"   - Match: \"{r['incoming_text']}\" => \"{r['reply_text']}\"")

    # 7. Build LLM Prompt
    active_turns = [
        {"speaker": "Alex", "text": incoming_query, "is_user": False}
    ]
    prompt_messages = build_suggestion_prompt(
        contact_name="Alex",
        platform="whatsapp",
        recent_turns=active_turns,
        style_profile=profile,
        few_shot_exchanges=retrieved,
    )
    print("\n[Step 7] Prompt assembled for model inference.")

    # 8. Inference execution
    print("\n[Step 8] Executing inference via Ollama...")
    client = OllamaClient()
    ping = await client.ping()
    print(f" -> Ollama health check: {ping.get('status')} (Available models: {ping.get('models', [])})")

    # Pick an available model from Ollama
    avail_models = ping.get("models", [])
    chosen_model = "llama3.2:3b"
    if chosen_model not in avail_models and avail_models:
        # Pick another ready model if llama3.2 is still downloading
        for m in avail_models:
            if "embedding" not in m:
                chosen_model = m
                break

    print(f" -> Querying model: {chosen_model}")
    try:
        suggestions = await client.generate_suggestions(prompt_messages, model=chosen_model)
        print(f"\n[SUCCESS] Generated 3 Style-Matched Reply Chips:")
        for idx, chip in enumerate(suggestions, 1):
            print(f"   Chip {idx}: \"{chip}\"")
    except Exception as e:
        import traceback
        traceback.print_exc()
        print(f" -> Inference call failed: {type(e).__name__}: {e}")
        print(" -> Fallback sample chips:")
        print("   Chip 1: \"yeah free, what's up?\"")
        print("   Chip 2: \"nah busy saturday but free sunday!\"")
        print("   Chip 3: \"down, what are we doing? haha\"")

    print("\n" + "=" * 60)
    print("END-TO-END DRY RUN COMPLETE")
    print("=" * 60)


if __name__ == "__main__":
    asyncio.run(run_e2e_dry_run())
