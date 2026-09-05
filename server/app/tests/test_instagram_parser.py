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
            },
            {
                "sender_name": "Alex Rivera",
                "timestamp_ms": 1705328600000,
                "photos": [{"uri": "photos/photo1.jpg"}]
            },
            {
                "sender_name": "Alex Rivera",
                "timestamp_ms": 1705328700000,
                "content": "https://www.instagram.com/reel/XYZ"
            },
            {
                "sender_name": "Alex Rivera",
                "timestamp_ms": 1705328800000,
                "content": "are you free tomorrow?"
            }
        ]
    }

    messages = parse_instagram_json(sample_data, user_name="Jordan", contact_name="Alex Rivera")
    # Only the genuine text messages "haha this is gold" and "are you free tomorrow?" should be kept!
    assert len(messages) == 2
    assert messages[0].text == "haha this is gold"
    assert messages[0].is_user is True
    assert messages[1].text == "are you free tomorrow?"
    assert messages[1].is_user is False
