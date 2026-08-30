# Running BooKI locally

Practical guide: what runs on which port, the different ways to start each piece, and how to check/stop them. For architecture, see [architecture.md](architecture.md); this is just the "how do I turn it on/off" flow.

## API keys: put them in `.env`, not `export`

`cp .env.example .env` at the **repo root**, fill in real values (e.g. `ANTHROPIC_API_KEY`), save. 
`backend/build.gradle`'s `bootRun`/`bootRunLocal` tasks read that file automatically 
`export` it in" — it works the same regardless of which terminal runs `./gradlew`. 
`.env` is gitignored, so this never gets committed. See 
`docs/backend.md`'s AI configuration section for the full variable list.

## Port map

| Port | What it is | Needed for... |
|---|---|---|
| `5173` | Frontend (Vite dev server) | Viewing the app in the browser |
| `8080` | Real backend (Spring Boot) | The frontend actually working (login, uploading PDFs, AI) |
| `3001` | Mock backend (Node/Express) | Testing the frontend WITHOUT Java/DB/API keys |
| `5432` | PostgreSQL (if using Docker) | Only needed if the backend runs with the `dev` profile (not needed with `local`) |
| `9000` / `9001` | MinIO — S3 API / web console (if using Docker) | Only needed to test `STORAGE_DRIVER=s3` locally (default is `local` = disk) — see §5 |
| `11434`| Ollama daemon (if installed) | Only needed if a session's `aiProvider` is `ollama` — see §4 below |

**Important:** the frontend always requests `/api/...` on its own port (5173), and Vite forwards (proxies) that to `http://localhost:8080` — that's set in `frontend/vite.config.ts`. This means it can **only talk to ONE backend at a time** (the real one, on 8080). The mock on 3001 is a completely separate server, only useful if you change the proxy or hit `localhost:3001` directly with `curl`/Postman.

## 1. Frontend — `npm run dev`

```bash
cd frontend
npm run dev
```

