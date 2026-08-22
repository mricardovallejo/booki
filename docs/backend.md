# BooKI Backend

## Profiles

- `dev` (default): MySQL on localhost:3306.
- `local`: file-based H2 (`~/booki-local-db`) with `AUTO_SERVER=TRUE`. Ideal for development without Docker.
- `test`: in-memory H2, Flyway disabled.

### Running locally with H2

```bash
cd backend
./gradlew bootRunLocal
```

This starts the backend on `http://localhost:8080` without needing MySQL or Docker.

## Main entities

- `User`: email, password hash, display name, bio, and a free-text `systemPrompt` the reader can write about themselves (used to personalize BooKI's tone).
- `Document`: metadata for a PDF uploaded by the user (title, file path, page count).
- `DocumentPage`: text extracted per page of a document.
- `ProfileMaster`: an expert persona (name, short description, system prompt, `isActive` flag) selectable when creating a session. **Per-user, not global**: every account gets its own editable copy of the 4 built-in defaults, seeded from template rows (`user_id IS NULL`) at registration; editing or deleting one never affects any other user's copy. Deleting one clears (sets to `null`) the `profileMasterId` on any `Session`/`QuizAttempt` that referenced it — their history is kept, they just lose the persona tag.
- `Tag`: a per-user label a document can be filed under (many-to-many with `Document`); exposed via the `/api/collections` endpoints for historical reasons — see note below.
- `Session`: a page range (`startPage`/`endPage`) of a document, with `currentPage`, chosen `difficulty`, `language`, `aiProvider` (nullable — see AI configuration below), and an optional `ProfileMaster`.
- `Message`: one turn of conversation history in a session (`USER` / `BOOKI`, `TEXT` / `VOICE`).
- `QuizAttempt`: a generated quiz question for a page plus the reader's answer, correctness, score, and feedback.
- `SentReport`: a record of a progress/quiz report generated (and optionally emailed) for a session.

## REST API

All routes below are under `/api` and require a `Authorization: Bearer <jwt>` header unless noted otherwise.

### Auth — `/api/auth` (public)

| Method | Route | Description |
|--------|------|-------------|
| POST | `/api/auth/register` | Register with email/password, returns a JWT + the new user (`201`) |
| POST | `/api/auth/login` | Login, returns a JWT + the user |

Email is normalized (trimmed + lowercased) before lookup/storage on both routes, so `Name@Example.com` and `name@example.com` are treated as the same account.

### Users — `/api/users`

| Method | Route | Description |
|--------|------|-------------|
| GET | `/api/users/me` | Get the current user's profile |
| PATCH | `/api/users/me` | Update name/bio/systemPrompt |

### Documents — `/api/documents`

| Method | Route | Description |
|--------|------|-------------|
| GET | `/api/documents` | List the current user's PDFs |
| POST | `/api/documents` | Upload a PDF (multipart, field `file`); `400` on a missing/invalid/unreadable PDF |
| GET | `/api/documents/{id}` | Get one document's metadata |
| GET | `/api/documents/{id}/file` | Stream/view the PDF file |
| DELETE | `/api/documents/{id}` | Delete a document |

### Profile Masters — `/api/profile-masters`

| Method | Route | Description |
|--------|------|-------------|
| GET | `/api/profile-masters` | List the current user's own Masters (4 defaults + any custom ones) |
| POST | `/api/profile-masters` | Create a new master, owned by the current user |
| PATCH | `/api/profile-masters/{id}` | Update one of the current user's own Masters (`404` if it belongs to someone else) |
| DELETE | `/api/profile-masters/{id}` | Delete one of the current user's own Masters |

### Collections (Tags) — `/api/collections`

> Mounted at `/api/collections` for historical reasons — the product/domain concept is **Tag**, not a nested "collection". See the comment on `TagController` and `docs/openapi.yaml`.

| Method | Route | Description |
|--------|------|-------------|
| GET | `/api/collections` | List the current user's tags |
| POST | `/api/collections` | Create a tag |
| PATCH | `/api/collections/{id}` | Rename a tag |
| DELETE | `/api/collections/{id}` | Delete a tag |
| PUT | `/api/collections/{id}/documents/{documentId}` | Add a document to a tag |
| DELETE | `/api/collections/{id}/documents/{documentId}` | Remove a document from a tag |

### Sessions — `/api/sessions`

| Method | Route | Description |
|--------|------|-------------|
| POST | `/api/sessions` | Create a session (document, page range, difficulty, language, Profile Master, `aiProvider`); `400` if `startPage > endPage`, `endPage` exceeds the document's real page count, or `aiProvider` isn't a known provider name |
| GET | `/api/sessions/{id}` | Load a session |
| GET | `/api/sessions/{id}/context` | Inspect the raw prompt pieces BooKI will use (app prompt, master prompt, user prompt) — for transparency/debugging |
| PATCH | `/api/sessions/{id}/current-page` | Update the reader's current page; `400` if outside `[startPage, endPage]` |
| GET | `/api/sessions/{id}/messages` | Conversation history |
| POST | `/api/sessions/{id}/messages` | Send a message to BooKI, get its reply |
| GET | `/api/sessions/{id}/progress` | Reading progress for the session |
| GET | `/api/sessions/{id}/notifications` | Contextual nudges (halfway, done, say hi, try a quiz), localized per session language |
| GET | `/api/sessions/{id}/reports` | List reports already generated/sent for this session |
| POST | `/api/sessions/{id}/reports/progress` | Generate (and optionally email) a progress report |
| POST | `/api/sessions/{id}/reports/quiz` | Generate (and optionally email) a quiz report |
| POST | `/api/sessions/{id}/summary` | Generate a reading summary |

### Quiz — `/api/sessions/{sessionId}` (mounted under Sessions)

| Method | Route | Description |
|--------|------|-------------|
| POST | `/api/sessions/{sessionId}/quiz` | Generate a quiz question for the session |
| POST | `/api/sessions/{sessionId}/quiz/answer` | Submit an answer, get correctness/feedback |
| GET | `/api/sessions/{sessionId}/quiz/attempts` | Quiz attempt history/report for the session |

### Reports — `/api/reports`

| Method | Route | Description |
|--------|------|-------------|
| GET | `/api/reports/{id}/file` | Download a generated report PDF |

### Health — `/api/health` (public)

| Method | Route | Description |
|--------|------|-------------|
| GET | `/api/health` | Liveness check |

## Security

- JWT Bearer token in the `Authorization` header (`security/JwtAuthenticationFilter`, `security/JwtUtil`).
- Passwords hashed with BCrypt.
- CORS configured for `http://localhost:5173` only (see `config/SecurityConfig`) — any other origin, including `http://127.0.0.1:5173`, is rejected with a 403 "Invalid CORS request".
- `/api/auth/**` and `/api/health` are public; every other `/api/**` route requires a valid JWT.
- The JWT is stateless: a valid signature is enough to authenticate, even if the `userId` it carries no longer exists (e.g. after a local DB reset, or after switching between the `local`/`dev` profiles — H2 and MySQL are entirely separate user sets). Any endpoint that then looks up that user throws `NoSuchElementException` → `404 {"error": "Resource not found"}`. `GET /profile-masters` is a quieter variant of the same symptom: it doesn't `orElseThrow` on the user, it just returns an empty list for a `userId` matching nobody — so a stale token there looks like "no Masters" with no error at all, not a `404`. Either way, the fix is the same: log out and back in (or register fresh) to get a token for a user that actually exists in whichever DB the backend is currently pointed at.
- Every error response, from every handler in `config/GlobalExceptionHandler`, uses the same `{"error": "..."}` shape — including validation (`400`), auth (`401`), not-found (`404`), and the two multipart-specific cases (missing file part, file too large). The frontend's `lib/errors.ts` (see `docs/frontend.md`) relies on this being consistent everywhere.

## AI configuration

The `AiProvider` interface (package `ai`) has 4 implementations, **all always registered** as named Spring beans (`@Component("claude")`, `@Component("openai")`, etc.) — unlike an earlier version of this file, they're no longer gated to a single active one. `AiProviderRegistry` holds all 4 (`Map<String, AiProvider>`, auto-populated by Spring from the bean names) and resolves which one to use per call: a specific name if given and known, otherwise `booki.ai.default-provider`.

| Bean name | Class | Notes |
|---|---|---|
| `claude` | `ClaudeProvider` | Anthropic Messages API. System prompt is its own top-level field (not a `system`-role message like the others), and `max_tokens` is required. `content` is an array of *typed* blocks, not always one `text` block at index 0 — `claude-sonnet-5` puts a `thinking` block first, so the reply is found by scanning for `type: "text"`, not `content[0]`. Bumped `max_tokens` to 4096 (from an initial 1024) since thinking tokens eat into the same budget as the reply. |
| `openai` | `OpenAiProvider` | Extends `OpenAiCompatibleProvider` (see below). |
| `kimi` | `KimiProvider` | Also extends `OpenAiCompatibleProvider` — Moonshot's Kimi API is explicitly OpenAI-compatible, just a different base URL/model. Good for very large documents/contexts. |
| `ollama` | `OllamaProvider` | Talks to a local Ollama daemon, no API key. "Local" means local to whatever machine runs *this backend* — not to the end user's device, which matters since the app is mobile-first (see `docs/decisions.md`, ADR-007). |

`OpenAiCompatibleProvider` is a shared abstract base class for any provider speaking the OpenAI chat-completions wire format (`OpenAiProvider`, `KimiProvider`) — same request/response parsing, only base URL/API key/model differ.

### Per-session provider choice

`Session.aiProvider` (nullable) is set once at `POST /sessions` and used for the lifetime of that session — chat, quiz question generation, quiz grading, and summary generation all resolve the same `AiProvider` via `AiProviderRegistry.get(session.getAiProvider())`. Passing an unrecognized name is a `400`; passing none falls back to `booki.ai.default-provider`, which is **profile-dependent**:

- `local` profile → defaults to `ollama` (no cost, no key, good for offline iteration).
- `dev` profile → defaults to `claude`.
- Either can be overridden per-run with the `AI_PROVIDER` env var regardless of profile.

`GET/POST /sessions` always echoes the **resolved** name in `aiProvider` (never null), even for a session that didn't pick one explicitly.

### Where AI is actually called vs. templated

- **Chat, quiz question generation, quiz grading, summary generation** — all real AI calls, grounded in the session's reading (the relevant page(s) of `DocumentPage.extractedText`) plus the same three-layer prompt `SessionContextBuilder` builds (app baseline + Profile Master persona + the user's own `systemPrompt`). Quiz grading asks the model to reply in a strict `CORRECT:`/`SCORE:`/`FEEDBACK:` format that `QuizServiceImpl.parseGrade` parses; a response that doesn't follow the format (e.g. the offline fallback message) degrades to `correct=false, score=0`, feedback = the raw text.
- **Progress/quiz-correction PDF reports** (`POST /sessions/{id}/reports/*`) — deliberately stay template-based, no AI call. These are factual recaps (page counts, past Q&A already graded) where a template is more reliable than an LLM restating numbers.

