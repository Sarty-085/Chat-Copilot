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
            # Same sender consecutive message
            current_texts.append(msg.text)
            end_time = msg.timestamp
            msg_count += 1
        else:
            # Sender switched: finalize previous turn
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
            # Start new turn
            current_sender_is_user = msg.is_user
            current_speaker = msg.sender_raw
            current_contact = msg.contact_name
            current_platform = msg.platform
            current_texts = [msg.text]
            start_time = msg.timestamp
            end_time = msg.timestamp
            msg_count = 1

    # Finalize last turn
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


def extract_exchange_pairs(turns: List[UnifiedTurn]) -> List[ExchangePair]:
    """
    Extracts conversational pairs where a contact's turn is immediately answered by the user's turn.
    These pairs form the few-shot RAG retrieval dataset.
    """
    pairs: List[ExchangePair] = []
    for i in range(len(turns) - 1):
        turn_a = turns[i]
        turn_b = turns[i + 1]

        # Contact turn followed by User reply
        if not turn_a.is_user and turn_b.is_user:
            # Filter out trivially empty or single-character noise
            if turn_a.text.strip() and turn_b.text.strip():
                pairs.append(
                    ExchangePair(
                        contact_name=turn_a.contact_name,
                        platform=turn_a.platform,
                        incoming_text=turn_a.text.strip(),
                        reply_text=turn_b.text.strip(),
                        timestamp=turn_b.start_time,
                    )
                )
    return pairs
