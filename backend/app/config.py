"""Runtime configuration, read from environment variables only. Keys are never hardcoded."""
from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path

from dotenv import load_dotenv

load_dotenv(Path(__file__).resolve().parents[1] / ".env")

DEFAULT_BASE = "https://api.meta.ai/v1"  # Meta Model API base URL (dev.meta.ai/docs/api-reference)
DEFAULT_MODEL = "muse-spark-1.3"  # latest Muse Spark per dev.meta.ai/docs/models
DEFAULT_ASR_MODEL = "muse-voice-transcribe-1.0"


@dataclass(frozen=True)
class Settings:
    api_key: str
    api_base: str
    model: str
    asr_model: str
    force_mock: bool
    relay_token: str
    timeout_s: float

    @property
    def mock(self) -> bool:
        """Mock mode = no key configured, or explicitly forced for an offline demo."""
        return self.force_mock or not self.api_key


def load_settings() -> Settings:
    # META_API_KEY is our name; MODEL_API_KEY is the name Meta's own docs use. Accept both.
    key = os.environ.get("META_API_KEY") or os.environ.get("MODEL_API_KEY") or ""
    return Settings(
        api_key=key.strip(),
        api_base=(os.environ.get("META_API_BASE") or DEFAULT_BASE).rstrip("/"),
        model=os.environ.get("META_MODEL") or DEFAULT_MODEL,
        asr_model=os.environ.get("META_ASR_MODEL") or DEFAULT_ASR_MODEL,
        force_mock=os.environ.get("FOCUSGUARD_MOCK", "0").lower() in {"1", "true", "yes"},
        relay_token=os.environ.get("RELAY_TOKEN", ""),
        timeout_s=float(os.environ.get("META_TIMEOUT_S", "90")),
    )
