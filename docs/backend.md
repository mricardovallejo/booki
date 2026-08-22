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
- `Session`: a page range (`startPage`/`endPage`) of a document, with `currentPage`, chosen `difficulty`, `language`, and an optional `ProfileMaster`.
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
| POST | `/api/sessions` | Create a session (document, page range, difficulty, language, Profile Master) |
| GET | `/api/sessions/{id}` | Load a session |
| GET | `/api/sessions/{id}/context` | Inspect the raw prompt pieces BooKI will use (app prompt, master prompt, user prompt) — for transparency/debugging |
| PATCH | `/api/sessions/{id}/current-page` | Update the reader's current page |
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
- The JWT is stateless: a valid signature is enough to authenticate, even if the `userId` it carries no longer exists (e.g. after a local DB reset). Any endpoint that then looks up that user throws `NoSuchElementException` → `404 {"error": "Resource not found"}`. Symptom from the frontend: everything looks "not found" until you log out and back in for a fresh token.
- Every error response, from every handler in `config/GlobalExceptionHandler`, uses the same `{"error": "..."}` shape — including validation (`400`), auth (`401`), not-found (`404`), and the two multipart-specific cases (missing file part, file too large). The frontend's `lib/errors.ts` (see `docs/frontend.md`) relies on this being consistent everywhere.

## AI configuration

Variables in `.env` or the environment:

```
AI_PROVIDER=openai
OPENAI_API_KEY=sk-...
KIMI_API_KEY=...
```

The `AiProvider` interface (package `ai`) is implemented today by `OpenAiProvider` only. `AI_PROVIDER=kimi` is read by `AiProviderOpenAiCondition` but there is currently no `KimiProvider` bean, so setting it will fail Spring's dependency injection at startup — Kimi support is wired for configuration but not yet implemented in code.
