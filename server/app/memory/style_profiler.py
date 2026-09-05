import re
from collections import Counter
from typing import Dict, List, Optional
from pydantic import BaseModel, Field
from ..ingestion.base import UnifiedTurn

# Regex for common emojis
EMOJI_PATTERN = re.compile(
    r"[\U00010000-\U0010ffff]|"
    r"[\u2600-\u27BF]|"
    r"[\u2300-\u23FF]|"
    r"[\u2B50-\u2B55]|"
    r"[\u203C\u2049\u2122\u2139\u2194-\u2199\u21A9-\u21AA]"
)

SLANG_WORDS = {
    "lol", "lmao", "haha", "hahaha", "bruh", "nah", "yeah", "yep", "tbh", "idk",
    "gonna", "wanna", "gotta", "bet", "fr", "ngl", "sup", "bro", "dude", "yo",
    "omg", "pls", "plz", "rn", "np", "ty", "thx", "btw", "rip", "smh", "gg"
}

FORMAL_WORDS = {
    "please", "thank", "sincerely", "regards", "appreciate", "however", "furthermore",
    "certainly", "apologies", "assistance", "received", "confirm", "schedule", "regarding"
}


class ContactStyleProfile(BaseModel):
    contact_name: str
    total_messages_analyzed: int = 0
    avg_chars_per_reply: float = 0.0
    avg_words_per_reply: float = 0.0
    emoji_density: float = 0.0  # Emojis per message
    top_emojis: List[str] = Field(default_factory=list)
    formality_score: float = 0.3  # 0.0 = ultra casual, 1.0 = formal
    capitalization_rate: float = 0.5  # Ratio of messages starting with capital letter
    punctuation_style: str = "balanced"
    common_phrases: List[str] = Field(default_factory=list)
    common_greetings: List[str] = Field(default_factory=list)
    common_signoffs: List[str] = Field(default_factory=list)
    custom_tone_notes: str = ""

    def to_system_prompt_snippet(self) -> str:
        """Generates concise persona constraints for the system prompt."""
        caps_desc = "Standard capitalization." if self.capitalization_rate > 0.6 else "Rarely uses initial capitalization (lowercase aesthetic)."
        formality_desc = "Casual and direct" if self.formality_score < 0.4 else ("Formal and polite" if self.formality_score > 0.7 else "Friendly and conversational")

        emoji_desc = "None"
        if self.emoji_density > 0.8:
            emoji_desc = f"High frequency. Often uses: {' '.join(self.top_emojis[:4])}"
        elif self.emoji_density > 0.2:
            emoji_desc = f"Moderate. Preferred emojis: {' '.join(self.top_emojis[:3])}"
        else:
            emoji_desc = "Rarely or never uses emojis."

        phrases_desc = ", ".join(f'"{p}"' for p in self.common_phrases[:6]) if self.common_phrases else "None in particular"

        notes = f"\n- User-customized guidance: {self.custom_tone_notes}" if self.custom_tone_notes else ""

        return f"""### Contact-Specific Style Profile (Speaking to {self.contact_name}):
- Overall Tone: {formality_desc} (formality index: {self.formality_score:.2f})
- Typical Length: ~{int(self.avg_words_per_reply)} words (~{int(self.avg_chars_per_reply)} characters). Keep replies succinct.
- Capitalization: {caps_desc}
- Punctuation Habit: {self.punctuation_style}
- Emojis: {emoji_desc}
- Characteristic Catchphrases/Vocabulary: {phrases_desc}{notes}"""


def build_style_profile_from_turns(turns: List[UnifiedTurn], contact_name: str) -> ContactStyleProfile:
    """
    Analyzes all user replies directed to a specific contact to construct their style profile.
    """
    # Filter only turns where is_user is True
    user_turns = [t for t in turns if t.is_user and t.contact_name.lower() == contact_name.lower()]

    if not user_turns:
        # Fallback default profile if no history
        return ContactStyleProfile(
            contact_name=contact_name,
            total_messages_analyzed=0,
            avg_chars_per_reply=35.0,
            avg_words_per_reply=7.0,
            emoji_density=0.3,
            formality_score=0.35,
            punctuation_style="casual",
            custom_tone_notes="Match the contact's tone naturally."
        )

    total_chars = 0
    total_words = 0
    emoji_counter = Counter()
    total_emojis = 0
    caps_count = 0
    exclamation_count = 0
    question_count = 0
    period_count = 0
    no_punct_count = 0
    slang_matches = 0
    formal_matches = 0

    all_tokens = []
    n_gram_counter = Counter()

    for turn in user_turns:
        text = turn.text.strip()
        if not text:
            continue

        total_chars += len(text)
        words = re.findall(r"\b\w+\b", text.lower())
        total_words += len(words)
        all_tokens.extend(words)

        # Capitalization check
        if text[0].isupper():
            caps_count += 1

        # Punctuation check
        if text.endswith("!"):
            exclamation_count += 1
        elif text.endswith("?"):
            question_count += 1
        elif text.endswith("."):
            period_count += 1
        else:
            no_punct_count += 1

        # Emojis
        emojis_found = EMOJI_PATTERN.findall(text)
        total_emojis += len(emojis_found)
        for em in emojis_found:
            emoji_counter[em] += 1

        # Word n-grams (2-word phrases)
        for i in range(len(words) - 1):
            phrase = f"{words[i]} {words[i+1]}"
            n_gram_counter[phrase] += 1

        # Slang vs formal counts
        for w in words:
            if w in SLANG_WORDS:
                slang_matches += 1
            if w in FORMAL_WORDS:
                formal_matches += 1

    n = len(user_turns)
    avg_chars = total_chars / n
    avg_words = total_words / n
    emoji_density = total_emojis / n
    caps_rate = caps_count / n

    # Determine punctuation style
    if no_punct_count > (n * 0.5):
        punct_style = "omits trailing punctuation / sends punchy short texts"
    elif exclamation_count > (n * 0.3):
        punct_style = "enthusiastic with exclamation marks"
    elif period_count > (n * 0.4):
        punct_style = "complete sentences with terminal periods"
    else:
        punct_style = "natural, minimal punctuation"

    # Formality score (0.0 to 1.0)
    # Baseline 0.35 + adjustments
    formality = 0.35
    if slang_matches > (n * 0.5):
        formality -= 0.15
    if formal_matches > (n * 0.3):
        formality += 0.25
    if caps_rate < 0.25:
        formality -= 0.10
    if caps_rate > 0.75:
        formality += 0.15
    formality = max(0.05, min(0.95, formality))

    # Top phrases
    filtered_phrases = [
        phrase for phrase, count in n_gram_counter.most_common(15)
        if count >= 2 and not all(w in {"the", "a", "an", "to", "in", "it", "is"} for w in phrase.split())
    ]

    return ContactStyleProfile(
        contact_name=contact_name,
        total_messages_analyzed=n,
        avg_chars_per_reply=round(avg_chars, 1),
        avg_words_per_reply=round(avg_words, 1),
        emoji_density=round(emoji_density, 2),
        top_emojis=[em for em, _ in emoji_counter.most_common(5)],
        formality_score=round(formality, 2),
        capitalization_rate=round(caps_rate, 2),
        punctuation_style=punct_style,
        common_phrases=filtered_phrases[:8],
        custom_tone_notes="",
    )
