import json
from datetime import datetime
from typing import Any, Dict, List, Optional
from .base import UnifiedMessage


def fix_meta_mojibake(text: str) -> str:
    """Fixes Meta's UTF-8 encoded as ISO-8859-1 mojibake commonly found in exports."""
    if not text:
        return ""
    try:
        return text.encode("latin1").decode("utf-8")
    except (UnicodeEncodeError, UnicodeDecodeError):
        return text


def parse_instagram_json(
    json_data: Any,
    user_name: Optional[str] = None,
    contact_name: Optional[str] = None,
) -> List[UnifiedMessage]:
    """
    Parses Instagram messages.json thread data into UnifiedMessage objects.
    Maps reactions, stories, shared media, and audio into structured text placeholders.
    """
    if isinstance(json_data, str):
        data = json.loads(json_data)
    else:
        data = json_data

    raw_messages = data.get("messages", [])
    participants = [fix_meta_mojibake(p.get("name", "")) for p in data.get("participants", [])]
    thread_title = fix_meta_mojibake(data.get("title", ""))

    # Identify contact name
    resolved_contact = contact_name
    if not resolved_contact:
        if thread_title:
            resolved_contact = thread_title
        elif user_name:
            others = [p for p in participants if p.lower() != user_name.lower()]
            resolved_contact = others[0] if others else "Contact"
        else:
            resolved_contact = participants[0] if participants else "Contact"

    messages: List[UnifiedMessage] = []

    for msg in raw_messages:
        sender_raw = fix_meta_mojibake(msg.get("sender_name", ""))
        timestamp_ms = msg.get("timestamp_ms", 0)
        dt = datetime.fromtimestamp(timestamp_ms / 1000.0) if timestamp_ms else datetime.now()

        raw_content = fix_meta_mojibake(msg.get("content", ""))
        media_flag = None

        # Ignore non-text media shares (posts, reels, photos, videos, story shares)
        if msg.get("photos") or msg.get("videos") or msg.get("audio_files"):
            continue

        if msg.get("share"):
            # Shared post or reel
            continue

        # Filter out empty or pure link messages (e.g. reel links)
        if not raw_content or not raw_content.strip():
            continue

        text = raw_content.strip()

        # Check if the content is just an Instagram link or reel
        if text.startswith("http://") or text.startswith("https://") or "instagram.com/reel" in text.lower() or "instagram.com/p/" in text.lower() or "instagram.com/stories" in text.lower():
            continue

        is_user = False
        if user_name and sender_raw.lower() == user_name.lower():
            is_user = True
        elif sender_raw.lower() in ["you", "me"]:
            is_user = True
        elif not user_name and sender_raw != resolved_contact:
            is_user = True

        messages.append(
            UnifiedMessage(
                platform="instagram",
                contact_name=resolved_contact,
                sender_raw=sender_raw,
                is_user=is_user,
                text=text,
                timestamp=dt,
                media_flag=media_flag,
            )
        )

    # Instagram export is usually reverse-chronological (newest first). Sort chronologically.
    messages.sort(key=lambda m: m.timestamp)
    return messages
