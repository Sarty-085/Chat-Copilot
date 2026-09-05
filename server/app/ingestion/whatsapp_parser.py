import re
from datetime import datetime
from typing import List, Optional, Tuple
from .base import UnifiedMessage

# WhatsApp timestamp regex patterns
TIMESTAMP_PATTERNS = [
    # 1. Bracketed: [15/01/24, 14:30:15] or [1/15/24, 2:30:15 PM] or [15.01.2024, 14:30]
    re.compile(r"^\[(?P<date>\d{1,4}[/.-]\d{1,2}[/.-]\d{2,4}),\s+(?P<time>\d{1,2}:\d{2}(?::\d{2})?(?:\s+[APap][Mm])?)\]\s+(?P<rest>.*)$"),
    # 2. Standard dash: 15/01/24, 14:30 - or 1/15/24, 2:30 PM - 
    re.compile(r"^(?P<date>\d{1,4}[/.-]\d{1,2}[/.-]\d{2,4}),\s+(?P<time>\d{1,2}:\d{2}(?::\d{2})?(?:\s+[APap][Mm])?)\s+-\s+(?P<rest>.*)$"),
    # 3. Unicode narrow non-breaking space / special characters often in Android exports
    re.compile(r"^(?P<date>\d{1,4}[/.-]\d{1,2}[/.-]\d{2,4}),?\s+(?P<time>\d{1,2}:\d{2}(?::\d{2})?[\s\u202f\xa0]*[APap][Mm])\s+-\s+(?P<rest>.*)$"),
]

# Complete list of WhatsApp system messages, media export placeholders, and non-conversational syntaxes
IGNORED_SYSTEM_OR_SYNTAX_PATTERNS = [
    # Omitted media and attachments
    re.compile(r"^\s*<media\s+om+i+t+ed>\s*$", re.IGNORECASE),
    re.compile(r"^\s*(?:image|video|audio|voice\s+(?:call|message)|sticker|document|contact\s+card|gif)\s+omitted\s*$", re.IGNORECASE),
    re.compile(r"^\s*<attached:\s*.*?>\s*$", re.IGNORECASE),
    re.compile(r"^\s*\S+\.(?:jpg|jpeg|png|mp4|opus|mp3|pdf|docx|zip|webp|apk|m4a|wav)\s+\(file attached\)\s*$", re.IGNORECASE),
    re.compile(r"^\s*\(file attached\)\s*$", re.IGNORECASE),
    
    # Deleted and omitted message notices
    re.compile(r"^\s*<this\s+message\s+was\s+(?:om+i+t+ed|deleted)>\s*$", re.IGNORECASE),
    re.compile(r"^\s*this\s+message\s+was\s+(?:om+i+t+ed|deleted)(?:\s+by\s+an\s+admin)?\s*$", re.IGNORECASE),
    re.compile(r"^\s*you\s+deleted\s+this\s+message\s*$", re.IGNORECASE),
    
    # Call notices
    re.compile(r"^\s*missed\s+(?:group\s+)?(?:voice|video)\s+call\s*$", re.IGNORECASE),
    re.compile(r"^\s*(?:voice|video)\s+call,\s+\d+\s+(?:min|sec|hours?|hr)\s*$", re.IGNORECASE),
    
    # Locations and Polls
    re.compile(r"^\s*live\s+location\s+shared\s*$", re.IGNORECASE),
    re.compile(r"^\s*location:\s*https?://\S+\s*$", re.IGNORECASE),
    re.compile(r"^\s*poll:\s*$", re.IGNORECASE),
    re.compile(r"^\s*view\s+poll\s*$", re.IGNORECASE),
    
    # Encryption, group events and system alerts
    re.compile(r"Messages and calls are end-to-end encrypted", re.IGNORECASE),
    re.compile(r"Waiting for this message\. This may take a while\.", re.IGNORECASE),
    re.compile(r"created group\b", re.IGNORECASE),
    re.compile(r"added you\b", re.IGNORECASE),
    re.compile(r"joined using this group's invite link", re.IGNORECASE),
    re.compile(r"\bleft the group\b", re.IGNORECASE),
    re.compile(r"\bleft\b", re.IGNORECASE),
    re.compile(r"changed the group\b", re.IGNORECASE),
    re.compile(r"changed their phone number", re.IGNORECASE),
    re.compile(r"security code changed", re.IGNORECASE),
    re.compile(r"disappearing messages", re.IGNORECASE),
    re.compile(r"You're now an admin", re.IGNORECASE),
    re.compile(r"Pinned a message", re.IGNORECASE),
    re.compile(r"^\s*null\s*$", re.IGNORECASE),
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
    # Dots & hyphens
    "%d.%m.%y %H:%M:%S",
    "%d.%m.%Y %H:%M:%S",
    "%d.%m.%y %H:%M",
    "%d.%m.%Y %H:%M",
]


