# Running BooKI locally

Practical guide: what runs on which port, the different ways to start each piece, and how to check/stop them. For architecture, see [architecture.md](architecture.md); this is just the "how do I turn it on/off" flow.

## API keys: put them in `.env`, not `export`

`cp .env.example .env` at the **repo root**, fill in real values (e.g. `ANTHROPIC_API_KEY`), save. `backend/build.gradle`'s `bootRun`/`bootRunLocal` tasks read that file automatically and inject each value as an environment variable — no more "which terminal did I `export` it in" — it works the same regardless of which terminal runs `./gradlew`. `.env` is gitignored, so this never gets committed. See `docs/backend.md`'s AI configuration section for the full variable list.

## Port map

| Port | What it is | Needed for... |
|---|---|---|
| `5173` | Frontend (Vite dev server) | Viewing the app in the browser |
| `8080` | Real backend (Spring Boot) | The frontend actually working (login, uploading PDFs, AI) |
| `3001` | Mock backend (Node/Express) | Testing the frontend WITHOUT Java/DB/API keys |
| `3306` | MySQL (if using Docker) | Only needed if the backend runs with the `dev` profile (not needed with `local`) |
| `11434` | Ollama daemon (if installed) | Only needed if a session's `aiProvider` is `ollama` — see §4 below |

**Important:** the frontend always requests `/api/...` on its own port (5173), and Vite forwards (proxies) that to `http://localhost:8080` — that's set in `frontend/vite.config.ts`. This means it can **only talk to ONE backend at a time** (the real one, on 8080). The mock on 3001 is a completely separate server, only useful if you change the proxy or hit `localhost:3001` directly with `curl`/Postman.

## 1. Frontend — `npm run dev`

```bash
cd frontend
npm run dev
```

- Keep this terminal open: it shows compile errors/HMR output.
- Opens at `http://localhost:5173` — **always use `localhost`, not `127.0.0.1`** (the backend only allows CORS from `http://localhost:5173`, see `backend/src/main/java/com/booki/config/SecurityConfig.java`; `127.0.0.1` gets a 403 "Invalid CORS request").
- Stop it: `Ctrl+C` in that terminal.

## 2. Real backend — two ways (Gradle)

**a) `local` profile (recommended for everyday dev, no Docker needed):**

```bash
cd backend
./gradlew bootRunLocal
```

Uses H2 in a file (`~/booki-local-db`), doesn't need MySQL running. A session that doesn't explicitly pick an AI model defaults to **Ollama** here (see §4 below) — free, but needs Ollama actually installed and running, or chat/quiz/summary just get the offline fallback message.

**b) `dev` profile (requires MySQL via Docker):**

```bash
docker compose up -d      # starts MySQL on port 3306
cd backend
./gradlew bootRun
```

A session that doesn't explicitly pick an AI model defaults to **Claude** here — needs `ANTHROPIC_API_KEY` set, or same offline fallback as above.

Requires Docker installed and your user in the `docker` group (`sudo usermod -aG docker $USER`, then log out/in or `newgrp docker` for just the current shell) so `docker`/`docker compose` work without `sudo`.

Wait for MySQL to actually be ready before starting the backend — `docker compose ps` should show `booki-mysql` as `(healthy)`, not just `Up`. On a slow disk the very first startup (creating the data volume) can take a few minutes instead of the usual ~20s; if it gets interrupted mid-init you can end up with a half-initialized database (the `booki` user missing, `root` with an empty password instead of the configured one). The data lives in a Docker **volume**, which survives container restarts — so a plain restart won't fix a half-initialized one; you need to wipe the volume and let it redo the whole init:
```bash
docker compose down -v     # -v also removes the volume — nukes all MySQL data
docker compose up -d
```

In both cases:
- Keep the terminal open — that's where the logs show up. `local` (H2) has `show-sql: true`, so you'll see every SQL statement; `dev` (MySQL) deliberately doesn't, to keep the log readable once you're not debugging queries directly.
- Stop it: `Ctrl+C` in that terminal.
- Stop MySQL when you're done: `docker compose down` (no `-v`, so your data is still there next time).

**c) From VS Code (Run/Debug button on `BookiApplication`):**

Works, but the output does NOT go to your terminal — it goes to a new tab inside VS Code's **TERMINAL** panel (check the terminal dropdown) or to the **DEBUG CONSOLE** if you launched it in debug mode. For day-to-day dev, option (a) via terminal is simpler because you always know where to look.

## 3. Mock backend (optional, no Java needed)

