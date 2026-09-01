# Architecture decisions (ADRs)

## ADR-001: React + Vite + responsive PWA as the primary client

- **Context**: BooKI must be usable on Android, Windows and Linux.
- **Decision**: one responsive React + Vite + PWA application is the primary (and only) BooKI client. No Flutter, React Native, Tauri, or native apps.
- **Reasons**: a single responsive web app covers all three targets at once while preserving the existing React codebase; it deploys instantly in any browser with no app-store step; and it installs as a PWA where the user wants an app-like entry point. This choice stands on cross-platform reach and code reuse — **not** on any browser API (voice is a backend concern, see ADR-009).
- **Consequence**: the experience isn't 100% native, which is an accepted trade-off for one codebase across every target. Anything genuinely platform-specific would need justification against this decision.

## ADR-002: Web Speech API for voice

- **Context**: fast voice support was needed without backend complexity.
- **Decision**: browser-side STT with `SpeechRecognition`; TTS pending via `speechSynthesis`.
- **Reasons**: reduces latency, zero cost, no need to send binary audio.
- **Consequence**: depends on the browser; can migrate to a backend implementation later.
- **Superseded by ADR-009**: `SpeechRecognition` is no longer the architectural voice dependency — it only works on Chromium and breaks exactly on Android-as-PWA, a target client. It stays as a fallback; the core path is cloud STT/TTS.

## ADR-003: MySQL for dev, H2 for tests

- **Context**: a relational database with structured data was needed.
- **Decision**: MySQL via Docker Compose for development; in-memory H2 for tests.
- **Reasons**: dev/prod parity with MySQL; fast, isolated tests with H2.
- **Superseded by ADR-011**: the engine is now PostgreSQL. The dev/test split (a real database in Docker, H2 for fast tests) is unchanged.

## ADR-004: file storage stays behind a storage seam

- **Context**: BooKI stores two kinds of files — uploaded PDFs and generated report/summary PDFs. It's intended to run as a cloud application, eventually on more than one instance.
- **Decision**: all file access goes through a `StorageAdapter` interface (`com.booki.storage`) — `put(key, bytes, contentType)` / `get(key)` / `delete(key)`, addressing everything by an opaque forward-slash key (`documents/…`, `reports/…`). The key is what's persisted (`documents.file_path`, `sent_reports.file_name`), never an absolute path; reads are handed out as a Spring `Resource`, never a `File`. PDF text is still extracted per page into the database (`DocumentPage`), which is where the AI context comes from. The implementation is chosen by `booki.storage.driver`.
- **Reasons**: the seam means the storage backend is a deployment choice, not a code change — controllers, the frontend and the DB never see it.
- **Consequence**: `LocalStorageAdapter` (default, `driver=local`) writes under one directory (`booki.storage.local-path`, default `./storage`) and is fine for local dev and single-instance runs; it does not survive an ephemeral redeploy and is not shared between instances. See ADR-012 for the S3-compatible backend that removes that limitation.

## ADR-005: OpenAI as the initial AI provider

- **Context**: basic Kimi and OpenAI packages are available.
- **Decision**: OpenAI by default, behind an abstract interface for swapping providers.
- **Reasons**: stable API, extensive documentation, an affordable model (`gpt-4o-mini`).
- **Consequence**: can be changed via configuration without touching session logic.
- **Superseded by ADR-007**: the "one active provider, chosen at startup" model this ADR assumed no longer holds — see below.

## ADR-006: Profile Masters are per-user, not global

- **Context**: Profile Masters originally had no owner — one shared list of 4 built-in personas, visible and editable by every account (`isActive` was the only real state). Once editing was added, that meant one user's edit would silently change what every other user sees.
- **Decision**: add `user_id` to `profile_masters`. The original 4 seed rows stay as templates (`user_id IS NULL`, never returned directly by the API); at registration, each new user gets their own copy of all 4. Every read/write (`list`, `create`, `update`, `delete`) is scoped by owner (`findByIdAndUserId`), the same pattern already used for `Document`/`Tag`/`Session`.
- **Reasons**: lets a reader freely rename/tweak "their" Grade-3-teacher persona without touching anyone else's; keeps the "pick a Master" UX at session/quiz creation unchanged (still just a list to choose from) — it's simply each user's own list now instead of one global list.
- **Consequence**: deleting a Master a user already used in a `Session`/`QuizAttempt` had to stop being a raw DB delete — those rows now get their `profileMasterId` cleared (history kept, persona tag dropped) before the delete, instead of hitting a foreign-key constraint violation.

