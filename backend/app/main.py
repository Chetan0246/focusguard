"""FocusGuard relay.

Keeps the Meta Model API key off the phone. Accepts ONLY the anonymized daily summary
(see schemas.DailySummary) and forwards it to Muse Spark; accepts a short WAV voice check-in and
forwards it to Muse Voice Transcribe. Stores nothing.

Run:  uvicorn app.main:app --host 0.0.0.0 --port 8000
"""
from __future__ import annotations

import datetime as dt

from fastapi import Depends, FastAPI, File, Header, HTTPException, UploadFile
from pydantic import ValidationError

from . import mock
from .config import Settings, load_settings
from .meta_client import MetaClient, UpstreamError
from .prompts import INTENT_DEVELOPER, PLAN_DEVELOPER, plan_user_message
from .schemas import (
    INTENT_JSON_SCHEMA,
    PLAN_JSON_SCHEMA,
    CheckinIntent,
    CheckinResult,
    DailySummary,
    StudyPlan,
)

app = FastAPI(title="FocusGuard Relay", version="1.0.0")
MAX_WAV_BYTES = 8 * 1024 * 1024  # ~3 min at 24 kHz mono; Meta's limit is 32 MB / 10 min

_client_override: MetaClient | None = None  # tests inject a client with a mock transport


def get_settings() -> Settings:
    return load_settings()


def get_client(settings: Settings = Depends(get_settings)) -> MetaClient:
    return _client_override or MetaClient(settings)


def check_token(settings: Settings = Depends(get_settings), x_relay_token: str | None = Header(default=None)):
    if settings.relay_token and x_relay_token != settings.relay_token:
        raise HTTPException(status_code=401, detail="bad relay token")


@app.get("/health")
def health(settings: Settings = Depends(get_settings)):
    return {
        "status": "ok",
        "mode": "mock" if settings.mock else "live",
        "model": settings.model,
        "asr_model": settings.asr_model,
        "api_base": settings.api_base,
    }


def _tomorrow(weekday: str) -> str:
    i = mock.DAYS.index(weekday)
    return mock.DAYS[(i + 1) % 7]


@app.post("/v1/plan", response_model=StudyPlan, dependencies=[Depends(check_token)])
def plan(summary: DailySummary, settings: Settings = Depends(get_settings)):
    tomorrow = _tomorrow(summary.weekday)
    if settings.mock:
        return mock.mock_plan(summary, tomorrow)
    client = get_client(settings)
    try:
        raw = client.chat_json(PLAN_DEVELOPER, plan_user_message(summary, tomorrow), PLAN_JSON_SCHEMA, "study_plan")
        return StudyPlan(**raw, source=settings.model)
    except UpstreamError as e:
        raise HTTPException(status_code=502, detail=str(e))
    except (ValidationError, TypeError) as e:
        raise HTTPException(status_code=502, detail=f"model output failed validation: {e}")


@app.post("/v1/checkin", response_model=CheckinResult, dependencies=[Depends(check_token)])
async def checkin(audio: UploadFile = File(...), settings: Settings = Depends(get_settings)):
    data = await audio.read()
    if len(data) > MAX_WAV_BYTES:
        raise HTTPException(status_code=413, detail="audio too large")
    if len(data) < 44 or data[:4] != b"RIFF" or data[8:12] != b"WAVE":
        raise HTTPException(status_code=400, detail="audio must be a WAV file (mono 16-bit PCM, 16 or 24 kHz)")
    if settings.mock:
        return mock.mock_checkin(len(data))
    client = get_client(settings)
    try:
        transcript = client.transcribe_wav(data)
    except UpstreamError as e:
        raise HTTPException(status_code=502, detail=str(e))
    try:
        raw = client.chat_json(INTENT_DEVELOPER, transcript, INTENT_JSON_SCHEMA, "checkin_intent")
        intent = CheckinIntent(**raw)
        source = f"{settings.asr_model}+{settings.model}"
    except (UpstreamError, ValidationError, TypeError):
        intent = mock.intent_from_text(transcript)  # still useful: transcript is real
        source = f"{settings.asr_model}+heuristic"
    return CheckinResult(transcript=transcript, intent=intent, source=source)


@app.get("/")
def root():
    return {"service": "focusguard-relay", "time": dt.datetime.now(dt.timezone.utc).isoformat(), "docs": "/docs"}
