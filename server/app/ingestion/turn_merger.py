import re
from typing import List
from .base import UnifiedMessage, UnifiedTurn, ExchangePair


def merge_consecutive_messages(messages: List[UnifiedMessage]) -> List[UnifiedTurn]:
    """
    Concatenates consecutive messages from the same sender into a single logical turn.
    Separate message texts with newlines.
    """
    if not messages:
        return []

    turns: List[UnifiedTurn] = []
    current_sender_is_user = messages[0].is_user
    current_speaker = messages[0].sender_raw
    current_contact = messages[0].contact_name
    current_platform = messages[0].platform
    current_texts = [messages[0].text]
    start_time = messages[0].timestamp
    end_time = messages[0].timestamp
    msg_count = 1

    for msg in messages[1:]:
        if msg.is_user == current_sender_is_user:
            current_texts.append(msg.text)
            end_time = msg.timestamp
            msg_count += 1
        else:
            combined_text = "\n".join(t for t in current_texts if t.strip())
            turns.append(
                UnifiedTurn(
                    platform=current_platform,
                    contact_name=current_contact,
                    speaker="USER" if current_sender_is_user else current_speaker,
                    is_user=current_sender_is_user,
                    text=combined_text,
                    start_time=start_time,
                    end_time=end_time,
                    message_count=msg_count,
                )
            )
            current_sender_is_user = msg.is_user
            current_speaker = msg.sender_raw
            current_contact = msg.contact_name
            current_platform = msg.platform
            current_texts = [msg.text]
            start_time = msg.timestamp
            end_time = msg.timestamp
            msg_count = 1

    if current_texts:
        combined_text = "\n".join(t for t in current_texts if t.strip())
        turns.append(
            UnifiedTurn(
                platform=current_platform,
                contact_name=current_contact,
                speaker="USER" if current_sender_is_user else current_speaker,
                is_user=current_sender_is_user,
                text=combined_text,
                start_time=start_time,
                end_time=end_time,
                message_count=msg_count,
            )
        )

    return turns


def clean_turn_text_for_pairs(text: str) -> str:
    """Strips non-conversational media placeholders from turn text."""
    t = text.strip()
    t = re.sub(r"\[media_omitted\]", "", t, flags=re.IGNORECASE)
    t = re.sub(r"<media\s+om+i+t+ed>", "", t, flags=re.IGNORECASE)
    t = re.sub(r"<this\s+message\s+was\s+(?:om+i+t+ed|deleted)>", "", t, flags=re.IGNORECASE)
    t = re.sub(r"\b(?:image|video|audio|voice\s+(?:call|message)|sticker|document|contact\s+card|gif)\s+omitted\b", "", t, flags=re.IGNORECASE)
    return t.strip()


def extract_exchange_pairs(turns: List[UnifiedTurn]) -> List[ExchangePair]:
    """
    Extracts conversational pairs where a contact's turn is immediately answered by the user's turn.
    Strictly filters out non-conversational media placeholders or omitted message notices.
    """
    pairs: List[ExchangePair] = []
    for i in range(len(turns) - 1):
        turn_a = turns[i]
        turn_b = turns[i + 1]

        # Contact turn followed by User reply
        if not turn_a.is_user and turn_b.is_user:
            clean_incoming = clean_turn_text_for_pairs(turn_a.text)
            clean_reply = clean_turn_text_for_pairs(turn_b.text)

            if clean_incoming and clean_reply:
                pairs.append(
                    ExchangePair(
                        contact_name=turn_a.contact_name,
                        platform=turn_a.platform,
                        incoming_text=clean_incoming,
                        reply_text=clean_reply,
                        timestamp=turn_b.start_time,
                    )
                )
    return pairs
