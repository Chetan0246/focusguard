# Draft answers for the submission form

Edit freely; keep it true to what you show in the demo. If you can't run Muse Glimmer for the video, cut the Glimmer sentences or say clearly it is an optional tier you couldn't demo live.

---

## Project Description

FocusGuard is an Android focus coach for students that works from behaviour, not chat. It reads which apps I actually use (Android Usage Access) and knows when I'm inside a study block from my timetable. Every app session is labelled focused, distracted or neutral. A rule engine on the phone handles the obvious cases instantly and offline. The genuinely ambiguous ones, like YouTube during a DSA block (lecture or rabbit hole?) or a long browser session, go to Meta's open-weight Muse Glimmer running on my own laptop over local Wi-Fi. Glimmer uses context, such as the apps just before and after, to decide.

When distraction inside a study block crosses my threshold, FocusGuard sends a nudge in the moment, and that works with no internet. Each evening it builds an anonymized summary (minutes per category, an hourly distraction histogram, timetable and exam countdowns, no per-app usage) and sends only that through a small relay to Muse Spark. Muse Spark returns a structured plan for tomorrow: time blocks ordered by exam urgency, scheduled away from my distraction peaks, a realistic focus goal, and nudge texts written for my pattern. I can also do a voice check-in ("DBMS for ninety minutes, but I keep checking Instagram"). Muse Voice Transcribe transcribes it and Muse Spark turns it into structured intent for the plan.

Privacy is enforced in code: raw sessions never leave devices I own, the relay rejects any payload that isn't aggregates (HTTP 422), the app blocks public hosts for the Glimmer tier, and a "What leaves my phone?" button shows the exact JSON before it is sent. Built for my world: a hostel room, a timetable and two exams in the next two weeks.

## Describe your use of Meta products

FocusGuard uses three Meta models, each where it fits best:

1. **Muse Glimmer (open weights)** is the private judge. It runs on the student's own laptop through llama.cpp, Ollama or LM Studio (OpenAI-compatible) and is called from the Android app over the local network. A `HybridClassifier` first labels sessions with on-device rules and sends only the low-confidence ones to Glimmer, with context: app label, category, minutes, time, whether it was in a study block, and the neighbouring apps. Glimmer returns JSON labels with short reasons, which appear in the UI tagged `muse-glimmer`. If Glimmer is off or unreachable, the app falls back to rules automatically.
2. **Muse Spark (`muse-spark-1.3`) on the Meta Model API** is the planner. A FastAPI relay (which holds the API key, so the key never ships in the app) forwards the anonymized daily summary to `/v1/chat/completions` with a `developer` instruction and a JSON-schema `response_format`, so the plan comes back as validated structured data: blocks, nudges, exam strategy, focus score and goal. The nudge texts Muse Spark writes are then used by the phone's offline nudge engine the next day. Muse Spark also extracts structured intent (subject, minutes, blockers) from voice check-ins.
3. **Muse Voice Transcribe (`muse-voice-transcribe-1.0`)** turns a short spoken check-in, recorded on the phone as 16 kHz mono WAV, into text via `/v1/asr/transcribe`.

The relay has an offline mock mode for development and labels every response with its `source`, so it is always visible which model produced what.

## Why are Meta's models important to your solution?

The hard part of focus tracking is ambiguity, and it is also the most private data. Whether a YouTube session is a lecture depends on context (what came before and after, the time, the timetable), which rules can't capture and a language model can. But that context is a minute-by-minute record of someone's life, so I didn't want it going to a cloud API. Muse Glimmer being open-weight is what makes the middle tier possible. The student can run a strong model on their own hardware and keep the raw data on devices they own. A closed API would force a choice between accuracy and privacy.

Muse Spark complements that. Once the data is reduced to aggregates, a strong reasoning model with reliable structured output can do the planning: weighing exam countdowns against distraction peaks and timetable gaps, and writing nudges specific to the student. JSON-schema output means the phone gets data it can schedule and notify from, not prose. Muse Voice Transcribe lets a student log intent hands-free, in seconds, which matters when the goal is less time on the phone.

Together they give a clean split that matches the privacy story: rules on the phone, open weights on your own hardware, and the hosted model only for anonymized summaries.

(Honest limitation to keep in mind: Muse Glimmer is 30B, so it runs on a laptop/desktop, not on the phone itself. The phone always works on its rule engine alone.)
