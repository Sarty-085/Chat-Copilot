import re
from datetime import datetime
from typing import List, Optional, Tuple
from .base import UnifiedMessage

# Common WhatsApp timestamp regex patterns
TIMESTAMP_PATTERNS = [
    # 1. Bracketed: [15/01/24, 14:30:15] or [1/15/24, 2:30:15 PM]
    re.compile(r"^\[(?P<date>\d{1,2}[/.-]\d{1,2}[/.-]\d{2,4}),\s+(?P<time>\d{1,2}:\d{2}(?::\d{2})?(?:\s+[APap][Mm])?)\]\s+(?P<rest>.*)$"),
    # 2. Standard dash: 15/01/24, 14:30 - or 1/15/24, 2:30 PM - 
    re.compile(r"^(?P<date>\d{1,2}[/.-]\d{1,2}[/.-]\d{2,4}),\s+(?P<time>\d{1,2}:\d{2}(?::\d{2})?(?:\s+[APap][Mm])?)\s+-\s+(?P<rest>.*)$"),
    # 3. Unicode narrow non-breaking space / special characters often in Android exports
    re.compile(r"^(?P<date>\d{1,2}[/.-]\d{1,2}[/.-]\d{2,4}),?\s+(?P<time>\d{1,2}:\d{2}(?::\d{2})?[\s\u202f\xa0]*[APap][Mm])\s+-\s+(?P<rest>.*)$"),
]

SYSTEM_PATTERNS = [
    re.compile(r"Messages and calls are end-to-end encrypted", re.IGNORECASE),
    re.compile(r"created group", re.IGNORECASE),
    re.compile(r"added you", re.IGNORECASE),
    re.compile(r"joined using this group's invite link", re.IGNORECASE),
    re.compile(r"left\b", re.IGNORECASE),
    re.compile(r"changed the group", re.IGNORECASE),
    re.compile(r"security code changed", re.IGNORECASE),
    re.compile(r"disappearing messages", re.IGNORECASE),
    re.compile(r"Waiting for this message", re.IGNORECASE),
    re.compile(r"You deleted this message", re.IGNORECASE),
    re.compile(r"This message was deleted", re.IGNORECASE),
]

MEDIA_OMITTED_PATTERNS = [
    (re.compile(r"<Media omitted>", re.IGNORECASE), "media_omitted"),
    (re.compile(r"image omitted", re.IGNORECASE), "image"),
    (re.compile(r"video omitted", re.IGNORECASE), "video"),
    (re.compile(r"audio omitted", re.IGNORECASE), "audio"),
    (re.compile(r"sticker omitted", re.IGNORECASE), "sticker"),
    (re.compile(r"document omitted", re.IGNORECASE), "document"),
    (re.compile(r"GIF omitted", re.IGNORECASE), "gif"),
]

DATE_FORMATS = [
    # Day first
    "%d/%m/%y %H:%M:%S",
    "%d/%m/%Y %H:%M:%S",
    "%d/%m/%y %H:%M",
    "%d/%m/%Y %H:%M",
    "%d/%m/%y %I:%M:%S %p",
    "%d/%m/%Y %I:%M:%S %p",
    "%d/%m/%y %I:%M %p",
    "%d/%m/%Y %I:%M %p",
    # Month first
    "%m/%d/%y %H:%M:%S",
    "%m/%d/%Y %H:%M:%S",
    "%m/%d/%y %H:%M",
    "%m/%d/%Y %H:%M",
    "%m/%d/%y %I:%M:%S %p",
    "%m/%d/%Y %I:%M:%S %p",
    "%m/%d/%y %I:%M %p",
    "%m/%d/%Y %I:%M %p",
    # Dots
    "%d.%m.%y %H:%M:%S",
    "%d.%m.%Y %H:%M:%S",
    "%d.%m.%y %H:%M",
    "%d.%m.%Y %H:%M",
]


