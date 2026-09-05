# BooKI

A cloud-based conversational reading assistant: PDF reading in page-range
sessions, with a context-aware AI you talk to by text or voice — and that can
quiz you, summarize, or explain a passage without leaving the conversation.
One responsive web / PWA app for Android, Windows and Linux.

## Monorepo structure

```
booki/
├── backend/          # Spring Boot 4.1 + Java 21 + Gradle
├── frontend/         # React + TypeScript + Vite + PWA
├── docs/             # Vision, architecture, and agent memory
├── docker-compose.yml
├── .env.example
└── README.md
```

> Practical guide to ports, ways to start each server, and how to stop them: [docs/local-dev.md](docs/local-dev.md).

## Requirements

- Java 21
- Gradle (wrapper included at `backend/gradlew`)
- Node.js 20+ and npm (for the frontend)
- Docker and Docker Compose **optional** (for PostgreSQL; you can also use H2)
- An AI provider API key for the assistant (Anthropic by default; OpenAI / Kimi / local Ollama also supported)
- Optional: an OpenAI key for cloud voice (STT/TTS) — without it, voice falls back to the browser recognizer (Chromium only)

## Quick start with H2 (no Docker)

### 1. Backend

```bash
cd backend
./gradlew bootRunLocal
```

Uses the `local` profile with file-based H2 (`~/booki-local-db`). The backend listens on `http://localhost:8080`.

### 2. Frontend

```bash
cd frontend
npm install
npm run dev
```

The frontend listens on `http://localhost:5173`.

## Starting with PostgreSQL + Docker

```bash
cp .env.example .env
docker compose up -d      # PostgreSQL 16 (:5432); also MinIO (:9000) for optional S3 testing
cd backend && ./gradlew bootRun
cd frontend && npm run dev
```

This is the `dev` profile — the same database engine as the deployed
environment. See [docs/deployment.md](docs/deployment.md).

## Test flow

1. Open `http://localhost:5173`.
2. Sign up or log in from the login page.
3. Upload a PDF from the home screen.
4. Click a book to create a session (choose page range, difficulty, and an AI Profile).
5. Open the session and chat with BooKI by text or voice.

## AI configuration

Edit `.env` (at the repo root) or set environment variables. All four providers
— `claude`, `openai`, `kimi`, `ollama` — are always available; a session picks
one at creation, falling back to `AI_PROVIDER` (default `openai`, or `ollama` on
the `local` profile).

```bash
AI_PROVIDER=openai
OPENAI_API_KEY=sk-proj-...
OPENAI_MODEL=gpt-4o-mini        # optional; a stronger model (gpt-4o / newer) is better for the assistant
# other providers: ANTHROPIC_API_KEY=sk-ant-...  /  KIMI_API_KEY=...  /  (ollama needs no key)
```

The **same `OPENAI_API_KEY`** also powers cloud voice (backend STT/TTS) — one
key covers chat and voice. Model and voice options: see
[docs/ai-voice.md](docs/ai-voice.md).

## Agent memory profiles

- `docs/vision.md` — product vision and principles.
- `docs/architecture.md` — stack and structure.
- `docs/backend.md` — backend details.
- `docs/frontend.md` — frontend details.
- `docs/prompts.md` — prompts, AI Profiles, and user context.
- `docs/ai-voice.md` — AI and voice strategy.
- `docs/decisions.md` — architecture decisions.
- `docs/agent-memory.md` — compact summary.
- `docs/local-dev.md` — how to run/stop each server locally.
- `docs/deployment.md` — deployment plan (DB migration, storage, hosting, CI/CD).

## Status

Core product in place: authentication, PDF library and per-page extraction,
page-range sessions, AI Profiles (the per-session prompt set — `docs/prompts.md`),
the unified conversation engine (text + voice + capabilities), per-session AI
provider, quiz, progress, reports, and cloud STT/TTS. Voice streaming (incremental STT/TTS) is architected but not
wired — see [docs/ai-voice.md](docs/ai-voice.md) "Streaming".
