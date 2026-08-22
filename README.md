# BooKI

A PDF reader with page-range sessions and a contextual learning/discussion assistant, controllable by text and voice.

## Monorepo structure

```
booki/
├── backend/          # Spring Boot 3.3 + Java 21 + Gradle
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
- Docker and Docker Compose **optional** (for MySQL; you can also use H2)
- An OpenAI (or Kimi) API key for the assistant

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

## Starting with MySQL + Docker

```bash
cp .env.example .env
docker compose up -d
cd backend && ./gradlew bootRun
cd frontend && npm run dev
```

## Test flow

1. Open `http://localhost:5173`.
2. Sign up or log in from the login page.
3. Upload a PDF from the home screen.
4. Click a book to create a session (choose page range, difficulty, and a Profile Master).
5. Open the session and chat with BooKI by text or voice.

## AI configuration

Edit `.env` or set environment variables:

```bash
AI_PROVIDER=openai
OPENAI_API_KEY=sk-...
```

To use Kimi:

```bash
AI_PROVIDER=kimi
KIMI_API_KEY=...
```

Note: as of now, only the OpenAI provider is actually implemented (`OpenAiProvider`) — `AI_PROVIDER=kimi` will fail to start until a `KimiProvider` is added.

## Agent memory profiles

- `docs/vision.md` — product vision and principles.
- `docs/architecture.md` — stack and structure.
- `docs/backend.md` — backend details.
- `docs/frontend.md` — frontend details.
- `docs/ai-voice.md` — AI and voice strategy.
- `docs/decisions.md` — architecture decisions.
- `docs/agent-memory.md` — compact summary.
- `docs/local-dev.md` — how to run/stop each server locally.

## MVP status

Base structure ready to iterate on. Immediate pending items:

- [x] UI to create a session by selecting a page range.
- [x] Improve reader and chat UX.
- [x] Login/register UI in the frontend.
- [ ] Integrate TTS with the Web Speech API.
- [ ] Integration tests with H2.
