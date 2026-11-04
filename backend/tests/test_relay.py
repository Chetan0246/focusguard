import json

import httpx
import pytest

from app import main
from app.config import load_settings
from app.meta_client import MetaClient
from tests.conftest import wav_bytes


def test_health_reports_mock_without_key(client):
    r = client.get("/health")
    assert r.status_code == 200
    assert r.json()["mode"] == "mock"
    assert r.json()["api_base"] == "https://api.meta.ai/v1"
    assert r.json()["model"] == "muse-spark-1.3"


def test_health_live_with_key_and_env_overrides(client, monkeypatch):
    monkeypatch.setenv("META_API_KEY", "LLM|1|x")
    monkeypatch.setenv("META_MODEL", "muse-spark-1.2")
    monkeypatch.setenv("META_API_BASE", "https://example.test/v1/")
    j = client.get("/health").json()
    assert j["mode"] == "live" and j["model"] == "muse-spark-1.2" and j["api_base"] == "https://example.test/v1"


def test_force_mock_overrides_key(monkeypatch):
    monkeypatch.setenv("META_API_KEY", "k")
    monkeypatch.setenv("FOCUSGUARD_MOCK", "1")
    assert load_settings().mock is True


def test_model_api_key_alias(monkeypatch):
    monkeypatch.setenv("MODEL_API_KEY", "abc")
    assert load_settings().api_key == "abc"


def test_mock_plan_is_labelled_and_uses_exams(client, summary):
    r = client.post("/v1/plan", json=summary)
    assert r.status_code == 200, r.text
    p = r.json()
    assert p["source"] == "mock"
    assert p["blocks"][0]["start"] == "18:00"  # tomorrow (Wed) study block
    assert "DBMS" in p["blocks"][0]["task"]  # closest exam first
    assert 0 <= p["focus_score"] <= 100
    assert p["tomorrow_focus_goal_min"] >= 30


@pytest.mark.parametrize("extra", [
    {"sessions": [{"package": "com.instagram.android", "start": 1, "end": 2}]},
    {"top_apps": ["Instagram"]},
])
def test_privacy_contract_rejects_raw_usage(client, summary, extra):
    r = client.post("/v1/plan", json={**summary, **extra})
    assert r.status_code == 422


def test_rejects_package_names_in_note(client, summary):
    r = client.post("/v1/plan", json={**summary, "checkin_note": "stuck on com.google.android.youtube"})
    assert r.status_code == 422


def test_rejects_bad_histogram(client, summary):
    r = client.post("/v1/plan", json={**summary, "distracted_by_hour": [1, 2, 3]})
    assert r.status_code == 422


def test_relay_token_enforced(client, summary, monkeypatch):
    monkeypatch.setenv("RELAY_TOKEN", "s3cret")
    assert client.post("/v1/plan", json=summary).status_code == 401
    assert client.post("/v1/plan", json=summary, headers={"X-Relay-Token": "s3cret"}).status_code == 200


def _live(monkeypatch, handler):
    monkeypatch.setenv("META_API_KEY", "LLM|123|secret")
    main._client_override = MetaClient(load_settings(), transport=httpx.MockTransport(handler))


PLAN = {
    "headline": "140 focused vs 95 distracted.", "focus_score": 61,
    "blocks": [{"start": "18:00", "end": "18:45", "task": "DBMS normal forms", "why": "exam in 9 days"}],
    "nudges": ["21:00 is your slump. One more Pomodoro?"],
    "exam_strategy": "DBMS first.", "tomorrow_focus_goal_min": 160,
}


