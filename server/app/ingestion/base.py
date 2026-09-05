from datetime import datetime
from typing import List, Optional
from pydantic import BaseModel, Field


class UnifiedMessage(BaseModel):
    """Normalized message representation across platforms."""
    platform: str  # "whatsapp" | "instagram"
    contact_name: str
    sender_raw: str
    is_user: bool
    text: str
    timestamp: datetime
    media_flag: Optional[str] = None  # None, "image", "audio", "reaction", "shared_post", etc.


class UnifiedTurn(BaseModel):
    """A collapsed single logical turn composed of consecutive messages from the same sender."""
    platform: str
    contact_name: str
    speaker: str
    is_user: bool
    text: str
    start_time: datetime
    end_time: datetime
    message_count: int = 1


class ExchangePair(BaseModel):
    """A retrieved conversational pair: Contact statement followed by User reply."""
    contact_name: str
    platform: str
    incoming_text: str
    reply_text: str
    timestamp: datetime