## ADR-007: per-session AI provider, and which features actually call AI

- **Context**: ADR-005 assumed one active `AiProvider` bean chosen at startup via `AI_PROVIDER`. Two things outgrew that: wanting a different default per environment (no-cost local iteration vs. a real model in dev), and wanting each *session* — not the whole app — to pick its model, since the app is mobile-first and different readers/devices may want different cost/quality tradeoffs. Separately, Quiz (question generation + grading) and Summary were still mock-style templates/heuristics, explicitly flagged in the contract as a future upgrade.
- **Decision**:
  - All 4 `AiProvider` implementations (`claude`, `openai`, `kimi`, `ollama`) are now always-registered named beans, held by a new `AiProviderRegistry` (`Map<String, AiProvider>`) instead of exactly one being conditionally active.
  - `Session` gained a nullable `aiProvider` column, set once at `POST /sessions` and used for that session's entire lifetime (chat, quiz generation, quiz grading, summary). Omitting it falls back to `booki.ai.default-provider`, set per Spring profile: `ollama` for `local`, `openai` elsewhere (env var `AI_PROVIDER` still overrides either). *(The non-`local` default was `claude` originally; it moved to `openai` once cloud voice shipped, so a single `OPENAI_API_KEY` covers chat and voice.)*
  - The layered-prompt logic (app baseline + Master persona + user's own `systemPrompt`) that only chat used before was extracted from `SessionServiceImpl` into a shared `SessionContextBuilder`, since Quiz and Summary now need the identical concept.
  - Quiz question generation, quiz grading, and summary generation were rewritten to make real `AiProvider.converse()` calls grounded in the relevant page(s)' text plus that shared context, replacing the per-language template banks and keyword-overlap scoring. Grading asks for a strict `CORRECT:`/`SCORE:`/`FEEDBACK:` reply format that gets parsed, degrading to `score=0` if the model didn't follow it (e.g. the offline fallback message).
- **Reasons**: per-session choice matches "different reader, different session, different model" better than one app-wide setting; `ollama` as the `local` default keeps everyday dev free and offline; Quiz/Summary being real AI calls was the natural next step now that the layered-context plumbing already existed for chat.
- **Consequence**: Progress and quiz-correction PDF **reports** deliberately did *not* get this treatment — they're factual recaps of numbers and already-graded Q&A, where a template is more trustworthy than an LLM re-describing them. Only chat, quiz, and summary call AI; reports stay templated.

## ADR-008: conversational capabilities via a provider-neutral intent layer

- **Context**: Quiz, Summary and Explain should be usable *inside* the chat ("ask me a question about this", "summarize before I continue") without the reader navigating to a separate panel — while the existing Quiz panel and Summary modal stay exactly as they are. The clean way to let a model choose a tool is native function/tool calling, but BooKI's `AiProvider` abstraction is a single `String converse(system, context, user)` and the four providers (`claude`, `openai`, `kimi`, `ollama`) each have a different tool-call wire format and a multi-turn tool-result loop. Adding that now would destabilise every provider (the same reason Phase 5 keeps `converse()` non-streaming). Keyword matching (`if message.contains("quiz")`) was explicitly ruled out as fragile.
- **Decision**:
  - A small `ConversationCapability` interface — `name()`, `modelDescription()`, `execute(CapabilityInvocation)` — with one bean per capability (`quiz`, `summary`, `explain`, `mnemonic`). This is **not** an agent framework: a capability returns the reply text for one turn and nothing else.
  - Capabilities **reuse the existing services**: `QuizCapability` → `QuizService.generateComprehensionQuestion(Session)` (extracted from the panel's per-page generator), `SummaryCapability` → `ReportService.generateSummaryText(Session, …)` (the same method behind `POST /summary`). `explain`/`mnemonic` are small prompts on top of the shared `SessionContextBuilder` — nothing existing did them.
  - **Routing is provider-neutral.** `CapabilityRegistry.routerInstructions()` is appended to the session's normal system prompt; when a capability clearly fits, the model replies with *only* `{"capability":"<name>"}`. `parseDirective()` accepts that only if the whole trimmed reply is that JSON, ≤160 chars, and names a registered capability — otherwise the reply is treated as a normal answer. Common chat stays **one** model call; a capability adds a second (its own specialised call).
  - **Quick-action buttons** ("Ask me", "Summarize", …) send their canned text plus an optional `capabilityHint` on the existing `POST /sessions/{id}/messages` — the engine runs that capability directly, no routing call, no separate backend path.
  - **Conversational quiz asks, it does not grade.** The reader's answer and any "give me a hint" are ordinary chat turns (the model has the question in history and the pages in context). Scored `QuizAttempt` rows — and everything Progress/Reports count — stay exclusive to the explicit `POST /sessions/{id}/quiz/answer` flow. This avoids a fragile "is the reader answering a quiz right now?" state machine.
- **Reasons**: works identically on all four providers today; no schema change (`MessageRequest` only gains an optional nullable `capabilityHint`); the Quiz panel / Summary modal and their endpoints are untouched; native tool calling can replace the directive later behind the same `ConversationCapability` interface without touching callers.
- **Consequence**: routing depends on the model emitting the exact directive, so it can miss (it then just answers in prose — still a fine response) or, for `explain`, over-trigger since plain chat already explains; capability `modelDescription()`s are worded narrowly and the buttons give readers a deterministic path. Latency is ~2× on a turn that invokes a capability.

## ADR-009: voice is a cloud, provider-agnostic capability (supersedes ADR-002)

- **Context**: ADR-002 put STT in the browser via `SpeechRecognition`. That API only works on Chromium, is unreliable in an installed PWA, and gives no TTS — and BooKI targets Android/Windows/Linux through responsive web/PWA, so a Chrome-only voice path is not acceptable as the architecture. Voice must also converge with text: same `Message`, same `ConversationEngine`, same session context.
- **Decision**:
  - Two backend interfaces, `SpeechToTextProvider` and `TextToSpeechProvider`, mirroring the `AiProvider` pattern — minimal and synchronous, credentials server-side. First implementation is OpenAI-compatible (`whisper-1` transcription, `/audio/speech` MP3); a Google/Azure/Deepgram impl slots in behind the same interface. Both report `isConfigured()` and are inert (throw `VoiceProviderException` on use) without an API key.
  - `VoiceConversationService` is the voice adapter in front of the engine: `audio → SpeechToTextProvider → ConversationEngine.converse(…, InputType.VOICE) → TextToSpeechProvider → audio`. The transcript goes through the **exact same** engine call as a typed message; history, context and capabilities are all the engine's.
  - Transport: `POST /api/sessions/{id}/voice` (multipart audio in) returns the two persisted `MessageResponse`s plus the reply audio as base64 (or `null`). `GET /api/voice/capabilities` → `{stt, tts}` so the client picks the cloud path or the browser fallback up front. No SSE/WebSocket — REST, like every other endpoint.
  - **STT failure fails the turn** (`VoiceTranscriptionException` → 502, no input to run). **TTS failure is best-effort** — the text reply is already persisted and returned; the client shows text or uses browser `speechSynthesis`.
  - Session language drives STT (and TTS where the provider uses it). The hardcoded `es-ES` in `useVoice` is gone; the fallback recognizer now also follows session language.
  - **Raw audio is never persisted** — it lives only for the request. Upload capped (`booki.voice.max-audio-bytes`, 10 MB); TTS input capped (`tts-max-input-chars`, 1200) so a long summary read aloud can't produce a huge synchronous call.
  - The frontend captures audio with `getUserMedia` + `MediaRecorder` (universal support). `SpeechRecognition` (`useVoice`) is kept **only** as a fallback for browsers without `MediaRecorder` or deployments with no STT provider — it still posts through `POST /messages` with `InputType.VOICE`.
- **Reasons**: works on every modern browser; voice and text share one pipeline and one persistence model; the provider interfaces are shaped so a streaming implementation (Phase 5) is an additive method, not a rewrite; nothing forces WebSocket/WebRTC into the rest of the app.
- **Consequence**: a voice turn costs an STT call + the conversation call + a TTS call, all synchronous — fine for "press, speak, hear the answer", not yet low-latency. Base64 audio in JSON is simple but not streamable; Phase 5 revisits this when there is a concrete streaming requirement. A deployment with no OpenAI key still has working voice via the browser fallback (Chromium only), matching pre-Phase-4 behavior.
- **Confirmed in practice (2026-08-29)**: end-to-end voice turns work correctly on both desktop (Firefox, `https://localhost:5173`) and mobile (Chrome, `https://<LAN-IP>:5173`, self-signed dev cert — see `docs/local-dev.md` "HTTPS for mobile testing"; plain `http://<LAN-IP>` cannot work at all, since `getUserMedia` requires a secure context and that exception doesn't cover a LAN IP). Round-trip latency on PC measured up to ~7s. Root cause, walking `VoiceConversationService.processTurn` (STT → `ConversationEngine.converse` → TTS, three sequential blocking calls, none start before the previous finishes): the LLM call is normally the dominant cost since it returns nothing until the *entire* reply is generated (no streaming), and that cost cascades — a longer LLM reply also means more text for TTS to synthesize afterward. This is the exact "not yet low-latency" consequence above, now observed rather than theoretical; the fix is ADR-010's prepared-but-unbuilt streaming path, planned before Release 1, not a quick patch.
- **New requirement surfaced by real use (2026-08-29)**: voice *input* and voice *output* should be independently toggleable. Motivating case: using session voice input to read/answer a book chapter together with a child, guided by a Profile Master persona (a Quebec-style ortho-pedagogue), where an on-screen text reply is more useful *and* cheaper than a synthesized-audio reply (skips the TTS call entirely). Today `booki.voice.openai.api-key` blank is the only way to disable TTS, and it's global/deployment-wide, not a per-session or per-turn choice. Needs a decision before Release 1: likely a session- or request-level flag that skips the `textToSpeech.synthesize(...)` call in `VoiceConversationService.processTurn` (line ~73) while still accepting voice *input*, returned via the same `VoiceTurnResponse` (`replyAudio: null`, exactly like today's TTS-failure fallback, just intentional instead of an error path).

## ADR-010: streaming-ready interfaces, but no streaming transport yet

**In one paragraph:** "Streaming" here means BooKI's reply arriving word-by-word
as the model writes it (like ChatGPT) instead of appearing all at once after a
pause. Phase 5 did **not** build that. It only shaped the backend Java
interfaces so streaming can be added later without re-architecting anything —
`converse()` and every HTTP endpoint are unchanged, nothing calls the new
streaming code yet. **This ADR has zero frontend impact and nothing to do with
browser support** — that is ADR-002/ADR-009. When streaming is actually built it
will use SSE, which every browser supports.

- **Context**: the target voice experience is "first audible response as early as possible" — incremental STT → streaming LLM → streaming TTS. That needs SSE/WebSocket/WebRTC, none of which BooKI has. The brief is explicit: don't reactive-ify the app, don't add a streaming transport "merely because voice exists", get cloud conversation right first (done, Phases 1–4). Phase 5 is *preparation*: shape the interfaces so streaming implementations drop in later without a rewrite.
- **Decision**:
  - **Optional companion interfaces**, never replacements. `StreamingAiProvider` sits alongside `AiProvider`; `StreamingTextToSpeechProvider` / `StreamingSpeechToTextProvider` alongside their blocking forms. A provider implements the streaming one only if it can; `AiProvider.converse()` and the TTS/STT contracts are byte-for-byte unchanged.
  - **Library-neutral callbacks**, not `Flux`: `TokenStream { onDelta, onComplete, onError }` etc. Reactor may be used inside a provider impl (WebClient already is) but never leaks into a signature — the domain layer stays uncoupled from any streaming library, the same way it's uncoupled from transport.
  - **One entry point that always works**: `AiProviderRegistry.converseStreaming(...)` uses the native streaming impl when the resolved provider has one, otherwise bridges the blocking call as a single delta + complete. Callers never branch on capability; no provider is destabilised.
  - **`ClaudeProvider` is the reference streaming impl** (Anthropic SSE, `content_block_delta`/`text_delta`). It proves the interface shape against a real API. `converse()` was refactored only to share request-body building — same wire call, same behaviour.
  - **`ConversationEngine.converseStreaming(request, ConversationStream)`** is additive next to `converse()`. `ConversationStream` is domain-typed (`onComplete(ConversationResult)`) so the engine's API stays transport-neutral; an SSE/WebSocket controller adapts it later with zero engine changes. Model-driven capability routing still works while streaming via **directive gating**: output is withheld only while the accumulated reply could still be a `{"capability":...}` directive (short, starts with `{`), then flushed live once it can't be; a completed directive runs its capability instead. An explicit `capabilityHint` runs the capability and emits it as one delta (capabilities aren't token-streamable).
  - **TTS/STT streaming interfaces are shape-only** — no implementation. Streaming TTS needs the transport to forward chunks (today a voice reply is base64 in one JSON body); streaming STT additionally needs a streaming *request* (WS/WebRTC). Both land with that transport.
- **Reasons**: when a concrete low-latency requirement appears, the work is "add an SSE endpoint + a streaming provider method", not "re-architect the engine / providers / DTOs". Everything shipped stays synchronous and REST. The gating logic means streaming doesn't force a choice between token-by-token replies and conversational capabilities.
- **Consequence**: `converseStreaming` has no HTTP caller yet — it's exercised only by unit tests (a fake streaming provider). That's the intended state for "preparation"; the risk is the path bit-rotting before it's wired, mitigated by the tests. When SSE arrives it should also carry the persisted message ids to the client (the callback already returns `ConversationResult`).

## ADR-011: PostgreSQL instead of MySQL (supersedes ADR-003)

- **Context**: BooKI is moving to a deployed environment (see `docs/deployment.md`). The database must be a **managed** service — backups, patching and HA handled by the provider, not by us — and, for a dev/pilot with few users, it should fit a real free tier. Every genuinely free managed database today (Neon, Supabase, …) is PostgreSQL; there is no free managed MySQL, and Google Cloud has no free managed PostgreSQL either (Cloud SQL starts ~10 $/mo).
- **Decision**: switch the engine from MySQL 8 to **PostgreSQL 16**. Deployed database on **Neon** (free tier, serverless Postgres); local `dev` on `postgres:16` via Docker Compose; `local` and `test` keep H2 but in **PostgreSQL compatibility mode** so the SQL H2 parses matches the real engine. The migration was cheap: no production data existed anywhere, so the 9 MySQL migrations were **collapsed into a single Postgres-native `V1__init.sql`** (the header comment in that file explains why) rather than rewritten one by one.
- **Reasons**: free managed hosting; standard Postgres wire protocol keeps the door open to Cloud SQL / RDS / Supabase / a VPS later with just `pg_dump`/restore; Postgres' type system (`TEXT`, `TIMESTAMPTZ`, `GENERATED … AS IDENTITY`) is a clean fit for the entities. Portability, not a bet on any one host.
- **Consequence**: the entity `columnDefinition = "LONGTEXT"` hints became `"TEXT"`; timestamp columns are `TIMESTAMP WITH TIME ZONE` to match Hibernate's mapping of `Instant` under `ddl-auto: validate`. `docker compose` now exposes port 5432, not 3306. No Java service/logic changes — the switch is confined to build config, `application.yml`, the migration, `docker-compose.yml`, and the column-type hints.

## ADR-012: S3-compatible object storage behind `StorageAdapter` (implements ADR-004)

- **Context**: on Cloud Run the container filesystem is wiped on every deploy and not shared between instances, so uploaded PDFs and generated reports need to live in object storage. ADR-004 already put every file access behind a seam for exactly this.
- **Decision**: add `S3StorageAdapter` (`booki.storage.driver=s3`) using the **AWS SDK v2 `S3Client`** with `endpointOverride` + `forcePathStyle`. "S3" here is the *protocol*, not the vendor: the same adapter targets **Google Cloud Storage** (its S3 XML API + HMAC keys — the deployed target), Cloudflare R2, MinIO or AWS, selected by `S3_ENDPOINT`. Deployed credentials come from env vars / the platform secret store. Local dev/testing uses **MinIO** in `docker-compose` (with a one-shot bucket-creator); the default stays `local` so MinIO is opt-in. The sync `url-connection-client` HTTP client is used and the default Netty async client excluded, to keep the dependency/image footprint down.
- **Reasons**: the S3 API is the de-facto standard — six+ providers speak it — so this is the *most* portable choice, more than a cloud-specific SDK or a mounted volume. One `@ConditionalOnProperty` `@Component` per driver, no factory.
- **Consequence**: objects are held whole in memory (`RequestBody.fromBytes` / `ByteArrayResource`), bounded by the 50 MB multipart cap — acceptable for a pilot; a presigned-URL redirect is the escape hatch. `documents.file_path` / `sent_reports.file_name` now store an opaque key (`documents/…`, `reports/…`), not a path, so rows are backend-agnostic.

## ADR-013: frontend and backend are separate origins in production

- **Context**: deployed, the frontend is a static build on Firebase Hosting and the backend a container on Cloud Run — different origins. Local dev keeps the Vite dev-server proxy (`/api` → `localhost:8080`), which papers over cross-origin concerns.
- **Decision**: the frontend reads a single build-time `API_BASE` (`config/endpoints.ts`) = `import.meta.env.VITE_API_BASE_URL || '/api'`. Unset → `/api` (local, proxied); set → the backend's absolute API root (deployed). The backend's CORS allow-list is already env-driven (`booki.cors.allowed-origins` → `CORS_ALLOWED_ORIGINS`), so the deployed origin is configuration, not code. No same-origin bundling (backend serving the SPA) — keeping them independently deployable is worth one env var and one CORS line.
- **Reasons**: a static frontend on a CDN and a scale-to-zero API container have different lifecycles, scaling and cost models; coupling them into one deployable to avoid CORS would trade that away for very little.
- **Consequence**: a deployed frontend build is pinned to one backend URL (rebuild to repoint). `getDocumentFileUrl` also uses `API_BASE` because react-pdf fetches that URL directly, outside axios.

## ADR-014: minimal first deployment, hardening deferred

- **Context**: the first deployment is for a small trusted group, not a public launch.
- **Decision**: deploy the minimum that stands up and is usable — PostgreSQL (Neon free), object storage (GCS), backend on Cloud Run (`min-instances=0`), frontend on Firebase Hosting, one `deploy.yml` GitHub Action, and Actuator health with a per-dependency breakdown (`db`, `storage`, `diskSpace`, `ssl`) plus liveness/readiness probes. **Deferred** (Phase 7): Sentry, DB backups beyond Neon's 7 days, per-user AI rate limiting, CSP/security headers, auth hardening, Workload Identity Federation. A GCP service-account JSON key (one GitHub secret) is accepted over WIF for now.
- **Reasons**: most of the hardening only pays off with real traffic. Env-driven config, the storage seam and the CI split mean each deferred item is an additive change later, not a rewrite.
- **Consequence**: `/actuator` is public (`SecurityConfig`'s `anyRequest().permitAll()`), the OpenAI key has no in-app spend guard (set a billing alert on it), and a lost SA key means full project access. Acceptable at this scale; all on the Phase 7 list before opening up.
