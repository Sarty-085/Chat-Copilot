import json
import sqlite3
from datetime import datetime
from pathlib import Path
from typing import List, Optional
from ..ingestion.base import ExchangePair, UnifiedTurn
from .style_profiler import ContactStyleProfile

DEFAULT_DB_PATH = Path("data/chatpilot.db")


class LocalStorage:
    """Manages persistent SQLite storage for contact profiles and past exchange pairs."""

    def __init__(self, db_path: Optional[Path] = None):
        self.db_path = db_path or DEFAULT_DB_PATH
        self.db_path.parent.mkdir(parents=True, exist_ok=True)
        self._init_db()

    def _get_conn(self) -> sqlite3.Connection:
        conn = sqlite3.connect(self.db_path)
        conn.row_factory = sqlite3.Row
        return conn

    def _init_db(self):
        with self._get_conn() as conn:
            cursor = conn.cursor()
            # Contact style profiles table
            cursor.execute("""
                CREATE TABLE IF NOT EXISTS contact_profiles (
                    contact_name TEXT PRIMARY KEY,
                    profile_json TEXT NOT NULL,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """)
            # Exchange pairs table for RAG retrieval
            cursor.execute("""
                CREATE TABLE IF NOT EXISTS exchange_pairs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    contact_name TEXT NOT NULL,
                    platform TEXT NOT NULL,
                    incoming_text TEXT NOT NULL,
                    reply_text TEXT NOT NULL,
                    timestamp TIMESTAMP,
                    embedding_json TEXT
                )
            """)
            cursor.execute("CREATE INDEX IF NOT EXISTS idx_pairs_contact ON exchange_pairs(contact_name)")
            conn.commit()

    def save_profile(self, profile: ContactStyleProfile):
        with self._get_conn() as conn:
            cursor = conn.cursor()
            cursor.execute(
                """
                INSERT INTO contact_profiles (contact_name, profile_json, updated_at)
                VALUES (?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT(contact_name) DO UPDATE SET
                    profile_json = excluded.profile_json,
                    updated_at = CURRENT_TIMESTAMP
                """,
                (profile.contact_name, profile.model_dump_json()),
            )
            conn.commit()

    def get_profile(self, contact_name: str) -> Optional[ContactStyleProfile]:
        with self._get_conn() as conn:
            cursor = conn.cursor()
            cursor.execute(
                "SELECT profile_json FROM contact_profiles WHERE LOWER(contact_name) = LOWER(?)",
                (contact_name,),
            )
            row = cursor.fetchone()
            if row:
                data = json.loads(row["profile_json"])
                return ContactStyleProfile(**data)
            return None

    def list_profiles(self) -> List[ContactStyleProfile]:
        with self._get_conn() as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT profile_json FROM contact_profiles ORDER BY contact_name ASC")
            rows = cursor.fetchall()
            return [ContactStyleProfile(**json.loads(r["profile_json"])) for r in rows]

    def save_exchange_pairs(self, pairs: List[ExchangePair], embeddings: Optional[List[List[float]]] = None):
        with self._get_conn() as conn:
            cursor = conn.cursor()
            for i, pair in enumerate(pairs):
                emb_json = json.dumps(embeddings[i]) if embeddings and i < len(embeddings) else None
                cursor.execute(
                    """
                    INSERT INTO exchange_pairs (contact_name, platform, incoming_text, reply_text, timestamp, embedding_json)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    (
                        pair.contact_name,
                        pair.platform,
                        pair.incoming_text,
                        pair.reply_text,
                        pair.timestamp.isoformat(),
                        emb_json,
                    ),
                )
            conn.commit()

    def get_exchange_pairs(self, contact_name: str) -> List[dict]:
        with self._get_conn() as conn:
            cursor = conn.cursor()
            cursor.execute(
                """
                SELECT id, contact_name, platform, incoming_text, reply_text, timestamp, embedding_json
                FROM exchange_pairs
                WHERE LOWER(contact_name) = LOWER(?)
                ORDER BY timestamp DESC
                """,
                (contact_name,),
            )
            rows = cursor.fetchall()
            results = []
            for r in rows:
                results.append({
                    "id": r["id"],
                    "contact_name": r["contact_name"],
                    "platform": r["platform"],
                    "incoming_text": r["incoming_text"],
                    "reply_text": r["reply_text"],
                    "timestamp": r["timestamp"],
                    "embedding": json.loads(r["embedding_json"]) if r["embedding_json"] else None,
                })
            return results
