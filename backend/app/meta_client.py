"""Thin client for the Meta Model API (OpenAI-compatible)."""
from __future__ import annotations

import json

import httpx

from .config import Settings


class UpstreamError(RuntimeError):
    pass


class MetaClient:
    def __init__(self, settings: Settings, transport: httpx.BaseTransport | None = None):
        self.s = settings
        self._client = httpx.Client(
            base_url=settings.api_base,
            headers={"Authorization": "Bearer " + settings.api_key},
            timeout=settings.timeout_s,
            transport=transport,
        )

    def chat_json(self, developer: str, user: str, schema: dict, schema_name: str) -> dict:
        body = {
            "model": self.s.model,
            "messages": [
                {"role": "developer", "content": developer},
                {"role": "user", "content": user},
            ],
            "response_format": {
                "type": "json_schema",
                "json_schema": {"name": schema_name, "schema": schema, "strict": True},
            },
            "reasoning_effort": "low",
        }
        try:
            r = self._client.post("/chat/completions", json=body)
        except httpx.HTTPError as e:
            raise UpstreamError(f"Meta Model API unreachable: {e}") from e
        if r.status_code != 200:
            raise UpstreamError(f"Meta Model API {r.status_code}: {r.text[:300]}")
        try:
            content = r.json()["choices"][0]["message"]["content"]
            return json.loads(content)
        except (KeyError, IndexError, ValueError, TypeError) as e:
            raise UpstreamError(f"Unexpected Meta Model API response: {r.text[:300]}") from e

    def transcribe_wav(self, wav: bytes) -> str:
        req = {"model": self.s.asr_model, "audioEncoding": "WAV", "mode": "PUSH_TO_TALK"}
        files = {
            "request": (None, json.dumps(req), "application/json"),
            "audio": ("checkin.wav", wav, "audio/wav"),
        }
        try:
            r = self._client.post("/asr/transcribe", files=files, headers={"Accept": "application/json"})
        except httpx.HTTPError as e:
            raise UpstreamError(f"Muse Voice Transcribe unreachable: {e}") from e
        if r.status_code != 200:
            raise UpstreamError(f"Muse Voice Transcribe {r.status_code}: {r.text[:300]}")
        try:
            return str(r.json()["transcript"])
        except (KeyError, ValueError) as e:
            raise UpstreamError(f"Unexpected transcribe response: {r.text[:300]}") from e