def parse_timestamp(date_str: str, time_str: str, date_order_hint: Optional[str] = None) -> datetime:
    """Parses a date and time string into a datetime object with intelligent format detection."""
    # Clean non-breaking spaces
    time_cleaned = time_str.replace("\u202f", " ").replace("\xa0", " ").strip()
    date_cleaned = date_str.replace("-", "/").replace(".", "/").strip()
    combined = f"{date_cleaned} {time_cleaned}"

    # Reorder formats based on hint if provided
    formats = list(DATE_FORMATS)
    if date_order_hint == "MDY":
        formats = [f for f in formats if f.startswith("%m")] + [f for f in formats if not f.startswith("%m")]
    elif date_order_hint == "DMY":
        formats = [f for f in formats if f.startswith("%d")] + [f for f in formats if not f.startswith("%d")]

    for fmt in formats:
        try:
            # Replace / with match
            fmt_clean = fmt.replace(".", "/")
            return datetime.strptime(combined, fmt_clean)
        except ValueError:
            continue

    # Fallback to current time if parsing completely fails
    return datetime.now()


def is_system_message(text: str) -> bool:
    """Checks if message is a WhatsApp system alert."""
    return any(pattern.search(text) for pattern in SYSTEM_PATTERNS)


def extract_media_flag(text: str) -> Tuple[str, Optional[str]]:
    """Checks for media placeholders and returns (cleaned_text, media_flag)."""
    for pattern, flag in MEDIA_OMITTED_PATTERNS:
        if pattern.search(text):
            return f"[{flag}]", flag
    return text, None


def parse_whatsapp_text(
    raw_content: str,
    user_name: Optional[str] = None,
    contact_name: Optional[str] = None,
    date_order_hint: Optional[str] = None,
) -> List[UnifiedMessage]:
    """
    Parses WhatsApp .txt export into a list of UnifiedMessage items.
    Handles:
    - Multi-line message accumulation
    - 12h/24h timestamps
    - System message filtering
    - Media indicator conversion
    """
    lines = raw_content.splitlines()
    raw_records = []
    current_record = None

    for line in lines:
        matched = False
        for pattern in TIMESTAMP_PATTERNS:
            m = pattern.match(line)
            if m:
                if current_record:
                    raw_records.append(current_record)
                current_record = {
                    "date": m.group("date"),
                    "time": m.group("time"),
                    "rest": m.group("rest"),
                    "multiline": []
                }
                matched = True
                break

        if not matched:
            if current_record is not None:
                current_record["multiline"].append(line)

    if current_record:
        raw_records.append(current_record)

    messages: List[UnifiedMessage] = []
    discovered_senders = set()

    for rec in raw_records:
        rest = rec["rest"]
        if rec["multiline"]:
            rest = rest + "\n" + "\n".join(rec["multiline"])

        # Check if message has a sender colon (e.g. "Sender: Message")
        if ":" not in rest:
            # Likely system notification without sender
            if is_system_message(rest):
                continue

        parts = rest.split(":", 1)
        if len(parts) == 2:
            sender_raw = parts[0].strip()
            msg_body = parts[1].strip()
        else:
            # System message or unsent line
            if is_system_message(rest):
                continue
            sender_raw = "System"
            msg_body = rest.strip()

        if is_system_message(msg_body):
            continue

        clean_text, media_flag = extract_media_flag(msg_body)
        ts = parse_timestamp(rec["date"], rec["time"], date_order_hint)

        discovered_senders.add(sender_raw)

        messages.append({
            "sender_raw": sender_raw,
            "text": clean_text,
            "timestamp": ts,
            "media_flag": media_flag,
        })

    # Identify user vs contact
    resolved_contact = contact_name
    if not resolved_contact:
        # If user_name is provided, contact is the other sender
        other_senders = [s for s in discovered_senders if s != user_name and s != "You" and s != "System"]
        resolved_contact = other_senders[0] if other_senders else "Contact"

    output_messages: List[UnifiedMessage] = []
    for item in messages:
        sender = item["sender_raw"]
        is_user = False
        if user_name and sender.lower() == user_name.lower():
            is_user = True
        elif sender.lower() in ["you", "me"]:
            is_user = True
        elif not user_name and sender != resolved_contact:
            # Heuristic: if sender is not the detected contact, assume it's user
            is_user = True

        output_messages.append(
            UnifiedMessage(
                platform="whatsapp",
                contact_name=resolved_contact,
                sender_raw=sender,
                is_user=is_user,
                text=item["text"],
                timestamp=item["timestamp"],
                media_flag=item["media_flag"],
            )
        )

    return output_messages