def test_live_plan_sends_openai_compatible_request(client, summary, monkeypatch):
    seen = {}

    def handler(req: httpx.Request):
        seen["url"] = str(req.url)
        seen["auth"] = req.headers["authorization"]
        seen["body"] = json.loads(req.content)
        return httpx.Response(200, json={"choices": [{"message": {"role": "assistant", "content": json.dumps(PLAN)}}]})

    _live(monkeypatch, handler)
    r = client.post("/v1/plan", json=summary)
    assert r.status_code == 200, r.text
    assert r.json()["source"] == "muse-spark-1.3"
    assert seen["url"] == "https://api.meta.ai/v1/chat/completions"
    assert seen["auth"] == "Bearer LLM|123|secret"
    b = seen["body"]
    assert b["model"] == "muse-spark-1.3"
    assert b["messages"][0]["role"] == "developer"
    assert b["response_format"]["type"] == "json_schema"
    assert "temperature" not in b
    # The prompt carries the summary but no raw identifiers.
    assert "instagram" not in b["messages"][1]["content"].lower()


def test_live_plan_upstream_error_is_502(client, summary, monkeypatch):
    _live(monkeypatch, lambda req: httpx.Response(429, json={"error": {"message": "rate limited"}}))
    r = client.post("/v1/plan", json=summary)
    assert r.status_code == 502 and "429" in r.json()["detail"]


def test_live_plan_bad_model_output_is_502(client, summary, monkeypatch):
    _live(monkeypatch, lambda req: httpx.Response(200, json={"choices": [{"message": {"content": "not json"}}]}))
    assert client.post("/v1/plan", json=summary).status_code == 502


def test_checkin_mock(client):
    r = client.post("/v1/checkin", files={"audio": ("a.wav", wav_bytes(), "audio/wav")})
    assert r.status_code == 200
    j = r.json()
    assert j["source"] == "mock" and j["intent"]["planned_minutes"] == 90


def test_checkin_rejects_non_wav(client):
    r = client.post("/v1/checkin", files={"audio": ("a.mp3", b"ID3" + b"\x00" * 100, "audio/mpeg")})
    assert r.status_code == 400


def test_checkin_live_transcribe_then_intent(client, monkeypatch):
    calls = []

    def handler(req: httpx.Request):
        calls.append(req.url.path)
        if req.url.path.endswith("/asr/transcribe"):
            assert b"muse-voice-transcribe-1.0" in req.content and b"RIFF" in req.content
            assert req.headers["accept"] == "application/json"
            return httpx.Response(200, json={"sessionId": "s1", "transcript": "Doing OS scheduling for 45 minutes",
                                             "audioDurationMs": 2000, "turns": []})
        intent = {"subject": "OS", "planned_minutes": 45, "blockers": [], "summary": "OS for 45 min."}
        return httpx.Response(200, json={"choices": [{"message": {"content": json.dumps(intent)}}]})

    _live(monkeypatch, handler)
    r = client.post("/v1/checkin", files={"audio": ("a.wav", wav_bytes(), "audio/wav")})
    assert r.status_code == 200, r.text
    assert calls == ["/v1/asr/transcribe", "/v1/chat/completions"]
    assert r.json()["intent"]["subject"] == "OS"
    assert r.json()["source"] == "muse-voice-transcribe-1.0+muse-spark-1.3"


def test_checkin_live_intent_failure_falls_back_to_heuristic(client, monkeypatch):
    def handler(req):
        if req.url.path.endswith("/asr/transcribe"):
            return httpx.Response(200, json={"transcript": "doing maths for 30 minutes", "sessionId": "x",
                                             "audioDurationMs": 1, "turns": []})
        return httpx.Response(500, text="boom")

    _live(monkeypatch, handler)
    j = client.post("/v1/checkin", files={"audio": ("a.wav", wav_bytes(), "audio/wav")}).json()
    assert j["intent"]["planned_minutes"] == 30 and j["source"].endswith("+heuristic")


def test_android_generated_summary_passes_contract(client):
    """Fixture emitted by the Android unit test CoreTest.emitsContractFixture (SummaryBuilder)."""
    import pathlib
    payload = json.loads((pathlib.Path(__file__).parent / "fixtures" / "android_summary.json").read_text())
    r = client.post("/v1/plan", json=payload)
    assert r.status_code == 200, r.text
    assert r.json()["source"] == "mock"
