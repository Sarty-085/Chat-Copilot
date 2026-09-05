from app.ingestion.instagram_parser import parse_instagram_json


def test_instagram_parsing_reactions_and_shares():
    sample_data = {
        "participants": [{"name": "Alex Rivera"}, {"name": "Jordan"}],
        "title": "Alex Rivera",
        "is_still_participant": True,
        "thread_path": "inbox/alexrivera_123",
        "messages": [
            {
                "sender_name": "Alex Rivera",
                "timestamp_ms": 1705328400000,
                "content": "Check this reel out",
                "share": {"link": "https://www.instagram.com/reel/123"}
            },
            {
                "sender_name": "Jordan",
                "timestamp_ms": 1705328500000,
                "content": "haha this is gold",
                "reactions": [{"reaction": "😂", "actor": "Alex Rivera"}]
            },
            {
                "sender_name": "Alex Rivera",
                "timestamp_ms": 1705328600000,
                "photos": [{"uri": "photos/photo1.jpg"}]
            }
        ]
    }

    messages = parse_instagram_json(sample_data, user_name="Jordan", contact_name="Alex Rivera")
    assert len(messages) == 3
    assert messages[0].sender_raw == "Alex Rivera"
    assert "[shared https://www.instagram.com/reel/123]" in messages[0].text
    assert messages[1].is_user is True
    assert "Alex Rivera reacted with 😂" in messages[1].text
    assert "[shared a photo]" in messages[2].text
