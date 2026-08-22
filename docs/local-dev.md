# Running BooKI locally

Practical guide: what runs on which port, the different ways to start each piece, and how to check/stop them. For architecture, see [architecture.md](architecture.md); this is just the "how do I turn it on/off" flow.

## Port map

| Port | What it is | Needed for... |
|---|---|---|
| `5173` | Frontend (Vite dev server) | Viewing the app in the browser |
| `8080` | Real backend (Spring Boot) | The frontend actually working (login, uploading PDFs, AI) |
| `3001` | Mock backend (Node/Express) | Testing the frontend WITHOUT Java/DB/API keys |
| `3306` | MySQL (if using Docker) | Only needed if the backend runs with the `dev` profile (not needed with `local`) |

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

Uses H2 in a file (`~/booki-local-db`), doesn't need MySQL running.

**b) `dev` profile (requires MySQL via Docker):**

```bash
docker compose up -d      # starts MySQL on port 3306
cd backend
./gradlew bootRun
```

In both cases:
- Keep the terminal open — that's where the logs show up (with `show-sql: true`, you'll even see the SQL for every request).
- Stop it: `Ctrl+C` in that terminal.

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

## Checking what's running right now

```bash
lsof -i:5173   # frontend
lsof -i:8080   # real backend
lsof -i:3001   # mock backend
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
