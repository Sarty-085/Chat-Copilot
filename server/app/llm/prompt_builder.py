import json
from typing import Dict, List, Optional
from ..memory.style_profiler import ContactStyleProfile


def build_suggestion_prompt(
    contact_name: str,
    platform: str,
    recent_turns: List[Dict[str, str]],  # [{"speaker": "Contact"|"USER", "text": "..."}]
    style_profile: Optional[ContactStyleProfile] = None,
    few_shot_exchanges: Optional[List[Dict[str, str]]] = None,
) -> List[Dict[str, str]]:
    """
    Constructs the conversational prompt containing:
    1. System instructions + persona mimicking constraints from style profile.
    2. Retrieved few-shot past exchanges with this specific contact.
    3. Active conversation turns.
    4. Instruction to output exactly 3 distinct reply candidate chips as JSON.
    """
    style_snippet = ""
    if style_profile:
        style_snippet = f"\n{style_profile.to_system_prompt_snippet()}\n"
    else:
        style_snippet = f"\n- Speaking to: {contact_name}\n- Tone: Natural, friendly, casual messaging style."

    system_instruction = f"""You are ChatPilot, an AI reply assistant operating on behalf of the user in {platform.capitalize()}.
Your task is to generate 3 short, on-brand reply suggestions that sound EXACTLY like the user would reply to {contact_name}.
{style_snippet}
CRITICAL GUIDELINES:
1. NEVER send or suggest robotic/corporate pleasantries unless specifically required by the style profile.
2. Emulate the user's exact lowercase habits, emoji frequency, and concise sentence structures.
3. Generate 3 distinct options with slight flavor variations:
   - Option 1 (Direct): Quick, straight-to-the-point response.
   - Option 2 (Warm / Engaging): Adds a question, continuation, or friendly detail.
   - Option 3 (Playful / Short Reaction): Terse acknowledgement, reaction emoji, or banter.
4. Output format MUST be a valid JSON array of 3 strings:
["option 1", "option 2", "option 3"]
Output ONLY the raw JSON array. Do not include markdown formatting or commentary."""

    messages = [{"role": "system", "content": system_instruction}]

    # Inject few-shot historical examples
    if few_shot_exchanges:
        example_block = "Here are genuine examples of how the user historically replied to this contact:\n"
        for i, ex in enumerate(few_shot_exchanges, 1):
            inc = ex.get("incoming_text", "").strip()
            rep = ex.get("reply_text", "").strip()
            example_block += f"Example {i}:\nIncoming: {inc}\nUser Reply: {rep}\n"
        messages.append({"role": "system", "content": example_block})

    # Append recent live conversation context
    for turn in recent_turns:
        role = "assistant" if turn.get("is_user") or turn.get("speaker") == "USER" else "user"
        content = turn.get("text", "")
        messages.append({"role": role, "content": content})

    # Final prompt trigger
    messages.append({
        "role": "user",
        "content": f"[Generate 3 on-brand reply suggestions for the user as a JSON array of strings]"
    })

    return messages
