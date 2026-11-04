# Running Muse Glimmer on your own laptop for FocusGuard

FocusGuard's hybrid classifier sends only the *ambiguous* app sessions (e.g. YouTube during a study
block) to Meta's open-weight **Muse Glimmer** model, running on hardware you own. The phone talks to it
over your local Wi-Fi with the standard OpenAI chat-completions format, so any of the runtimes Meta
documents works.

Source: Meta's Muse Glimmer docs, https://dev.meta.ai/docs/muse-glimmer and
https://dev.meta.ai/docs/muse-glimmer/deploy (checked Nov 2026).

## Hardware reality check

- Muse Glimmer is a **dense 30B** model. bf16 weights are ~60 GB; the published quantized GGUF builds are
  **17–20 GB**. LM Studio reports Q4_K_M at ~24 GiB with a 16K context.
- You need a laptop/desktop with roughly 24 GB+ free RAM (CPU, slow) or a GPU / Apple silicon with enough
  memory. It will not run on a phone, which is why FocusGuard keeps a rule engine on the phone and treats
  Glimmer as an optional "own-hardware" tier.
- If you don't have such a machine, leave the Glimmer switch off: FocusGuard works fully on rules.

## Option A: llama.cpp (recommended, needs build b10353 or newer)

```bash
curl -LsSf https://llama.app/install.sh | sh          # Linux; macOS/Windows: the Llama app

llama serve -hf meta-models/Muse-Glimmer-30B-GGUF \
  --hf-file Muse-Glimmer-30B-KQuant-17GB-Q4_K_M.gguf \
  -a muse-glimmer \
  -ngl 99 -c 32768 -np 1 --no-mmproj \
  --host 0.0.0.0 --port 8080 \
  --chat-template-kwargs '{"reasoning_strength":"low"}'
# optional: --api-key <secret>   (then put the same secret in the app's "Local server API key")
```

- `-a muse-glimmer` makes the server answer to `"model": "muse-glimmer"` (FocusGuard's default).
- `--host 0.0.0.0` lets the phone on the same Wi-Fi reach it. Keep the laptop firewall limited to your LAN.
- `reasoning_strength: low` keeps Glimmer's thinking short; FocusGuard also sends it per request.
- Smoke test from the laptop:
  ```bash
  curl -s http://127.0.0.1:8080/v1/chat/completions -H "Content-Type: application/json" \
    -d '{"model":"muse-glimmer","messages":[{"role":"user","content":"Reply with the word ok"}]}'
  ```

## Option B: Ollama (Ollama 0.32.7+)

```bash
ollama run muse-glimmer          # Apple silicon: ollama run muse-glimmer:30b-mlx
OLLAMA_HOST=0.0.0.0 ollama serve # expose on LAN
```
App settings: URL `http://<laptop-ip>:11434/v1`, model `muse-glimmer`.

## Option C: LM Studio

`lms server start` (port 1234), then use `http://<laptop-ip>:1234/v1` and the model id from `GET /v1/models`.

## Connect the app

Settings → *Muse Glimmer judge* → on → base URL `http://<laptop-LAN-IP>:8080/v1` → *Test Glimmer connection*
(should print the `/v1/models` response) → go to Today → Refresh. Sessions Glimmer judged show
`source: muse-glimmer` and a `Glimmer: …` reason.

The app refuses public hosts (anything outside 10/8, 172.16/12, 192.168/16, 127/8, 100.64/10, `localhost`,
`*.local`) unless you explicitly switch on "Allow non-local host". That switch exists only for people who
knowingly run Glimmer on their own cloud box; a hosted provider would defeat the privacy design.

## What Glimmer receives

Only the ambiguous sessions, each as: app label, coarse category, minutes, HH:MM start, whether it was
inside a study block, and the labels of the apps just before and after. No package names, no full history.
