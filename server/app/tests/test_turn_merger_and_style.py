from datetime import datetime, timedelta
from app.ingestion.base import UnifiedMessage
from app.ingestion.turn_merger import merge_consecutive_messages, extract_exchange_pairs
from app.memory.style_profiler import build_style_profile_from_turns


def test_consecutive_turn_merging():
    base_time = datetime(2024, 1, 15, 12, 0)
    messages = [
        UnifiedMessage(platform="whatsapp", contact_name="Dave", sender_raw="Dave", is_user=False, text="hey man", timestamp=base_time),
        UnifiedMessage(platform="whatsapp", contact_name="Dave", sender_raw="Dave", is_user=False, text="you free tonight?", timestamp=base_time + timedelta(seconds=10)),
        UnifiedMessage(platform="whatsapp", contact_name="Dave", sender_raw="Dave", is_user=False, text="we're grabbing tacos", timestamp=base_time + timedelta(seconds=20)),
        UnifiedMessage(platform="whatsapp", contact_name="Dave", sender_raw="You", is_user=True, text="yo! yeah i'm down", timestamp=base_time + timedelta(minutes=1)),
        UnifiedMessage(platform="whatsapp", contact_name="Dave", sender_raw="You", is_user=True, text="what time? 🌮", timestamp=base_time + timedelta(minutes=1, seconds=15)),
    ]

    turns = merge_consecutive_messages(messages)
    assert len(turns) == 2
    assert turns[0].is_user is False
    assert turns[0].message_count == 3
    assert "hey man\nyou free tonight?\nwe're grabbing tacos" in turns[0].text

    assert turns[1].is_user is True
    assert turns[1].message_count == 2
    assert "yo! yeah i'm down\nwhat time? 🌮" in turns[1].text

    pairs = extract_exchange_pairs(turns)
    assert len(pairs) == 1
    assert "grabbing tacos" in pairs[0].incoming_text
    assert "what time? 🌮" in pairs[0].reply_text


def test_style_profiler_metrics():
    base_time = datetime(2024, 1, 15, 12, 0)
    messages = [
        UnifiedMessage(platform="whatsapp", contact_name="Sam", sender_raw="Sam", is_user=False, text="how was the test?", timestamp=base_time),
        UnifiedMessage(platform="whatsapp", contact_name="Sam", sender_raw="You", is_user=True, text="nah was totally fine haha 💀", timestamp=base_time + timedelta(minutes=1)),
        UnifiedMessage(platform="whatsapp", contact_name="Sam", sender_raw="Sam", is_user=False, text="did you pass?", timestamp=base_time + timedelta(minutes=2)),
        UnifiedMessage(platform="whatsapp", contact_name="Sam", sender_raw="You", is_user=True, text="yeah passed easily lol", timestamp=base_time + timedelta(minutes=3)),
    ]
    turns = merge_consecutive_messages(messages)
    profile = build_style_profile_from_turns(turns, contact_name="Sam")

    assert profile.contact_name == "Sam"
    assert profile.total_messages_analyzed == 2
    assert profile.emoji_density > 0.0
    assert "💀" in profile.top_emojis
    assert profile.formality_score < 0.5  # Slang + lowercase should yield casual
    snippet = profile.to_system_prompt_snippet()
    assert "Speaking to Sam" in snippet
