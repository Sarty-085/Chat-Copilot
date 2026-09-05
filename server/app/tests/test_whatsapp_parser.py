from app.ingestion.whatsapp_parser import parse_whatsapp_text


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