class WhatsAppTimestampParser:
    """High-speed timestamp parser with cached successful format for 100x faster execution."""

    def __init__(self, date_order_hint: Optional[str] = None):
        self.cached_format = None
        self.formats = list(DATE_FORMATS)
        if date_order_hint == "MDY":
            self.formats = [f for f in self.formats if f.startswith("%m")] + [f for f in self.formats if not f.startswith("%m")]
        elif date_order_hint == "DMY":
            self.formats = [f for f in self.formats if f.startswith("%d")] + [f for f in self.formats if not f.startswith("%d")]

    def parse(self, date_str: str, time_str: str) -> datetime:
        time_cleaned = time_str.replace("\u202f", " ").replace("\xa0", " ").strip()
        date_cleaned = date_str.replace("-", "/").replace(".", "/").strip()
        combined = f"{date_cleaned} {time_cleaned}"

        if self.cached_format:
            try:
                return datetime.strptime(combined, self.cached_format)
            except ValueError:
                pass

        for fmt in self.formats:
            fmt_clean = fmt.replace(".", "/")
            try:
                dt = datetime.strptime(combined, fmt_clean)
                self.cached_format = fmt_clean
                return dt
            except ValueError:
                continue

        return datetime.now()


def is_ignored_whatsapp_syntax(text: str) -> bool:
    """Checks if message is an export syntax, media placeholder, call, or system message."""
    clean = text.strip()
    if not clean:
        return True
    return any(p.search(clean) for p in IGNORED_SYSTEM_OR_SYNTAX_PATTERNS)


def clean_whatsapp_message_body(text: str) -> Tuple[str, Optional[str]]:
    """
    Cleans message body by stripping media placeholders and tags.
    Returns (cleaned_text, media_flag).
    If the message was only an omitted media or deleted message with no real text,
    returns ('[media_omitted]', 'media_omitted') or ('', None).
    """
    raw = text.strip()
    if is_ignored_whatsapp_syntax(raw):
        # Check if it was purely a media omission
        if re.search(r"om+i+t+ed|file attached", raw, re.IGNORECASE):
            return "[media_omitted]", "media_omitted"
        return "", None

    # Strip inline media omitted tags if user wrote a real caption alongside
    cleaned = re.sub(r"<media\s+om+i+t+ed>", "", raw, flags=re.IGNORECASE)
    cleaned = re.sub(r"<this\s+message\s+was\s+(?:om+i+t+ed|deleted)>", "", cleaned, flags=re.IGNORECASE)
    cleaned = re.sub(r"<attached:\s*.*?>", "", cleaned, flags=re.IGNORECASE)
    cleaned = re.sub(r"\S+\.(?:jpg|jpeg|png|mp4|opus|mp3|pdf|docx|zip|webp|apk)\s+\(file attached\)", "", cleaned, flags=re.IGNORECASE)
    cleaned = re.sub(r"\(file attached\)", "", cleaned, flags=re.IGNORECASE)
    cleaned = re.sub(r"\b(?:image|video|audio|voice\s+(?:call|message)|sticker|document|contact\s+card|gif)\s+omitted\b", "", cleaned, flags=re.IGNORECASE)
    cleaned = cleaned.strip()

    has_media = (len(cleaned) < len(raw))
    flag = "media_omitted" if has_media else None

    if not cleaned:
        return ("[media_omitted]", "media_omitted") if has_media else ("", None)

    return cleaned, flag


def parse_whatsapp_text(
    raw_content: str,
    user_name: Optional[str] = None,
    contact_name: Optional[str] = None,
    date_order_hint: Optional[str] = None,
) -> List[UnifiedMessage]:
    """
    Parses WhatsApp .txt export into a list of UnifiedMessage items.
    Filters out system notifications, deleted message notices, call logs,
    and media omitted placeholders.
    """
    lines = raw_content.splitlines()
    raw_records = []
    current_record = None
    ts_parser = WhatsAppTimestampParser(date_order_hint)

    for line in lines:
        # Strip leading hidden directional/BOM unicode characters often found in WhatsApp exports
        clean_line = re.sub(r"^[\u200e\u200f\ufeff\u202a-\u202e]+", "", line).strip()
        matched = False

        for pattern in TIMESTAMP_PATTERNS:
            m = pattern.match(clean_line)
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
                current_record["multiline"].append(clean_line)

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
            continue

        parts = rest.split(":", 1)
        if len(parts) != 2:
            continue

        sender_raw = parts[0].strip()
        msg_body = parts[1].strip()

        # Clean and filter system/media syntaxes
        clean_text, media_flag = clean_whatsapp_message_body(msg_body)
        if not clean_text:
            continue

        ts = ts_parser.parse(rec["date"], rec["time"])
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