- Keep this terminal open: it shows compile errors/HMR output.
- Opens at `https://localhost:5173` if `frontend/.certs/*.pem` exist (see "HTTPS for
  mobile testing" below), otherwise plain `http://localhost:5173` — **always use
  `localhost`, not `127.0.0.1`** (the backend only allows CORS from whatever's in
  `CORS_ALLOWED_ORIGINS`, see `backend/src/main/java/com/booki/config/SecurityConfig.java`;
  `127.0.0.1` isn't in that list by default, so it gets a 403 "Invalid CORS request").
- Stop it: `Ctrl+C` in that terminal.

### HTTPS for mobile testing

`getUserMedia` (the mic, used by voice) only works in a **secure context**:
`https://`, or the special `http://localhost` exception. That exception does
**not** cover a phone hitting your machine's LAN IP over plain `http://` — so
without HTTPS, voice silently looks unsupported on a phone (no permission
prompt at all, not even a denial) even though it works fine on your own
machine's `localhost`.

`frontend/vite.config.ts` turns on HTTPS automatically **only if**
`frontend/.certs/dev-key.pem` and `dev-cert.pem` exist — no certs, no change in
behavior (plain `http://localhost:5173`, as before). To generate them (self-signed,
gitignored, one-time per machine — replace `192.168.2.22` with your own LAN IP,
`ip addr` or your router's device list will show it):

```bash
mkdir -p frontend/.certs && cd frontend/.certs
cat > openssl-san.cnf <<'EOF'
[req]
distinguished_name = req_distinguished_name
x509_extensions = v3_req
prompt = no
[req_distinguished_name]
CN = booki-local-dev
[v3_req]
keyUsage = keyEncipherment, digitalSignature
extendedKeyUsage = serverAuth
subjectAltName = @alt_names
[alt_names]
DNS.1 = localhost
IP.1 = 127.0.0.1
IP.2 = 192.168.2.22
EOF
openssl req -x509 -nodes -newkey rsa:2048 -keyout dev-key.pem -out dev-cert.pem -days 825 -config openssl-san.cnf
```

Once HTTPS is on, the **whole** dev server is TLS-only (no more plain `http://`
on that port), so:
- Update `.env`'s `CORS_ALLOWED_ORIGINS` to `https://` origins:
  `CORS_ALLOWED_ORIGINS=https://localhost:5173,https://<your-LAN-IP>:5173`.
- On your phone, open `https://<your-LAN-IP>:5173` — Chrome/Firefox will warn
  "connection not private" (expected: it's a self-signed cert, not from a real
  CA). Tap **Advanced → Proceed** once; after that the mic prompt works
  normally.
- Restart both the backend (to re-read `.env`) and the frontend after touching
  either of these.

## 2. Real backend — two ways (Gradle)

**a) `local` profile (recommended for everyday dev, no Docker needed):**

```bash
cd backend
./gradlew bootRunLocal
```

Uses H2 in a file (`~/booki-local-db`), doesn't need PostgreSQL running. H2 runs
in **PostgreSQL compatibility mode** so the SQL it parses matches the real
database. A session that doesn't explicitly pick an AI model defaults to
**Ollama** here (see §4 below) — free, but needs Ollama actually installed and
running, or chat/quiz/summary just get the offline fallback message.

**b) `dev` profile (requires PostgreSQL via Docker):**

```bash
docker compose up -d      # starts PostgreSQL 16 on port 5432
cd backend
./gradlew bootRun
```

A session that doesn't explicitly pick an AI model defaults to **Claude** here — needs `ANTHROPIC_API_KEY` set, or same offline fallback as above.

Requires Docker installed and your user in the `docker` group (`sudo usermod -aG docker $USER`, then log out/in or `newgrp docker` for just the current shell) so `docker`/`docker compose` work without `sudo`.

Wait for the database to be ready before starting the backend — `docker compose ps` should show `booki-postgres` as `(healthy)`, not just `Up`. The data lives in a Docker **volume** (`postgres_data`) that survives container restarts. To wipe it and start from an empty database (Flyway will recreate the schema on the next `bootRun`):
```bash
docker compose down -v     # -v also removes the volume — nukes all Postgres data
docker compose up -d
```

In both cases:
- Keep the terminal open — that's where the logs show up. Every request logs one line (`http.request method=... path=... status=... userId=... durationMs=...`); raw SQL is off by default in both profiles (too noisy for day-to-day reading) — turn it on for one run with `LOGGING_LEVEL_ORG_HIBERNATE_SQL=debug ./gradlew bootRunLocal`, no file edits needed.
- Stop it: `Ctrl+C` in that terminal.
- Stop PostgreSQL when you're done: `docker compose down` (no `-v`, so your data is still there next time).

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

This downloads a shell script and pipes it straight into `sh` to run — it in turn downloads the real Ollama binary for your CPU/GPU and registers it as a **systemd service**. That matters: unlike `bootRunLocal` or `npm run dev`, you don't "launch" Ollama each time — it's installed once and then always running in the background, like a database service.

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

## 5. Object storage — testing the `s3` driver (optional)

By default BooKI stores uploaded PDFs and generated reports on **disk**
(`STORAGE_DRIVER=local`, under `backend/storage/`). Nothing else is needed for
normal dev.

To exercise the `s3` code path (the same `S3StorageAdapter` that runs in the
cloud against Google Cloud Storage / R2), use the **MinIO** service that
`docker compose up -d` already starts, plus its one-shot `minio-setup` that
creates the `booki` bucket:

```bash
docker compose up -d                       # db + minio + minio-setup
docker compose ps                          # wait for booki-minio (healthy)
```

Then run the backend with the S3 vars (copy them from `.env.example` into `.env`,
or export them for one run):

```bash
STORAGE_DRIVER=s3 \
S3_ENDPOINT=http://localhost:9000 \
S3_ACCESS_KEY=booki S3_SECRET_KEY=bookibooki \
S3_BUCKET=booki S3_PATH_STYLE=true S3_REGION=us-east-1 \
./gradlew bootRun
```

Uploads and reports now land in MinIO — browse them at the console
<http://localhost:9001> (login `booki` / `bookibooki`). Nothing is written to
`backend/storage/`. Drop the env vars (or set `STORAGE_DRIVER=local`) to go back
to disk.

## Checking what's running right now

```bash
lsof -i:5173   # frontend
lsof -i:8080   # real backend
lsof -i:3001   # mock backend
docker compose ps   # PostgreSQL container + its health status
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
