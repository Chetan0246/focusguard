"""Deterministic OFFLINE MOCK of the Muse Spark / Muse Voice responses.

Used when no META_API_KEY is configured or FOCUSGUARD_MOCK=1. Every mock response is labelled
``source="mock"`` so a demo can never be mistaken for a live model call.
"""
from __future__ import annotations

import re

from .schemas import CheckinIntent, CheckinResult, DailySummary, PlanBlock, StudyPlan

DAYS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]


def _add(hhmm: str, minutes: int) -> str:
    h, m = map(int, hhmm.split(":"))
    t = min(h * 60 + m + minutes, 23 * 60 + 59)
    return f"{t // 60:02d}:{t % 60:02d}"


def mock_plan(s: DailySummary, tomorrow: str) -> StudyPlan:
    total = s.focused_min + s.distracted_min
    score = round(100 * s.focused_min / total) if total else 50
    score = max(0, min(100, score + min(s.longest_focus_streak_min // 15, 10)))
    worst_hour = max(range(24), key=lambda h: s.distracted_by_hour[h]) if any(s.distracted_by_hour) else 21
    exams = sorted(s.exams, key=lambda e: e.days_until)
    subjects = list(dict.fromkeys([e.subject for e in exams] + [b.subject for b in s.study_blocks])) or ["Revision"]
    tomorrow_blocks = [b for b in s.study_blocks if b.day == tomorrow] or [
        b for b in s.study_blocks if b.day == s.weekday
    ]
    start = tomorrow_blocks[0].start if tomorrow_blocks else "18:00"
    blocks = []
    cursor = start
    for i, subj in enumerate((subjects * 3)[:4]):
        end = _add(cursor, 45 if i < 3 else 30)
        blocks.append(PlanBlock(start=cursor, end=end, task=f"{subj}: deep work (phone in another room)",
                                why="closest exam first" if i == 0 else "spaced repetition"))
        cursor = _add(end, 10)
    top_cat = max(s.minutes_by_category, key=s.minutes_by_category.get) if s.minutes_by_category else "video"
    goal = max(30, min(round(s.focused_min * 1.2), s.focused_min + 60))
    return StudyPlan(
        headline=f"{s.focused_min} min focused vs {s.distracted_min} min distracted; peak distraction at {worst_hour:02d}:00.",
        focus_score=score,
        blocks=blocks,
        nudges=[
            f"It's {worst_hour:02d}:00, your usual slump. 10 more minutes on {subjects[0]}?",
            f"{top_cat.capitalize()} has had {s.minutes_by_category.get(top_cat, 0)} min today. Back to the plan?",
            "Study block running. Park the phone face-down for 25 minutes.",
        ],
        exam_strategy=(
            f"Spend about 60% of tomorrow on {subjects[0]} ({exams[0].days_until} days left) and the rest on review."
            if exams else "No exams set: rotate subjects and keep one long block for the hardest topic."
        ),
        tomorrow_focus_goal_min=goal,
        source="mock",
    )


def mock_checkin(audio_bytes: int) -> CheckinResult:
    transcript = "I'm going to do DBMS normalization for ninety minutes, but I keep checking Instagram."
    return CheckinResult(transcript=transcript, intent=intent_from_text(transcript), source="mock")


_NUM_WORDS = {"thirty": 30, "forty five": 45, "forty-five": 45, "sixty": 60, "ninety": 90,
              "an hour": 60, "one hour": 60, "two hours": 120, "half an hour": 30}


def intent_from_text(text: str) -> CheckinIntent:
    """Tiny heuristic parser used in mock mode (and as a fallback if the model call fails)."""
    low = text.lower()
    minutes = 0
    m = re.search(r"(\d{1,3})\s*(min|minutes|hours?|hrs?)", low)
    if m:
        minutes = int(m.group(1)) * (60 if m.group(2).startswith("h") else 1)
    else:
        for w, v in _NUM_WORDS.items():
            if w in low:
                minutes = v
                break
    subj = "General"
    m2 = re.search(r"\bdo(?:ing)?\s+([A-Za-z]+(?:\s+[A-Za-z]+)?)", text)
    if m2:
        subj = m2.group(1).strip()
    blockers = []
    if "but" in low:
        blockers.append(text[low.index("but") + 3:].strip(" .").capitalize())
    return CheckinIntent(subject=subj, planned_minutes=min(minutes, 600), blockers=blockers,
                         summary=f"Plans {minutes or 'some'} min on {subj}.")
