# FocusGuard: 3-minute demo video script

Target length 2:50–3:00. Record the phone/emulator screen (Android Studio's emulator recorder, or `scrcpy --record demo.mp4` for a real phone) plus a terminal window. Voice-over in your own words; the lines below are a guide.

**Before recording**
- Relay running in **live** mode: `curl localhost:8000/health` shows `"mode":"live"`. (If you only have mock mode, say "mock mode" out loud and keep the `source: mock` label visible. Don't present mock output as Muse Spark.)
- If you have a machine that can run Muse Glimmer, start `llama serve … -a muse-glimmer` and test it in Settings. If not, skip shot 4 and say Glimmer is optional.
- App in demo-data mode on an emulator (timetable `Mon-Fri 18:00-20:00 DSA`, exams in 9 and 16 days), or real data on your phone.
- Clear the cached plan: Settings → toggle demo data off/on, or reinstall.
- Notifications and microphone already allowed. Do-not-disturb off.

| Time | Shot (screen) | Voice-over |
|---|---|---|
| 0:00–0:15 | Face cam or title card "FocusGuard: a focus coach that lives on your phone", then a quick shot of a phone with Instagram open over a PDF | "I'm Chetan, a third-year IT student. My world is a timetable, two exams in the next two weeks, and a phone that turns one YouTube lecture into an hour of Shorts. Screen-time apps tell me after the damage is done. I built FocusGuard to step in during the study block." |
| 0:15–0:35 | Onboarding screen → Usage Access → "How your data is handled" card | "FocusGuard reads which apps I use with Android's Usage Access permission. The raw usage stays on the phone. That's the core design rule." |
| 0:35–1:05 | **Today** tab: focused / distracted / neutral minutes, ratio bar, scroll session list showing labels, reasons and `source: rules` | "Every session is labelled focused, distracted or neutral. An on-device rule engine handles the obvious ones instantly and offline: Classroom is focus, Clash Royale in a study block is not. But some it can't decide: is this YouTube at 6:26 a lecture or a rabbit hole?" (pause on the YouTube row, confidence-low reason "could be a lecture") |
| 1:05–1:30 | Settings → Muse Glimmer on, URL of laptop → Test connection OK → back to Today → Refresh → YouTube row now `focused · Glimmer: lecture between PDF and notes · muse-glimmer`. Optional: split screen with the llama.cpp server log | "Those ambiguous sessions go to Muse Glimmer, Meta's open-weight model, running on my own laptop over Wi-Fi, not the cloud. It sees the context: YouTube sandwiched between my PDF reader and Google Docs during a DSA block. It calls it a lecture. The app refuses to send this to any public server." |
| 1:30–1:50 | Tap **Check nudge now** → notification slides in "DSA: 35 min off-task…" | "During a study block, a background check runs every 15 minutes. Once distraction crosses my threshold, I get a nudge, and it works with no internet at all." |
| 1:50–2:10 | Today → **Record** voice check-in: say "I'm doing DBMS normalization for ninety minutes, but I keep checking Instagram." → transcript + "DBMS, 90 min, blockers: …" + `source: muse-voice-transcribe-1.0+muse-spark-1.3` | "I can also check in by voice. Muse Voice Transcribe turns it into text and Muse Spark pulls out the subject, the time and what's blocking me." |
| 2:10–2:40 | **Plan** tab → tap **What leaves my phone?** (scroll the green JSON: minutes per category, hourly histogram, exams, no app names) → **Generate plan** → plan card: focus score, blocks, exam strategy, nudges, `source: muse-spark-1.3` | "For tomorrow's plan, only this anonymized summary leaves the phone. Minutes per category and an hourly histogram, no app names. Muse Spark on the Meta Model API returns a structured plan: DBMS first because the exam is nine days out, deep work scheduled away from my 9 PM slump, and nudges written for my pattern." |
| 2:40–2:52 | Terminal: run the 422 curl (below) → `422 Unprocessable Entity` | "And it's enforced, not just promised: if anything tries to send raw sessions, the relay rejects it before it reaches Meta." |
| 2:52–3:00 | Architecture diagram from README, GitHub URL on screen | "On-device rules, Muse Glimmer on my own hardware, Muse Spark only for aggregates. FocusGuard. Code is open source on GitHub." |

**422 curl for shot 2:40**

```bash
curl -s -o /dev/null -w "%{http_code}\n" -X POST localhost:8000/v1/plan \
  -H "Content-Type: application/json" \
  -d '{"weekday":"Wed","focused_min":10,"distracted_min":5,"neutral_min":0,
       "sessions":[{"package":"com.instagram.android","start":1,"end":2}]}'
# -> 422
```

**Recording tips**
- Make the font big: Emulator → Settings → Display → font size large.
- Keep each tap on screen for at least 1 s; cut dead time while Muse Spark thinks (it's a reasoning model; plans can take 10–40 s).
- Show `source:` labels on camera: they prove which model produced what.
- Upload unlisted to YouTube (or as the form requires) and put the link in the README.