```bash
cd mock-backend
node src/index.js
```

- Listens on `http://localhost:3001`.
- Comes with a preloaded demo user: `demo@booki.app` / `password`.
- Only useful if you point something directly at `localhost:3001` (the frontend's proxy doesn't use it by default).

## 4. Ollama (optional, local AI provider)

Only needed if you want a session's `aiProvider` set to `ollama` to actually work — see `docs/backend.md`'s AI configuration section for how provider selection works. Everything else in BooKI runs fine without ever touching this section.

### Install (Linux, any distro/arch — the script detects it)

```bash
curl -fsSL https://ollama.com/install.sh | sh
```

This downloads a shell script and pipes it straight into `sh` to run — it in turn downloads the real Ollama binary for your CPU/GPU and registers it as a **systemd service**. That matters: unlike `bootRunLocal` or `npm run dev`, you don't "launch" Ollama each time — it's installed once and then always running in the background, like MySQL.

### Managing the service

```bash
systemctl status ollama     # is it running?
sudo systemctl stop ollama    # stop it
sudo systemctl start ollama   # start it
sudo systemctl restart ollama # restart it (forcibly unloads any stuck/loaded model)
journalctl -u ollama --no-pager -n 50   # recent logs — check here for GPU/ROCm detection at startup
```

### Managing models

A "model" (e.g. `llama3.1`, `llama3.2:1b`) is a separate download from the Ollama binary itself — `booki.ai.ollama.model` in `application.yml` (env var `OLLAMA_MODEL`) picks which one BooKI actually calls; nothing pulls it automatically.

```bash
ollama pull llama3.2:1b    # download a model (only needs to be done once; cached on disk)
ollama list                # see what's downloaded, and each one's disk size
ollama rm llama3.1         # delete a model from disk to free space
ollama stop llama3.2:1b    # force-unload a model from RAM right now
```

`ollama stop` matters more than it sounds: Ollama keeps a model loaded in RAM for `OLLAMA_KEEP_ALIVE` (default 5 minutes) after the last request, so it responds fast to a follow-up question — but that means the RAM isn't released automatically the instant a request finishes. If your system is under memory pressure, don't wait out the 5 minutes — run `ollama stop <model>` to free it immediately.

### Hardware reality check — CPU vs. GPU inference

Run `journalctl -u ollama --no-pager -n 200 | grep -i "rocm\|gpu"` right after the service starts to see what Ollama actually detected. On this dev machine it found an integrated AMD Vega 3 GPU (`gfx902`) but **dropped it** — ROCm only supports `gfx1030` and newer (or specific datacenter chips), so `gfx902` falls back to pure CPU inference, and Ollama also disables integrated GPUs by default anyway (`OLLAMA_IGPU_ENABLE=1` would attempt it via Vulkan, likely still slow on an iGPU this old).

**Concretely what that costs you**: a single chat/quiz/summary request against the 8B `llama3.1` model, running on CPU alone, took **32 minutes of accumulated CPU time**, pushed system RAM usage to 93% and swap to 100% (`free -h` showed 174Mi free out of 14Gi), with real risk of the OOM killer stepping in or the whole desktop freezing. That's why `booki.ai.ollama.model` defaults to the much smaller **`llama3.2:1b`** (1.3GB vs. 4.9GB) instead of `llama3.1` — feasible on CPU-only hardware. If you have a real ROCm/CUDA-compatible GPU, bump `OLLAMA_MODEL` back up to something bigger.

If your system ever gets into that state (swap full, everything crawling), the fastest way out:
```bash
ollama stop <model>          # or, if that hangs too:
sudo systemctl restart ollama
free -h                      # confirm memory recovered
```

## Checking what's running right now

```bash
lsof -i:5173   # frontend
lsof -i:8080   # real backend
lsof -i:3001   # mock backend
docker compose ps   # MySQL container + its health status
```

If the command returns nothing, that port is free (nothing running there).

## Stopping something you didn't start in a visible terminal

If something got left running in the background (or you launched it from VS Code and can't find the Stop button):

```bash
lsof -i:8080          # note the PID (second column)
kill <PID>            # e.g. kill 187008
lsof -i:8080          # confirm nothing shows up anymore
```

Same pattern for any other port (`5173`, `3001`).

## Typical full flow (real backend, no Docker)

```bash
# Terminal 1
cd backend && ./gradlew bootRunLocal

# Terminal 2
cd frontend && npm run dev
```

Open `http://localhost:5173`, register a user, and watch Terminal 1 — you should see the request and the SQL happening there in real time.
