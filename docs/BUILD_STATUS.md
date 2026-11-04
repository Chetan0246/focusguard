# Build and verification status

Recorded Nov 4 2026 (IST), when the project was packaged.

| Check | Result | How |
|---|---|---|
| Backend tests | **18 passed** | `cd backend && pytest -q` (Python 3.12, FastAPI 0.162, Pydantic 2.14, httpx 0.28) |
| Relay smoke test | `/health` → `mode: mock`; summary with raw `sessions` → **422**; Android-generated summary → 200 plan | `uvicorn app.main:app` + curl |
| Meta Model API reachability | `POST https://api.meta.ai/v1/chat/completions` without a key → `401 {"error":{"code":"invalid_api_key","type":"authentication_error"}}` | curl. Confirms the endpoint and the OpenAI-style error envelope, **not** a successful generation |
| Android unit tests | **11 passed** (`CoreTest`: timetable, rules, categories, hybrid + fallback, summary privacy/contract, nudge policy, NetGuard, Glimmer protocol, plan JSON, contract fixture) | `gradle testDebugUnitTest` |
| Android debug APK | **assembled** (`app-debug.apk`, minSdk 26, targetSdk 35) | `gradle assembleDebug` with AGP 8.5.2, Kotlin 2.0.20, Gradle 8.9, JDK 17 |
| Cross-language contract | Summary JSON emitted by the Kotlin `SummaryBuilder` is accepted by the relay's Pydantic schema | `backend/tests/fixtures/android_summary.json` + `test_android_generated_summary_passes_contract` |

Build-environment note: the sandbox was Linux aarch64, so the build used a community aarch64 build of `aapt2`
(`android.aapt2FromMavenOverride`, set in the sandbox's user-level gradle.properties, **not** in this repo).
Android Studio on Windows/macOS/x86 Linux uses Google's own aapt2 and needs nothing extra.

## Not verified (do these before recording)

- A successful live Muse Spark plan and Muse Voice transcription with a real `META_API_KEY`.
- The hybrid classifier against a real Muse Glimmer server (llama.cpp / Ollama).
- The app running on a device or emulator: Usage Access flow, notifications, microphone, UI layout.
- The GitHub Actions workflow (`.github/workflows/ci.yml`) has not been run.