Variables, in `.env` at the **repo root** (sibling of `.env.example`, not inside `backend/`) or the shell environment:

```
AI_PROVIDER=claude
ANTHROPIC_API_KEY=sk-ant-...
ANTHROPIC_MODEL=claude-sonnet-5   # optional, this is already the default
OPENAI_API_KEY=sk-...
KIMI_API_KEY=...
OLLAMA_BASE_URL=http://localhost:11434   # optional, this is already the default
OLLAMA_MODEL=llama3.2:1b                 # optional, this is already the default — must be `ollama pull`ed first
```

`.env` isn't read by Spring Boot itself — `backend/build.gradle`'s `bootRun`/`bootRunLocal` tasks parse it and inject each `KEY=VALUE` line as a JVM environment variable before launching, so it works no matter which terminal you run `./gradlew` from. A variable already `export`ed in the real shell always wins over `.env` (same convention as dotenv tooling elsewhere) — `.env` only fills in what's missing. `.env` is gitignored (`.env.example` is the tracked template).

Every provider degrades the same way on failure (network error, missing/invalid key, model not found, or Ollama simply not running): logs the exception and returns a friendly fallback message instead of a `500` — verified with no `ANTHROPIC_API_KEY` set. Separately verified end-to-end **with a real, funded Anthropic key**: chat, quiz generation, quiz grading, and summary all produce genuine, content-grounded responses (not just the fallback).

Two Anthropic-specific errors worth recognizing from the backend's own log (not returned in the API response, since callers always get the same friendly fallback):
- **`401 Unauthorized`** — the key was copied from the wrong place. It must come from console.anthropic.com → API Keys, not a claude.ai chat session (a different account/system entirely).
- **`400 Bad Request` with `"Your credit balance is too low..."`** — the API console account itself has no credits/billing set up. This is separate from any claude.ai subscription; add a payment method or buy credits at console.anthropic.com → Plans & Billing.

**Hardware note on Ollama**: the default model is the small `llama3.2:1b`, not the earlier `llama3.1` (8B) — on a machine without an actual ROCm/CUDA-compatible GPU (verified on this dev machine: an integrated AMD Vega 3 iGPU, `gfx902`, isn't supported by ROCm at all — its minimum supported target is `gfx1030`), an 8B model runs on raw CPU and is impractically slow (one real request took 32 minutes of CPU time and pushed the whole system into swap). A 1B model is far more CPU-feasible. On real GPU-equipped hardware, `OLLAMA_MODEL` can be bumped back up.
