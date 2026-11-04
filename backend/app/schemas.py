"""The privacy contract.

The phone may only send an *anonymized daily summary*: category-level minutes, counts and
an hourly histogram. Every model uses ``extra="forbid"``, so a payload that tries to smuggle
raw sessions, package names or timestamps is rejected with HTTP 422 before it can reach Meta.
"""
from __future__ import annotations

import re
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator

Category = Literal["study", "productive", "communication", "social", "video", "games", "browser", "other"]
_HHMM = re.compile(r"^([01]\d|2[0-3]):[0-5]\d$")
# Package-name-ish strings (com.foo.bar) are not allowed anywhere in free text.
_PKG = re.compile(r"\b[a-z][a-z0-9_]*(\.[a-z0-9_]+){2,}\b", re.IGNORECASE)


class Strict(BaseModel):
    model_config = ConfigDict(extra="forbid")


class StudyBlock(Strict):
    day: Literal["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]
    start: str
    end: str
    subject: str = Field(max_length=40)

    @field_validator("start", "end")
    @classmethod
    def _hhmm(cls, v: str) -> str:
        if not _HHMM.match(v):
            raise ValueError("time must be HH:MM")
        return v


class Exam(Strict):
    subject: str = Field(max_length=40)
    days_until: int = Field(ge=0, le=365)


class DailySummary(Strict):
    """Aggregates only. No app names, no package names, no per-session timestamps."""

    date_label: Literal["today", "yesterday"] = "today"
    weekday: Literal["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]
    focused_min: int = Field(ge=0, le=1440)
    distracted_min: int = Field(ge=0, le=1440)
    neutral_min: int = Field(ge=0, le=1440)
    minutes_by_category: dict[Category, int] = Field(default_factory=dict)
    distracted_min_in_study_blocks: int = Field(ge=0, le=1440, default=0)
    longest_focus_streak_min: int = Field(ge=0, le=1440, default=0)
    app_switches: int = Field(ge=0, le=10000, default=0)
    late_night_min: int = Field(ge=0, le=1440, default=0, description="screen minutes 23:00-05:00")
    distracted_by_hour: list[int] = Field(default_factory=lambda: [0] * 24, min_length=24, max_length=24)
    nudges_sent: int = Field(ge=0, le=100, default=0)
    classifier: Literal["rules", "muse-glimmer", "hybrid"] = "rules"
    study_blocks: list[StudyBlock] = Field(default_factory=list, max_length=50)
    exams: list[Exam] = Field(default_factory=list, max_length=20)
    checkin_note: str | None = Field(default=None, max_length=500)

    @field_validator("minutes_by_category")
    @classmethod
    def _cat_range(cls, v: dict[str, int]) -> dict[str, int]:
        for k, m in v.items():
            if not 0 <= m <= 1440:
                raise ValueError(f"minutes for {k} out of range")
        return v

    @field_validator("checkin_note")
    @classmethod
    def _no_packages(cls, v: str | None) -> str | None:
        if v and _PKG.search(v):
            raise ValueError("checkin_note must not contain package names")
        return v


class PlanBlock(BaseModel):
    start: str
    end: str
    task: str
    why: str


class StudyPlan(BaseModel):
    headline: str
    focus_score: int = Field(ge=0, le=100)
    blocks: list[PlanBlock]
    nudges: list[str]
    exam_strategy: str
    tomorrow_focus_goal_min: int = Field(ge=0, le=900)
    source: str = "muse-spark"


class CheckinIntent(BaseModel):
    subject: str
    planned_minutes: int = Field(ge=0, le=600)
    blockers: list[str]
    summary: str


class CheckinResult(BaseModel):
    transcript: str
    intent: CheckinIntent
    source: str


# JSON Schema handed to Muse Spark via response_format (structured output, dev.meta.ai/docs/structured-output)
PLAN_JSON_SCHEMA = {
    "type": "object",
    "additionalProperties": False,
    "properties": {
        "headline": {"type": "string"},
        "focus_score": {"type": "integer"},
        "blocks": {
            "type": "array",
            "items": {
                "type": "object",
                "additionalProperties": False,
                "properties": {
                    "start": {"type": "string"},
                    "end": {"type": "string"},
                    "task": {"type": "string"},
                    "why": {"type": "string"},
                },
                "required": ["start", "end", "task", "why"],
            },
        },
        "nudges": {"type": "array", "items": {"type": "string"}},
        "exam_strategy": {"type": "string"},
        "tomorrow_focus_goal_min": {"type": "integer"},
    },
    "required": ["headline", "focus_score", "blocks", "nudges", "exam_strategy", "tomorrow_focus_goal_min"],
}

INTENT_JSON_SCHEMA = {
    "type": "object",
    "additionalProperties": False,
    "properties": {
        "subject": {"type": "string"},
        "planned_minutes": {"type": "integer"},
        "blockers": {"type": "array", "items": {"type": "string"}},
        "summary": {"type": "string"},
    },
    "required": ["subject", "planned_minutes", "blockers", "summary"],
}
