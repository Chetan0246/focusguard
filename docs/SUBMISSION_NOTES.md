# Submission notes

## Short description (one line)
FocusGuard is an Android focus coach that senses how a student uses their phone during study blocks, judges ambiguous sessions with Meta's open-weight Muse Glimmer on the student's own laptop, nudges in the moment, and asks Muse Spark to plan tomorrow from an anonymized summary, so raw usage never leaves the student's own devices.

## Tech stack
- **Android**: Kotlin 2.0, Jetpack Compose (Material 3), `UsageStatsManager`, WorkManager (15-min nudge checks), `AudioRecord` → WAV, `HttpURLConnection` + `org.json` (no extra network libraries). minSdk 26, targetSdk 35, AGP 8.5.2.
- **On-device logic**: pure-Kotlin core (`RuleClassifier`, `HybridClassifier`, `SummaryBuilder`, `NudgePolicy`, `NetGuard`, timetable parser) with JVM unit tests.
- **Meta models**: Muse Glimmer (open weights, served locally via llama.cpp / Ollama / LM Studio, OpenAI-compatible); Muse Spark `muse-spark-1.3` (Meta Model API, chat completions + JSON-schema structured output); Muse Voice Transcribe `muse-voice-transcribe-1.0` (`/v1/asr/transcribe`).
- **Relay**: Python 3.12, FastAPI, Pydantic v2 (`extra="forbid"` privacy contract), httpx, pytest; offline mock mode.

## Challenges
- **The model I wanted on the phone doesn't fit on a phone.** Muse Glimmer is a dense 30B model (17–20 GB quantized). Instead of faking "on-device AI", I split the work into three tiers: deterministic rules on the phone (always on, offline), Glimmer on hardware the student owns for the hard cases, and Muse Spark only for aggregate planning. The `HybridClassifier` sends only low-confidence sessions to Glimmer, which cuts the load and the data exposure.
- **Proving privacy rather than claiming it.** The relay's schema rejects anything but aggregates (422), the phone scrubs package-name-like strings, `NetGuard` blocks public hosts for the Glimmer tier, and the app shows the exact outgoing JSON before sending.
- **Ambiguity is the real problem.** YouTube and browsers are both study tools and distractions. Context (neighbouring apps, time, study block) is what disambiguates them, which is exactly what a language model is good at and rules are bad at.
- **Usage events are messy.** RESUMED/PAUSED pairs, screen-off events, launchers and very short glances all need handling before any classification is meaningful.
- **Reasoning-model latency.** Muse Spark and Glimmer both reason before answering; the app uses `reasoning_effort: low` / `reasoning_strength: low`, long timeouts, and never blocks the nudge path on a network call.

## What's next
- Learn per-student category overrides from one-tap corrections ("this was a lecture") and feed them back as few-shot examples to Glimmer.
- Run a small distilled classifier fully on the phone (ExecuTorch) trained on Glimmer's labels, so the private tier works without a laptop.
- Focus-mode actions: when a nudge is ignored twice, offer to open the planned study app or start a timer.
- Weekly trend view and exam-countdown widget; Calendar import for the timetable.
- Deploy the relay with HTTPS + per-device tokens.

## Build and verification status
See `docs/BUILD_STATUS.md`: backend 18 tests pass, Android 11 unit tests pass and the debug APK assembles. Not yet verified: live Meta API calls with a real key, a real Muse Glimmer server, and the app on a device.
