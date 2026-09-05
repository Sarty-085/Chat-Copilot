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

        # Process shared media & placeholders
        text = raw_content

        if msg.get("photos"):
            media_flag = "image"
            text = f"{text} [shared a photo]".strip()
        elif msg.get("videos"):
            media_flag = "video"
            text = f"{text} [shared a video]".strip()
        elif msg.get("audio_files"):
            media_flag = "audio"
            text = f"{text} [voice note]".strip()
        elif msg.get("share"):
            media_flag = "shared_post"
            share_text = fix_meta_mojibake(msg.get("share", {}).get("link", "post"))
            text = f"{text} [shared {share_text}]".strip()

        # Process reactions
        reactions = msg.get("reactions", [])
        if reactions:
            rxn_parts = []
            for rxn in reactions:
                actor = fix_meta_mojibake(rxn.get("actor", ""))
                emoji = fix_meta_mojibake(rxn.get("reaction", "❤️"))
                rxn_parts.append(f"{actor} reacted with {emoji}")
            rxn_str = f" [{', '.join(rxn_parts)}]"
            text = f"{text}{rxn_str}".strip()

        if not text:
            # Empty non-text event (e.g. call log)
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
