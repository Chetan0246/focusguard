import pytest
from fastapi.testclient import TestClient

from app import main


@pytest.fixture(autouse=True)
def clean_env(monkeypatch):
    for k in ["META_API_KEY", "MODEL_API_KEY", "META_API_BASE", "META_MODEL", "FOCUSGUARD_MOCK", "RELAY_TOKEN"]:
        monkeypatch.delenv(k, raising=False)
    main._client_override = None
    yield
    main._client_override = None


@pytest.fixture
def client():
    return TestClient(main.app)


@pytest.fixture
def summary():
    return {
        "weekday": "Tue",
        "focused_min": 140,
        "distracted_min": 95,
        "neutral_min": 30,
        "minutes_by_category": {"study": 120, "social": 60, "video": 35, "productive": 20},
        "distracted_min_in_study_blocks": 25,
        "longest_focus_streak_min": 48,
        "app_switches": 210,
        "late_night_min": 15,
        "distracted_by_hour": [0] * 20 + [30, 40, 15, 10],
        "nudges_sent": 2,
        "classifier": "hybrid",
        "study_blocks": [{"day": "Wed", "start": "18:00", "end": "20:00", "subject": "DSA"}],
        "exams": [{"subject": "DBMS", "days_until": 9}, {"subject": "OS", "days_until": 16}],
    }


def wav_bytes(n_samples=1600, rate=16000):
    import struct
    data = b"\x00\x00" * n_samples
    hdr = b"RIFF" + struct.pack("<I", 36 + len(data)) + b"WAVEfmt " + struct.pack(
        "<IHHIIHH", 16, 1, 1, rate, rate * 2, 2, 16) + b"data" + struct.pack("<I", len(data))
    return hdr + data
