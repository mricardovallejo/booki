# BooKI Backend

## Profiles

- `dev` (default): PostgreSQL on localhost:5432 (via `docker compose up -d`). Same engine as the deployed environment.
- `local`: file-based H2 (`~/booki-local-db`) with `AUTO_SERVER=TRUE`, in PostgreSQL compatibility mode. Ideal for development without Docker.
- `test`: in-memory H2 (PostgreSQL mode), Flyway disabled.

### Running locally with H2

```bash
cd backend
./gradlew bootRunLocal
```

This starts the backend on `http://localhost:8080` without needing PostgreSQL or Docker.

## Main entities

- `User`: email, password hash, display name. Nothing else — the reader's goal / level / learning preferences live in **reader profiles** (ADR-015 removed the old `bio` + `systemPrompt`; ADR-017 made the reader context its own entity).
- `Document`: metadata for a PDF uploaded by the user (title, file path, page count).
- `DocumentPage`: text extracted per page of a document.
- `AiProfile` + `SlotPrompt`: the "master" a session runs on — persona, difficulty rubrics, per-function prompts, capability routing — plus `enabledCapabilities`. Per-user; seeded from code templates at registration. **`docs/prompts.md`** (ADR-015; the old `ProfileMaster` entity is gone).
- `ReaderProfile`: who is reading, in one study context — `name`, free-text `context`, `readerLevel`, `isDefault`, `readOnly`. A `user` of `null` marks a shipped read-only template (`General reader` or `Language-support reader`, seeded in `V1__init.sql`) everyone sees. Not tied to any AI Profile — a `Session` picks one. ADR-017/ADR-019.
- `ReaderProfileProvisioner`: registration creates one editable `My reader profile` from the General reader scaffold and makes it the user's default. ADR-020.
- `Tag`: a per-user label a document can be filed under (many-to-many with `Document`); exposed via the `/api/collections` endpoints for historical reasons — see note below.
- `Session`: an open-ended reading journey. `startPage` records where it began, `endPage` grows to the furthest page reached, and `currentPage` is freely navigable across the document. It also stores the chosen `difficulty`, `language`, `aiProvider` (nullable), an optional `AiProfile` and an optional `ReaderProfile` (both FKs `ON DELETE SET NULL`; null → resolved to the user's default at read time).
- `Message`: one turn of conversation history in a session (`USER` / `BOOKI`, `TEXT` / `VOICE`). Text and voice turns share this single model — a voice turn is just a `Message` whose `inputType` is `VOICE`; raw audio is never stored.
- `QuizAttempt`: a generated quiz question for a page plus the reader's answer, correctness, score, and feedback; FKs to the `AiProfile` it was graded under (`ON DELETE SET NULL`).
- `SentReport`: a record of a progress/quiz report generated (and optionally emailed) for a session.

## REST API

All routes below are under `/api` and require a `Authorization: Bearer <jwt>` header unless noted otherwise.

### Auth — `/api/auth` (public)

| Method | Route | Description |
|--------|------|-------------|
| POST | `/api/auth/register` | Register with email/password, returns a JWT + the new user (`201`) |
| POST | `/api/auth/login` | Login, returns a JWT + the user |

Email is normalized (trimmed + lowercased) before lookup/storage on both routes, so `Name@Example.com` and `name@example.com` are treated as the same account.

On register the account is also seeded with its default tutor profiles and an editable `My reader profile` (ADR-020). It then publishes a `UserRegisteredEvent`; unless `WELCOME_DOCUMENT_ENABLED=false`, `WelcomeDocumentProvisioner` reacts with `@TransactionalEventListener(AFTER_COMMIT)` + `@Async` (`config/AsyncConfig`, `backgroundTasks` pool) to drop the bundled BooKI guide into the new library (ADR-022) — off the request thread, so it adds nothing to the sign-up response.

### Users — `/api/users`

| Method | Route | Description |
|--------|------|-------------|
| GET | `/api/users/me` | Get the current user's profile |
| PATCH | `/api/users/me` | Update the display name (the only editable user field — learning preferences live in reader profiles) |

### Documents — `/api/documents`

| Method | Route | Description |
|--------|------|-------------|
| GET | `/api/documents` | List the current user's PDFs |
| POST | `/api/documents` | Upload a PDF (multipart, field `file`); `400` if the bytes don't start with `%PDF-` (checked before PDFBox) or the PDF is unreadable; `413`/`400` past the 50 MB multipart cap |
| GET | `/api/documents/{id}` | Get one document's metadata |
| GET | `/api/documents/{id}/file` | Stream/view the PDF file |
| DELETE | `/api/documents/{id}` | Delete a document |

Upload parsing, blob storage and row creation live in `DocumentService.importPdf(userId, title, bytes)`; `uploadDocument` just unwraps the multipart and delegates. The same method seeds the welcome guide (ADR-022). Text is extracted in a single `PDFTextStripper` pass over the whole document (split on a form feed), then the `DocumentPage` rows are written with one `saveAll` — a per-page `getText` loop re-walked the page tree each call and was O(n²) on large PDFs.

### AI Profiles — `/api/ai-profiles` · Reader Profiles — `/api/reader-profiles`

The AI Profile (the UI calls it a **tutor profile**) holds the persona,
difficulty, per-function prompts and routing; the reader profile is who is
reading. A session picks one of each. Full model in **`docs/prompts.md`**; the
endpoints:

| Method | Route | Description |
|--------|------|-------------|
| GET | `/api/ai-profiles` | The user's profiles (seeded at registration, one flagged default) — no slots |
| GET | `/api/ai-profiles/{id}` | One profile with its SlotPrompts |
| PATCH | `/api/ai-profiles/{id}` | Name / `enabledCapabilities` / slot `text` |
| POST | `/api/ai-profiles/{id}/duplicate` | Autonomous copy (`{name?}`) |
| POST | `/api/ai-profiles/{id}/revert` | One SlotPrompt back to its original (`{key}`) |
| POST | `/api/ai-profiles/{id}/restore` | Whole profile back to its shipped template |
| DELETE | `/api/ai-profiles/{id}` | Delete (`400` if it's the only one) |
| GET | `/api/reader-profiles` | Shipped read-only reader templates + the user's own |
| POST | `/api/reader-profiles` | Create (`{name, context?, readerLevel?, fromId?}`) |
| PATCH | `/api/reader-profiles/{id}` | `name` / `context` / `readerLevel` (`""` clears) / `isDefault:true` — `404` on shipped templates |
| DELETE | `/api/reader-profiles/{id}` | Delete an owned one — `404` on shipped templates |

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

Page fields have distinct meanings: `startPage` is the immutable start of the
reading journey, `endPage` is a reading-progress marker (the furthest page
reached, only grows), and `currentPage` is the page currently displayed. The
reader may navigate to any document page, including backward for review. The AI
activities (quiz, summary, chat quick-actions) run on a **client-side activity
range** the reader controls (default: pages read so far), passed on each request
and validated against the whole document (`1..pageCount`, clamped not rejected).
See ADR-024.

| Method | Route | Description |
|--------|------|-------------|
| POST | `/api/sessions` | Create a session (document, starting page, difficulty, language, `aiProfileId?`, `readerProfileId?`). `endPage` is optional for compatibility and defaults to `startPage`. Omit either profile id to use the user's default |
| GET | `/api/sessions/{id}` | Load a session |
| GET | `/api/sessions/{id}/context` | Inspect the assembled prompt layers (core, difficulty, persona, reader profile, per-function, routing, session facts) each tagged with a `group`, plus `aiProfileName` / `readerProfileName`. See `docs/prompts.md` |
| PATCH | `/api/sessions/{id}/current-page` | Navigate to any page in the document; moving forward also expands `endPage`, the furthest page reached |
| GET | `/api/sessions/{id}/messages` | Conversation history |
| POST | `/api/sessions/{id}/messages` | Send a message to BooKI, get its reply. Optional `capabilityHint` (`quiz`/`summary`/`explain`/`mnemonic`) runs that capability directly; a quick-action also sends the activity range as `pageStart`/`pageEnd` (plain chat omits them and stays on the reading position). `502` if the AI provider fails |
| POST | `/api/sessions/{id}/voice` | Voice turn: multipart `audio` (+ optional `capabilityHint`). Backend transcribes → same `ConversationEngine` → optional spoken reply. Returns the persisted user + bot messages and a base64 MP3 (or `null`). `502` if transcription fails |
| GET | `/api/sessions/{id}/progress` | Reading progress for the session |
| GET | `/api/sessions/{id}/notifications` | Contextual nudges (halfway, done, say hi, try a quiz), localized per session language |
| GET | `/api/sessions/{id}/reports` | List reports already generated/sent for this session |
| POST | `/api/sessions/{id}/reports/progress` | Generate (and optionally email) a progress report |
| POST | `/api/sessions/{id}/reports/quiz` | Generate (and optionally email) a quiz report |
| POST | `/api/sessions/{id}/summary` | Generate a summary for a requested page range (omitted fields default to the whole document; clamped, not rejected). The book excerpt is capped (~12k chars, even-sampled) so a wide range stays bounded |

### Quiz — `/api/sessions/{sessionId}` (mounted under Sessions)

| Method | Route | Description |
|--------|------|-------------|
| POST | `/api/sessions/{sessionId}/quiz` | Generate 1–20 questions for a requested page range (omitted fields default to the whole document; clamped, not rejected). Question count is independent of the range width — questions are spread evenly across it, and a 1-page range still yields N. The response echoes the effective range |
| POST | `/api/sessions/{sessionId}/quiz/answer` | Submit an open answer, get a score + teaching feedback that states the answer (ADR-023) |
| GET | `/api/sessions/{sessionId}/quiz/attempts` | Quiz attempt history/report for the session |

### Voice — `/api/voice`

| Method | Route | Description |
|--------|------|-------------|
| GET | `/api/voice/capabilities` | `{ stt, tts }` — whether the deployment has server-side speech providers configured, so the client picks the cloud path or the browser fallback |

(the voice *turn* endpoint lives under Sessions — `POST /api/sessions/{id}/voice`, above.)

### Reports — `/api/reports`

| Method | Route | Description |
|--------|------|-------------|
| GET | `/api/reports/{id}/file` | Download a generated report PDF |

### Health — `/api/health` + `/actuator` (partly public)

| Method | Route | Access | Description |
|--------|------|--------|-------------|
| GET | `/api/health` | public | Simple liveness check (`HealthController`) |
| GET | `/actuator/health` + `/actuator/health/{liveness,readiness}` | public | Aggregate status only for anonymous callers; per-dependency detail (`db`, `diskSpace`, `ssl`, custom `storage`) is shown only to an authenticated caller (`show-details: when-authorized`). Cloud Run's probe uses these. |
| GET | `/actuator/info` | **JWT** | Version/build info; `info.java`/`info.os` are turned off |
| — | `/v3/api-docs`, `/swagger-ui` | — | Enabled **only in the `local` profile**; a deployed instance (which runs the `dev` profile) doesn't expose them at all |

## Security

- **JWT Bearer** token in the `Authorization` header (`security/JwtAuthenticationFilter`, `security/JwtUtil`). Passwords hashed with BCrypt. Sessions are stateless (no server session store).
- **JWT signing key** comes from `booki.jwt.secret` (env `JWT_SECRET`). If it's unset, the shipped placeholder, or shorter than 32 bytes, `JwtUtil` signs with a **random ephemeral key** and logs a loud warning — `bootRunLocal` and the test suite work with zero setup, but the ephemeral key doesn't survive a restart and differs per instance, so a real deployment **must** set `JWT_SECRET` (`openssl rand -base64 32`). There is no predictable default key anymore.
- The auth filter **rejects a token whose user no longer exists** (`userRepository.existsById`) — a leftover token from a wiped DB or a deleted account no longer authenticates. `extractUserId` is null-safe. (Previously a valid signature alone was enough; the "log out and back in after a DB reset" advice still applies, you just get a `401` now instead of a later `404`.)
- **CORS** origins come from `booki.cors.allowed-origins` (env `CORS_ALLOWED_ORIGINS`, comma-separated; default `http://localhost:5173`), applied to `/api/**` only. Allowed request headers are an explicit list (`Authorization, Content-Type, Accept, X-Requested-With`), not `*`. Credentials are allowed, so `*` origins are not an option; any origin not on the list (incl. `http://127.0.0.1:5173` by default) gets a `403`.
- **Route access**: `/api/auth/**`, `/api/health`, `/actuator/health/**`, `OPTIONS`, and (in `local`) the swagger paths are public; **every other request requires a valid JWT** (`anyRequest().authenticated()`).
- **Error responses**: every handler in `config/GlobalExceptionHandler` returns the same `{"error": "..."}` shape — validation (`400`), auth (`401`), not-found (`404`, generic "Resource not found"), AI/voice provider failure (`502`), and the multipart cases. An **uncaught `RuntimeException` returns a fixed neutral `500` message** ("Something went wrong on our side…"); the real exception (which can carry bucket names, class names, paths) is logged, never sent to the client. The frontend's `lib/errors.ts` relies on the `{error}` shape being everywhere.
- **Prompt-injection defence-in-depth**: `PromptAssembler` fences the page text between `<<<BEGIN DOCUMENT>>>` / `<<<END DOCUMENT>>>`, and the core prompt states that the document and the reader's messages are material, not instructions. The uploaded file is also validated as a real PDF (`%PDF-` magic bytes) before PDFBox touches it, and the STT provider only accepts audio MIME types on a small allowlist.

## Resilience

- **Transactions**: service reads that walk lazy associations are `@Transactional(readOnly = true)`; multi-write operations (`createSession`, `updateCurrentPage`, `uploadDocument`, `register`, the report generators) are `@Transactional` so a mid-way failure rolls back cleanly. `uploadDocument` also deletes the just-stored object if the DB write fails. `ConversationEngine.sendMessage` and `generateSummary` are **deliberately not** transactional — they span a slow model call and persist their parts separately so a provider failure never leaves a fake reply behind. (`spring.jpa.open-in-view` is still on and currently masks any missed case; disabling it is a later step.)
- **Outbound HTTP timeouts** (`config/OutboundHttp`): every AI/voice provider `WebClient` gets a 10 s connect timeout, a 60 s idle (no-bytes) read timeout, and a 120 s whole-call ceiling (`Mono.timeout` for blocking calls, per-chunk `Flux.timeout` for the Claude stream). A hung upstream can no longer park a request thread indefinitely; the timeout surfaces as the same `502` as any other provider failure.
- **Background work** (`config/AsyncConfig`): `@EnableAsync` + one small bounded pool (`backgroundTasks`, core 1 / max 3 / queue 100, drains on shutdown). Its only user is the welcome-guide seeding (ADR-022) — best-effort work that must stay off the sign-up request thread. Not a general job queue; anything with delivery guarantees needs a real one.

## Request validation

Every write endpoint has `@Valid` on its `@RequestBody`. Free-text fields carry `@Size` caps (persona slots ≤ 8000, reader-profile context ≤ 4000, names ≤ 120, prompts ≤ 2000), enum-ish strings carry `@Pattern` (`difficulty` ∈ `easy|medium|hard`, `deliverAs` ∈ `chat|pdf`, `readerLevel` ∈ `beginner|intermediate|advanced`), numeric ranges carry `@Min`/`@Max` (`lengthPages` 1–10), and email fields carry `@Email`. A violation is a `400 {"error": "field: message"}` via `GlobalExceptionHandler.handleValidation`.

## Conversation engine, capabilities and voice

### Prompt catalog

The authoritative fixed core, editable starting texts, shipped tutor personas,
and optional per-template prompt overrides are in
`src/main/resources/prompts/catalog.yml`, not Java or environment variables.
`SlotPromptCatalog` validates the versioned catalog at startup.
`SlotKey` keeps machine-dependent output contracts typed in Java. The optional
`BOOKI_PROMPT_CATALOG` variable changes only the resource location for controlled
experiments. See `docs/prompts.md` and ADR-019.

### `ConversationEngine` (package `conversation`)

Every conversational turn — text, quick action, or transcribed voice — goes
through `ConversationEngine.converse(ConversationRequest)`. It:

1. resolves and ownership-checks the `Session`;
2. builds the history window — the **most recent N** messages
   (`booki.conversation.history-window`, default 20), in chronological order;
3. persists the user turn;
4. assembles the system prompt via `PromptAssembler` (the layered
   core → rubric → persona → reader context → session facts — see `docs/prompts.md`)
   plus page context and the capability router filtered to the profile's enabled
   set. By default, page context is the current page and up to seven preceding
   pages from this reading journey. An explicit written request such as
   `pages 10 to 12` selects that document range instead (maximum 20 pages). In
   both cases it is **capped** at `booki.conversation.max-context-chars`
   (default 24000), prioritizing the newest selected page before restoring
   reading order;
5. calls the session's `AiProvider` — via a capability if one applies (below);
6. persists BooKI's reply, or raises `ConversationFailedException`.

It's transport-neutral: `SessionServiceImpl.sendMessage` and
`VoiceConversationService` are adapters; the engine never sees HTTP.

### Conversational capabilities (`conversation/capability`)

`ConversationCapability` beans — `quiz`, `summary`, `explain`, `mnemonic` —
each produce the reply text for one turn. Not an agent framework. Routing is
**provider-neutral** (no native tool calling, no keyword matching): the router
instructions are appended to the system prompt, and when a capability fits the
model replies with only `{"capability":"<name>"}`, which `CapabilityRegistry`
recognises strictly. A quick-action button skips routing by passing
`capabilityHint`, and also sends the activity range (`pageStart`/`pageEnd`).
`ConversationEngine.buildContextText` uses that range (its last
`MAX_EXPLICIT_CONTEXT_PAGES`) when present, a range typed into the message when
that is present, else the reading-position window. `quiz` and `summary` reuse
`QuizService.generateComprehensionQuestion(Session, pageContextText)` /
`ReportService.generateSummaryText(Session, ..., pageContextText)`, so the
selected page context is carried into the capability.
The conversational quiz only *asks* — scored `QuizAttempt` rows stay on the
`POST /quiz/answer` panel flow. See ADR-008.

### Voice (`voice`)

`SpeechToTextProvider` / `TextToSpeechProvider` — server-side, credentials
server-side. First impl is OpenAI-compatible (`whisper-1`,
`/audio/speech`), inert without an API key. `VoiceConversationService` bridges
audio → STT → the same `ConversationEngine` (`InputType.VOICE`) → TTS
(best-effort). Session language drives the locale; raw audio is never persisted;
uploads and TTS input are size-capped (`booki.voice.*`). See ADR-009 and
`docs/ai-voice.md` for provider setup.

### Streaming readiness

`StreamingAiProvider` / `ConversationEngine.converseStreaming` are opt-in
companions — nothing calls them over HTTP yet. When a low-latency requirement
appears the work is "add an SSE endpoint + a streaming provider method", not a
rewrite. See ADR-010.

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

- `dev` / `test` → defaults to `openai` (one key also covers cloud voice STT/TTS).
- `local` profile → defaults to `ollama` (no cost, no key, good for offline iteration).
- Any profile can be overridden per-run with the `AI_PROVIDER` env var.

`GET/POST /sessions` always echoes the **resolved** name in `aiProvider` (never null), even for a session that didn't pick one explicitly.

### Where AI is actually called vs. templated

- **Chat, quiz question generation, quiz grading, summary generation** — all real AI calls, grounded in the session's reading (the relevant page(s) of `DocumentPage.extractedText`) plus the layered prompt `PromptAssembler` builds, with the matching `fn_*` SlotPrompt layered in for the capability calls (`docs/prompts.md`). Quiz grading asks the model for a `SCORE:` (0–1) + a teaching `FEEDBACK:` that states the answer (the `fn_answer_grading` locked frame); `QuizServiceImpl.parseGrade` derives `correct = score ≥ 0.6` so the correction report is internally consistent (ADR-023). A response that doesn't follow the format degrades to `score=0`, feedback = the raw text. (Provider *failures* no longer reach the parser — see below.)
- **Progress/quiz-correction PDF reports** (`POST /sessions/{id}/reports/*`) — deliberately stay template-based, no AI call. These are factual recaps (page counts, past Q&A already graded) where a template is more reliable than an LLM restating numbers.

Variables, in `.env` at the **repo root** (sibling of `.env.example`, not inside `backend/`) or the shell environment:

```
AI_PROVIDER=openai                       # default; also: claude | kimi | ollama
OPENAI_API_KEY=sk-proj-...               # covers chat AND cloud voice (STT/TTS)
OPENAI_MODEL=gpt-4o-mini                 # optional, this is the default
ANTHROPIC_API_KEY=sk-ant-...
ANTHROPIC_MODEL=claude-sonnet-5          # optional, this is already the default
KIMI_API_KEY=...
OLLAMA_BASE_URL=http://localhost:11434   # optional, this is already the default
OLLAMA_MODEL=llama3.2:1b                 # optional, this is already the default — must be `ollama pull`ed first
```

`.env` isn't read by Spring Boot itself — `backend/build.gradle`'s `bootRun`/`bootRunLocal` tasks parse it and inject each `KEY=VALUE` line as a JVM environment variable before launching, so it works no matter which terminal you run `./gradlew` from. A variable already `export`ed in the real shell always wins over `.env` (same convention as dotenv tooling elsewhere) — `.env` only fills in what's missing. `.env` is gitignored (`.env.example` is the tracked template).

On failure (network error, missing/invalid key, model not found, Ollama not running, an empty/unparseable payload, or a **timeout** — see Resilience above) a provider now throws `AiProviderException` instead of returning canned apology text. `ConversationEngine` turns that into `ConversationFailedException`, and `GlobalExceptionHandler` returns **`502` `{"error": "The reading assistant is temporarily unavailable…"}`** — a real, distinguishable error the frontend surfaces instead of persisting a fake BooKI answer. Quiz/summary endpoints propagate it the same way. Verified end-to-end **with a real, funded Anthropic key**: chat, quiz generation, quiz grading, and summary all produce genuine, content-grounded responses.

Two Anthropic-specific errors worth recognizing from the backend's own log (the API response is the generic `502` above):
- **`401 Unauthorized`** — the key was copied from the wrong place. It must come from console.anthropic.com → API Keys, not a claude.ai chat session (a different account/system entirely).
- **`400 Bad Request` with `"Your credit balance is too low..."`** — the API console account itself has no credits/billing set up. This is separate from any claude.ai subscription; add a payment method or buy credits at console.anthropic.com → Plans & Billing.

**Hardware note on Ollama**: the default model is the small `llama3.2:1b`, not the earlier `llama3.1` (8B) — on a machine without an actual ROCm/CUDA-compatible GPU (verified on this dev machine: an integrated AMD Vega 3 iGPU, `gfx902`, isn't supported by ROCm at all — its minimum supported target is `gfx1030`), an 8B model runs on raw CPU and is impractically slow (one real request took 32 minutes of CPU time and pushed the whole system into swap). A 1B model is far more CPU-feasible. On real GPU-equipped hardware, `OLLAMA_MODEL` can be bumped back up.
