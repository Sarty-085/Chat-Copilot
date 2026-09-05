from app.ingestion.whatsapp_parser import parse_whatsapp_text
from app.ingestion.turn_merger import merge_consecutive_messages, extract_exchange_pairs
from app.memory.style_profiler import build_style_profile_from_turns


def test_whatsapp_parsing_bracketed_and_multiline():
    sample = """[12/03/24, 10:15:00] Sarah: Hey! Are we still meeting for coffee today?
I found this really cool new cafe downtown.
[12/03/24, 10:16:30] You: Yeah totally! What time works for you?
[12/03/24, 10:17:00] Sarah: Around 3pm?
[12/03/24, 10:17:45] You: perfect see ya then ☕
"""
    messages = parse_whatsapp_text(sample, user_name="You", contact_name="Sarah")
    assert len(messages) == 4
    # Check multi-line message
    assert "cool new cafe downtown" in messages[0].text
    assert messages[0].is_user is False
    assert messages[0].contact_name == "Sarah"

    # Check user reply
    assert messages[1].is_user is True
    assert "totally" in messages[1].text
    assert messages[3].is_user is True
    assert "☕" in messages[3].text


def test_whatsapp_parsing_dash_and_system_filtering():
    sample = """15/01/2024, 14:20 - Messages and calls are end-to-end encrypted. No one outside of this chat can read or listen to them.
15/01/2024, 14:22 - Alex: yo did you see the game yesterday?
15/01/2024, 14:23 - Alex: <Media omitted>
15/01/2024, 14:25 - Alex: crazy finish bro
15/01/2024, 14:30 - Me: yeah insane lmao 💀
15/01/2024, 14:31 - Alex: You deleted this message
"""
    messages = parse_whatsapp_text(sample, user_name="Me", contact_name="Alex")
    assert len(messages) == 4
    # Encryption and deletion should be filtered out
    for m in messages:
        assert "end-to-end encrypted" not in m.text
        assert "deleted this message" not in m.text

    assert messages[0].sender_raw == "Alex"
    assert messages[1].media_flag == "media_omitted"
    assert messages[3].is_user is True
    assert "lmao 💀" in messages[3].text


def test_whatsapp_advanced_syntax_filtering():
    """Tests that omitted media variants, deleted notices, calls, and polls do not pollute style profile."""
    sample = """[15/01/24, 14:00:00] Rahul: <Media ommited>
[15/01/24, 14:01:00] Rahul: <This message was omitted>
[15/01/24, 14:02:00] Rahul: This message was deleted
[15/01/24, 14:03:00] Rahul: Missed voice call
[15/01/24, 14:04:00] Rahul: doc.pdf (file attached)
[15/01/24, 14:05:00] Rahul: Location: https://maps.google.com/?q=12.3,45.6
[15/01/24, 14:06:00] Rahul: yo are you free tonight?
[15/01/24, 14:07:00] Me: yeah bro lets do dinner
[15/01/24, 14:08:00] Me: <Media omitted>
[15/01/24, 14:09:00] Me: <This message was omitted>
"""
    messages = parse_whatsapp_text(sample, user_name="Me", contact_name="Rahul")
    turns = merge_consecutive_messages(messages)
    profile = build_style_profile_from_turns(turns, contact_name="Rahul")
    pairs = extract_exchange_pairs(turns)

    # Deleted message and call syntaxes must NOT be in text
    for m in messages:
        assert "<This message was omitted>" not in m.text
        assert "This message was deleted" not in m.text
        assert "Missed voice call" not in m.text

    # Style profile should only count genuine human replies
    assert profile.total_messages_analyzed == 1
    assert "media omitted" not in profile.common_phrases
    assert "message was" not in profile.common_phrases

    # Pairs should only contain clean conversational exchanges
    assert len(pairs) == 1
    assert pairs[0].incoming_text == "yo are you free tonight?"
    assert pairs[0].reply_text == "yeah bro lets do dinner"
