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
- **Decision**: all file access goes through a `StorageAdapter` interface (`com.booki.storage`) — `put(key, bytes, contentType)` / `get(key)` / `delete(key)`, addressing everything by an opaque forward-slash key (`documents/…`, `reports/…`). The key is what's persisted (`documents.file_path`, `sent_reports.file_name`), never an absolute path; reads are handed out as a Spring `Resource`, never a `File`. The implementation is chosen by `booki.storage.driver`. (No page-level text extraction into the database anymore — a document-capable AI provider reads the stored PDF itself; see ADR-025.)
- **Reasons**: the seam means the storage backend is a deployment choice, not a code change — controllers, the frontend and the DB never see it.
- **Consequence**: `LocalStorageAdapter` (default, `driver=local`) writes under one directory (`booki.storage.local-path`, default `./storage`) and is fine for local dev and single-instance runs; it does not survive an ephemeral redeploy and is not shared between instances. See ADR-012 for the S3-compatible backend that removes that limitation.

## ADR-005: OpenAI as the initial AI provider

- **Context**: basic Kimi and OpenAI packages are available.
- **Decision**: OpenAI by default, behind an abstract interface for swapping providers.
- **Reasons**: stable API, extensive documentation, an affordable model (`gpt-4o-mini`).
- **Consequence**: can be changed via configuration without touching session logic.
- **Superseded by ADR-007**: the "one active provider, chosen at startup" model this ADR assumed no longer holds — see below.

## ADR-006: Profile Masters are per-user, not global (superseded by ADR-015)

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
  - Capabilities **reuse the existing services**: `QuizCapability` → `QuizService.generateComprehensionQuestion(Session, pageContextText)`, `SummaryCapability` → `ReportService.generateSummaryText(Session, …, pageContextText)`. Their original no-context signatures were superseded by ADR-021 so capabilities use the engine's bounded or explicitly selected pages. `explain`/`mnemonic` are small prompts on top of the shared prompt assembly — nothing existing did them.
  - **Routing is provider-neutral.** For a chat turn the system prompt gets `PromptAssembler.chatRoutingSection()` (the editable `capability_routing` slot + its locked JSON contract) followed by `CapabilityRegistry.routerInstructions(enabled)` (the dynamic list of the session's enabled capabilities); when a capability clearly fits, the model replies with *only* `{"capability":"<name>"}`. `parseDirective()` accepts that only if the whole trimmed reply is that JSON, ≤160 chars, and names a registered capability — otherwise the reply is treated as a normal answer. Common chat stays **one** model call; a capability adds a second (its own specialised call).
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

## ADR-014: minimal first deployment, hardening deferred (partly addressed by ADR-016)

- **Context**: the first deployment is for a small trusted group, not a public launch.
- **Decision**: deploy the minimum that stands up and is usable — PostgreSQL (Neon free), object storage (GCS), backend on Cloud Run (`min-instances=0`), frontend on Firebase Hosting, one `deploy.yml` GitHub Action, and Actuator health with a per-dependency breakdown (`db`, `storage`, `diskSpace`, `ssl`) plus liveness/readiness probes. **Deferred** (Phase 7): Sentry, DB backups beyond Neon's 7 days, per-user AI rate limiting, CSP/security headers, auth hardening, Workload Identity Federation. A GCP service-account JSON key (one GitHub secret) is accepted over WIF for now.
- **Reasons**: most of the hardening only pays off with real traffic. Env-driven config, the storage seam and the CI split mean each deferred item is an additive change later, not a rewrite.
- **Consequence** *(as of ADR-016, most of this is now done)*: the OpenAI key still has no in-app spend guard (set a billing alert on it), and a lost SA key still means full project access. `/actuator` lockdown, security headers/CSP, exception-message sanitization, provider timeouts, transactions, validation, and the deleted-user token check landed in the post-Kimi hardening pass — see ADR-016. Still deferred: per-user rate limiting, `HttpOnly`-cookie auth, refresh tokens, Workload Identity Federation, a real `prod` profile.

## ADR-015: AI Profiles replace Profile Masters + user context (supersedes ADR-006)

- **Context**: the old model had three weak, disconnected prompt sources — a one-sentence app baseline, a 1–2 sentence Profile Master persona, and a free-text `user.systemPrompt`. Quiz/summary/explain each carried their own hardcoded one-line instruction in Java. "Difficulty" was a bare `easy|medium|hard` label the model interpreted however it wanted. Nothing was tunable without a redeploy, precedence between the layers was undefined, and the user could not see or shape most of it.
- **Decision**: replace all three with one object — the **AI Profile** — that holds every editable prompt a session runs on (persona, reader context, three difficulty levels, per-function instructions, capability routing), plus structured `readerLevel` and `enabledCapabilities`. A fixed app-owned **core** (safety, grounding, output language, the precedence rule) stays outside it. A session points at exactly one profile and keeps it. Every account is seeded at registration with an editable copy of each shipped template; the templates are hidden originals used only to seed copies and to "restore to original", so a session never runs on a read-only profile. The full mechanics — slots, precedence, assembly, lifecycle, the "template updates never touch user profiles" rule — are in **`docs/prompts.md`**.
  - Key choices and why: **one object per session**, not a persona library + learner profile + function settings, because a profile is already per study-context. **Structured where the machine parses (output formats, the capability list), free text where the human tunes (tone).** **A profile is autonomous and never rewritten by an update** — zero surprises, and no reconciliation logic in the migration. **Provider-neutral routing** via a JSON directive, not native tool-calling.

- **Delivered** in four backend commits after the frontend + mock: `ai_profiles` + `ai_profile_slot_prompts` tables (folded into `V1__init.sql` — no prod data, wipe the DB before deploying), templates in a `SlotPromptCatalog` class (not the DB), `PromptAssembler` replacing `SessionContextBuilder`, `/api/ai-profiles` CRUD, quiz/summary/capabilities reading their SlotPrompts, `enabledCapabilities` filtering the router, `/api/profile-masters` + `ProfileMaster*` + `user.bio`/`system_prompt` removed. No data migration (test data only). Mechanics: `docs/prompts.md`.

- **Consequence**: `GET /sessions/{id}/context` returns every layer BooKI reads, grouped, not just the editable three. Deferred: a global "advanced prompts" screen (the per-profile editor covers it); switching a running session's profile (create a new session). The professional rewrite of the default prompt text (core, rubrics, `fn_*`, personas, reader scaffold) landed later, alongside wiring the editable `capability_routing` body into the chat prompt — see `docs/prompts.md`.

## ADR-016: post-Kimi quality & security hardening pass

- **Context**: an external review (`docs/evaluation-kimi.md`, 2026-09-02) scored the MVP well on architecture but flagged concrete gaps — no `@Transactional` on multi-write services, no `WebClient` timeouts, a predictable default JWT secret, a public `/actuator` with full detail, an exception handler leaking `ex.getMessage()`, no request-body validation on several endpoints, prompt text concatenated without delimiters, no ESLint config, demo credentials in the frontend bundle, no security headers on the deployed frontend, and a deploy workflow with no test gate. Most were on ADR-014's "Phase 7" list; the product still isn't public, so *some* permissiveness stays.
- **Decision**: do a focused hardening pass in three committed stages — (1) frontend quality/architecture, (2) backend quality (transactions, timeouts, typed DTOs, validation), (3) security + details, frontend and backend — **without** the changes that would complicate day-to-day dev or the still-pending test/refactor work.
  - **JWT**: no predictable default. `JwtUtil` signs with a random ephemeral key + loud warning when `JWT_SECRET` is unset/placeholder/short — zero-setup local dev, unusable-by-design in production (per-instance, non-persistent), so a real deploy must set it.
  - **`/actuator`**: `health` + probes public (Cloud Run), everything else authenticated; `show-details: when-authorized`. Swagger/springdoc disabled outside the `local` profile. `anyRequest()` → `authenticated()`.
  - **Auth filter** rejects a token whose user no longer exists (`existsById`).
  - **Exception handler**: uncaught `RuntimeException` → fixed neutral `500`, detail only in logs. CORS allowed-headers is an explicit list, scoped to `/api/**`.
  - **Prompt injection**: page text fenced (`<<<BEGIN/END DOCUMENT>>>`) + a core-prompt line that document/reader text is material, not instructions. Upload validated by `%PDF-` magic bytes; STT MIME allowlist.
  - **Resilience**: `@Transactional(readOnly=true)` on association-walking reads, `@Transactional` on multi-write ops (with storage cleanup on upload rollback), *not* on the methods that span a model call. Shared `OutboundHttp` connect/read/call timeouts on every provider `WebClient`.
  - **Validation**: `@Valid` on every write body; `@Size`/`@Pattern`/`@Email`/`@Min`/`@Max` on free-text and enum-ish fields. `Map<String,Integer>` body → typed `UpdateCurrentPageRequest`.
  - **Frontend**: demo login gated behind `import.meta.env.DEV` (stripped from prod); ESLint config added + wired into CI; `firebase.json` security headers (CSP, HSTS, X-Frame-Options, …); markdown links get `rel="noopener noreferrer nofollow"`; token read from an in-memory holder, not `localStorage`, per request; stored-auth shape validated; bearer token withheld if a prod build targets plaintext `http`; client-side upload type/size check; `useOutsideDismiss` for popover a11y.
  - **CI**: `ci.yml` frontend job runs lint + type-check + build; `deploy.yml` gains a `verify` gate (backend tests + frontend checks) before it deploys — the Dockerfile builds `-x test` and deploy ran on push-to-`main` with nothing in front of it. `npm audit fix` (non-breaking); `dev-dist/` untracked.
- **Deliberately still open** (not blocking for a non-public product): `HttpOnly`-cookie auth instead of `localStorage`, account-enumeration message on register, model-JSON capability routing determinism, a real `prod` profile (deploy runs `dev`), Bucket4j rate-limiting, `react-router` 6→7 (breaking; open-redirect advisory), `generateSummary` returning `Object`, enum `CHECK` constraints, and broader backend test coverage (controllers `@WebMvcTest`, repos `@DataJpaTest`, security). *(The dead `sessions` columns were dropped in ADR-017; the reader-profile refactor + its service/prompt tests landed there too.)*
- **Consequence**: `spring.jpa.open-in-view` is still on and masks any read path missed by an explicit `@Transactional`; fully removing the lazy-init risk means turning OSIV off plus a test pass, later. The CSP is untested against a real deploy — iterate with `Content-Security-Policy-Report-Only`. Local `dev` profile (`./gradlew bootRun`, Docker Postgres) now needs `JWT_SECRET` in `.env` if you want persistent tokens; `bootRunLocal` doesn't.

## ADR-017: reader context is its own entity again (Reader Profiles) (adjusts ADR-015)

- **Context**: ADR-015 folded the reader's own context into each AI Profile as a `reader_context` slot — "the profile is already per study-context". In use that was wrong: the reader is the same person across personas, but their context differs by *subject* (learning a language vs. reading philosophy vs. studying science), not by which tutor persona they pick. Every seeded AI Profile shipped with an empty `reader_context`, and the editor offered "copy reader context from another profile" — a control that only makes sense if reader context varied by persona. `reader_level` (a property of the reader) also sat on the AI Profile.
- **Decision**: split the reader context back out into a **Reader Profile** — a named, reusable entity ("Languages", "Sciences", "Philosophy") holding `context` + `readerLevel`, with **no association to any AI Profile**. The **session** picks one AI Profile *and* one reader profile at creation (`session.aiProfileId`, `session.readerProfileId`); the reader profile is resolved from the session at turn time. A reader profile is **shared** — editing it changes it for every session that uses it. (A first pass paired the reader profile to the AI Profile via `AiProfile.readerProfileId`; the user rejected that — the master and the reader are orthogonal, so the choice belongs to the session.)
  - There is one built-in **read-only** reader profile ("General reader", an adaptive general-purpose scaffold, `readOnly`, `isDefault` until the user sets their own). You don't edit it — you **duplicate** it (like the AI Profile factory templates). `readerLevel` lives here and is also written into the assembled reader-context layer (`Reader level: intermediate.`), not just used as a difficulty hint.
  - Editing both kinds happens on the AI Profiles screen: the AI Profile editor plus a standalone "Reader profiles" section (pick which to edit / rename / set level / edit the shared context / save / duplicate / delete). The `reader_context` slot and the "copy from another profile" control are gone. `readerLevel` and `readerProfileId` are gone from the AI Profile entirely.
  - "Persona" is relabelled **"Master persona"** (the label didn't convey that it's the assistant's character — name/gender, tone, pedagogy). *(Reverted to plain "Persona" by ADR-018.)* The AI Profiles editor drops the "Advanced" fold — all slot groups (persona, difficulty, function prompts, capability routing) are always visible.
  - `CreateSessionModal` gains a reader-profile picker; `SessionSidebar` and the ℹ context panel show both the AI Profile and the reader profile, differentiated.
- **Delivered** across frontend, Node mock **and the Spring backend**: `/reader-profiles` CRUD with a read-only factory reader (`user_id NULL`, seeded in `V1__init.sql`), `sessions.reader_profile_id`, `SlotKey.READER_CONTEXT` + `ai_profiles.reader_level` removed, `ReaderProfileService.resolveFor(session)` feeding `PromptAssembler`. While rewriting `V1__init.sql` the two dead `sessions` columns ADR-016 had deferred (`config_json`, `completed_at`) were dropped too. Docs: `docs/prompts.md`, `docs/backend.md`, `docs/openapi.yaml`.
- **Consequence**: `ai_profiles` loses `reader_level`; `sessions` gains `reader_profile_id` (nullable, `ON DELETE SET NULL` — a deleted reader profile falls back to the default at read time) and loses `config_json` / `completed_at`. `SlotKey` has 10 values, not 11. `GET /sessions/{id}/context` returns a `reader` group layer sourced `Reader profile "<name>"` plus top-level `readerProfileId` / `readerProfileName`. `V1__init.sql` changed → **the DB must be wiped before deploying**.

## ADR-018: "Reading setup" — UI wording and layout only (adjusts ADR-017, frontend-only)

- **Context**: the AI Profiles screen tested confusingly. "AI Profile" / "master" / "Master persona" were three names for one thing; the page was titled "Profiles" but also edited reader profiles, nested under the tutor editor's sidebar; "difficulty" appeared in three places with two vocabularies (`beginner/intermediate/advanced` vs `Easy/Medium/Advanced`); the reader Save/Delete were invisible whenever the read-only built-in reader was selected (which was the default).
- **Decision** — **labels and layout in the frontend only. No backend, API, route, entity, or prompt-assembly change.** The code keeps `AiProfile` / `SlotKey` / `/ai-profiles`.
  - "AI Profile" → **"tutor profile"** in all UI copy; "Master persona" → just **"persona"** (reverting that part of ADR-017); the page → **"Reading setup"**; `ProfilePage` and its menu link → **"Account details"**.
  - The page becomes **two peer tabs** ("Tutor profile" / "Reader profile"), each: selector + `New` / `Duplicate` / `Delete` + editor + a bottom `Save changes`. Tutor `New` copies the user's default (there is no blank template endpoint); reader `New` is a blank editable copy of the scaffold. The reader tab lands on one of the user's own profiles; shipped read-only profiles display their full content in read-only mode with a duplication callout.
  - Difficulty: one vocabulary in the UI — the reader's stored `readerLevel` (`beginner|intermediate|advanced`, unchanged) is shown as `Easy|Medium|Advanced`; "Reader level" field → "Starting level"; short copy in each of the three places states its role (reader = preset, session = active level, tutor profile = the definition).
  - Session sidebar shows `Tutor: <name>` / `Reader: <name>` chips. Long help text is a small "?" disclosure, kept to two.
- **Consequence**: `docs/frontend.md`, `docs/prompts.md` (a "UI terminology" note draws the line between code/API names and UI labels). Nothing to migrate or redeploy.

## ADR-019: versioned prompt catalog and language-support shipped profiles

- **Context**: the production core, shared SlotPrompt defaults and shipped tutor
  personas were long Java string literals in `SlotPromptCatalog`; locked frames
  were mixed into `SlotKey`; and the Node mock duplicated the same prose. This
  made prompt review awkward and encouraged the UI-only mock to look like a
  second production source. A language-aware setup was also needed for readers
  who may need help understanding or expressing spoken or written language,
  without assigning a diagnosis or equating communication difficulty with low
  intellectual ability.
- **Decision**:
  - The authoritative production wording moves to the versioned
    `backend/src/main/resources/prompts/catalog.yml`. It holds the fixed core,
    shared editable starting texts, shipped tutor personas, and optional
    per-template prompt overrides.
  - `SlotPromptCatalog` loads and validates the catalog at startup. Unknown or
    missing slots, duplicate template keys, blank required text, or anything
    other than exactly one default tutor template fail startup.
  - Java retains `SlotKey`, labels and locked output frames because parsers and
    API behavior depend on those contracts. `application.yml` stores only the
    catalog resource location; prompt bodies are not environment variables.
  - The Node mock remains an intentionally simplified UI-design fixture. It is
    not synchronized with, tested as, or documented as the production prompt
    source.
  - Chat assembly now accepts the live capability description as a parameter
    and places all trusted routing instructions before session facts and the
    final fenced document block. The untrusted document stays last, improving
    inspectability and keeping the reusable instruction prefix stable.
  - A `Language & Learning Guide` tutor template is shipped with its own Easy,
    Medium and Advanced rubrics. Conceptual demand increases across the three
    levels while communication supports remain available. `V1__init.sql` also
    seeds a read-only `Language-support reader` template for support with spoken
    or written understanding and expression. Both separate intended meaning from
    language form, provide graduated response supports, and explicitly avoid
    diagnosis, infantilization, or assumptions about intelligence. The reader
    template has no `readerLevel`: support needs and difficulty remain orthogonal.
  - New tutor template keys are backfilled for existing users as autonomous
    copies. Existing prompts and the user's chosen default are never rewritten.
- **Consequence**: prompt changes are ordinary reviewable YAML diffs and carry a
  catalog version. Any `V1__init.sql` change still follows the project's current
  pre-production rule: wipe the target database so V1 runs again. The catalog
  location can be overridden with `BOOKI_PROMPT_CATALOG` for a controlled test,
  but prompt bodies are not placed in environment variables.

## ADR-020: provision an editable default reader profile

- **Context**: a newly registered account had only the shared, read-only reader
  templates. The Reader profile tab therefore opened on "General reader" with a
  callout instead of an editable form; the user had to press `New` before the
  intended generic reader setup existed. The UI already preferred an owned
  profile, so this was a lifecycle omission rather than a selector bug.
- **Decision**: registration provisions one owned `My reader profile`, copying
  `context` and `readerLevel` from the shipped "General reader", and stores it as
  the account default. Creation without an explicit `fromId` also copies the
  current General reader scaffold instead of a divergent hard-coded copy, with a
  small fallback used only when migrations are disabled in tests.
- **Consequence**: the Reader profile editor has a usable editable form on first
  visit, and a new session resolves to an owned reader profile by default. The
  shipped General and language-support profiles remain shared and read-only.
  The General reader seed in `V1__init.sql` is the canonical scaffold copied at
  registration.

## ADR-021: sessions are open-ended reading journeys

- **Context**: choosing pages 10–12 when opening a session made page 12 a hard
  navigation wall. The range was serving three unrelated purposes at once:
  viewer limits, reading progress, and AI/quiz context.
- **Decision**: session creation asks only where reading starts. The PDF viewer
  can navigate the whole document; `endPage` grows monotonically to the furthest
  page reached while `currentPage` remains the page currently displayed. Normal
  chat receives the current page plus at most seven recent pages, still subject
  to the character cap. A written request such as “pages 10 to 12” selects that
  explicit range (up to 20 pages). Quiz and summary screens choose their own
  explicit ranges within the pages read so far.
- **Consequence**: reaching the previous end no longer stops reading, progress
  is measured from the starting page toward the document's real final page, and
  long sessions do not silently send the whole PDF to the model. Existing
  `start_page`, `end_page`, and `current_page` columns are reused, so no database
  migration is required.

## ADR-022: every new account starts with the BooKI guide in its library

- **Context**: a first-time user landed on an empty library with nothing to
  open, and the landing "Learn more" button did nothing. There was also no
  in-product explanation of what BooKI does or how to use it — only the
  developer README. A short illustrated guide exists (`docs/booki-guide.html`
  → `docs/booki-guide.pdf`, built by `scripts/build-guide.mjs`); the question
  was how a user meets it.
- **Decision**: the guide ships as a real PDF with selectable text, so BooKI
  can read it like any other document. `AuthServiceImpl.register` publishes a
  `UserRegisteredEvent`; `WelcomeDocumentProvisioner` handles it with
  `@TransactionalEventListener(AFTER_COMMIT)` + `@Async` (`backgroundTasks`
  pool, `config/AsyncConfig`), so the PDF parse + blob write run off the request
  thread, after commit — a warm registration stays ~140 ms. The controller is
  untouched. It imports the bundled `welcome/booki-guide.pdf` via
  `DocumentService.importPdf(userId, title, bytes)` (the shared core extracted
  from `uploadDocument`). Any failure is logged and swallowed; it is idempotent
  (`documents.existsByUserIdAndTitle`) and gated by
  `booki.welcome-document.enabled`
  (`WELCOME_DOCUMENT_ENABLED`, default `true`; the integration suite sets it
  `false` because those tests assert on empty libraries — `WelcomeDocumentIT`
  turns it back on). The landing "Learn more" button links to the same file
  served statically from `frontend/public/booki-guide.pdf`.
- **Consequence**: a new user can immediately open the guide, read it, quiz
  themselves on it, or ask BooKI "how does this work?" — the document appears in
  the library within ~1 s of landing on home. Already-registered accounts are
  not backfilled; a seed dropped by a shutdown mid-task is not retried (the pool
  drains on shutdown, so this is a narrow window). Three copies of the PDF are
  committed (`docs/`, `frontend/public/`, `backend/src/main/resources/welcome/`)
  and kept in sync by the build script, since deploy builds do not run it.

## ADR-023: quiz grading is a teaching moment, on one score scale

- **Context**: the in-session quiz had three problems seen in use. (1) The quiz
  prompt allowed multiple-choice questions ("include choices as a), b), c)") but
  the UI only has a free-text box, so the reader got an a/b/c question, typed
  prose, and the grader replied "try picking a), b), or c)" — a dead end. (2)
  Grading told the reader to "try again" without ever stating the answer, and
  the textarea is disabled after grading, so there was nothing to try. (3) The
  model returned `CORRECT` (yes/no) and `SCORE` (0–1) independently, so the
  correction report could show "1 of 3 correct" next to a 62% average — two
  numbers telling different stories.
- **Decision**: catalog `1.3.0`. `fn_quiz_question` asks for one **open**
  question, no options. `fn_answer_grading` is reframed as teaching: FEEDBACK is
  two-to-four warm sentences that name what the reader got right, then give the
  missed or misread part **with the correct information from the page**, and
  never says "try again". The grading reply is now `SCORE` + `FEEDBACK` only;
  `QuizServiceImpl` derives `correct = SCORE ≥ 0.6`, so the report's
  correct-count and average score are two views of one scale. The Progress
  panel's "Quizzes taken" (which counted answered questions, not rounds) is
  renamed **"Quiz questions answered"** everywhere (`questionsAnswered`).
- **Consequence**: a reader finishes every question knowing the answer, in a
  supportive voice; the correction report is internally consistent. Prompt
  wording is a reviewable YAML diff; existing users keep their own edited copies
  (only new accounts seed `1.3.0`). No schema change — `quiz_attempts.correct`
  is still stored, just computed from the score. The Node mock's fixtures were
  updated to match; it still only word-matches, so its feedback can't quote the
  page.

## ADR-024: AI activities run on a reader-controlled page range, not the reading marker

> Superseded in part by ADR-025 (no more per-page text extraction) and ADR-026
> (the range now governs plain chat too, pins are exact — no more auto-growth
> past a pin, and voice quick-actions are wired through). Read this ADR for the
> original "why a shared range" motivation; read ADR-025/026 for what's true
> today.

- **Context**: the panel quiz and the summary modal were bounded to
  `startPage..endPage` — "the pages reached so far". Tying the AI activity scope
  to reading progress broke in use: a just-opened session has `endPage = 1`, so
  a quiz could only cover page 1; the old "one question per page" cap turned a
  request for 3 questions on a 1-page range into 1 question; each panel carried
  its own range selector; and the chat quick-actions (Ask me / Explain /
  Summarize / Mnemonic) ignored any range entirely and used the reading
  position, so tapping "Ask me" while reading chapter 3 of a child's history
  book produced a question about page 1.
- **Decision**: an **activity page range** — one value shared by the panel quiz,
  the summary modal and the chat quick-actions. It is **not** a session field:
  it lives in a React context (`ActivityRangeContext`, scoped per session id,
  not persisted). Default = page 1 to the furthest page read, following reading
  progress; once the reader edits the steppers it pins in place, though the end
  still extends if they later read past it. The steppers are a single bar above
  the sidebar tabs (`ActivityRangeBar`), removed from the individual panels.
  Every activity call carries its own `startPage`/`endPage`: the quiz and
  summary endpoints already accepted them; `POST /sessions/{id}/messages` (and
  `ConversationRequest` → `ConversationEngine`) gains optional
  `pageStart`/`pageEnd`, sent only for quick-actions — **plain chat text stays
  anchored on the reading position** (a range typed into the message still wins
  over both). The backend validates against the whole document (`1..pageCount`)
  and **clamps rather than rejects** a stale range. Quiz question count is
  decoupled from page count: N questions are spread evenly across the range
  (`i * (n-1) / (N-1)`), and a 1-page range still yields N. The summary book
  excerpt is capped (`SUMMARY_EXCERPT_CHAR_BUDGET`, ~12k chars) by even-sampling
  pages, so a wide range cannot blow up the request.
- **Consequence**: activities obey a range the reader owns, independent of where
  they navigate — "quiz me on chapter 3" no longer leaks chapter 4. No schema
  change: `Session.endPage` is now purely a reading-progress marker (still grows
  as pages turn, still shown as "read so far"). The range is per-tab state — a
  reload re-derives it from reading progress. Voice quick-actions still use the
  reading position (not wired through). PDF text is still extracted for every
  page at upload, now in one `PDFTextStripper` pass instead of a per-page loop.

## ADR-025: a document-capable provider reads the PDF itself — no more per-page text extraction

> Superseded in part by ADR-028: the "upload once per provider, reference by
> file id forever after" mechanism described below turned out to still bill
> and re-process the *whole* book on every single turn (referencing an id
> avoids resending bytes, not re-reading them) — reported directly as "loading
> the whole book on every chat message". ADR-028 replaces it with physically
> cutting just the requested range on every turn. The motivation below (real
> pages over extracted text) still holds; the delivery mechanism doesn't.

- **Context**: BooKI extracted every page's text at upload (`PDFTextStripper`
  into `DocumentPage.extractedText`) and sent that plain text to whichever AI
  provider a session used. For a page whose content is a scanned image, a map,
  or a table baked into a graphic — not a real text layer — the stripper
  returns nothing, and the session silently got "empty pages": no OCR, no
  fallback, no warning. Two OCR-based fixes were built and discarded before
  landing here (Tesseract via tess4j; an AI-vision call per page) — both still
  translate the page into something lossy before the answering model ever sees
  it, and the owner's actual test book (a history text with maps and tables)
  needed the real page, not a translation of it.
- **Decision**: stop extracting text at import. `DocumentServiceImpl.importPdf`
  only reads the page count now; `document_pages` and `DocumentPage` are gone.
  `AiProvider` gains an optional capability: `supportsDocuments()`,
  `uploadDocument(bytes, title)`, `converseWithDocument(systemPrompt, history,
  userMessage, fileId, startPage, endPage)`. `ClaudeProvider` and
  `OpenAiProvider` implement it — each uploads the PDF **once** per document,
  the first time that provider is used with it, via that provider's own Files
  API (`documents.claude_file_id` / `documents.openai_file_id` cache the id so
  it's never re-uploaded); every later turn just references the id and states
  which pages this turn is about, as an instruction ("use only pages X to Y"),
  not a hard restriction — the model has the whole book, the same as a person
  handed the whole book and asked to focus one chapter. `OpenAiProvider` talks
  to the Responses API (`/v1/responses`) for this specifically — Chat
  Completions, which it still uses for plain `converse()`, has no file-input
  shape — so it is genuinely calling two different OpenAI endpoints depending
  on the turn. `KimiProvider` and `OllamaProvider` don't implement the
  capability (`supportsDocuments()` stays `false`): for those two, and only
  those two, `ActivityContentService` falls back to extracting plain text from
  the requested range on the fly, via PDFBox, uncached — cheap for the small
  range one turn actually asks for, no OCR of any kind. One class,
  `ActivityContentService` (new, `com.booki.ai`), is the single place that
  decides document-reference-vs-plain-text and dispatches the model call;
  `QuizServiceImpl`, `ReportServiceImpl`, `ConversationEngine`, and the
  Explain/Mnemonic capabilities all go through it, so there is exactly one
  place this logic lives, not four.
- **Consequence**: no more silently empty pages — every provider that can read
  a PDF gets the real thing, tables/maps/images included; Kimi/Ollama degrade
  to text-only, same ceiling as before this ADR, not worse. Import is now
  effectively instant regardless of book size (page count only). Quiz
  generation changed from N sequential per-page AI calls to **one** call asking
  for N `PAGE:`/`QUESTION:` blocks spread across the range — fewer round trips,
  and the model can no longer be handed a single page's text in isolation, only
  the real range instruction. The "purest information, no translation" choice
  is a real trade-off the owner made deliberately: it costs more per call than
  cached plain text (an attached document is priced/processed like real input
  every time it's referenced — Claude's document block carries an ephemeral
  `cache_control` breakpoint to blunt this within one conversation; OpenAI has
  no equivalent lever exposed here) and is measurably slower to answer from
  than a short text snippet, especially the first turn on a new document
  (upload latency) — accepted in exchange for never mis-reading a table or
  losing a map. `documents.claude_file_id`/`openai_file_id` are workspace-wide
  per Claude's Files API terms — never accept a file id from an untrusted
  source; these are only ever written by `ActivityContentService` itself.

## ADR-026: the activity page range governs every turn — chat and voice included

- **Context**: three range-resolution rules coexisted in `ConversationEngine`
  after ADR-024 shipped, and disagreed with each other in practice: a regex
  matching "pages 4 to 6" typed into the message text; the shared activity
  range for a quick-action, silently truncated to its last 20 pages; and,
  for plain typed chat with neither of those, a sliding window of the last 8
  pages ending at the reading position. The owner's own report — "sometimes
  more pages, sometimes fewer, sometimes the whole book, sometimes just page
  1" — was each of these three rules firing on different turns of the *same*
  session. Separately, `ActivityRangeContext`'s pinned range always re-grew to
  at least the furthest page read (`Math.max(pinned.end, reached)`), so editing
  the end **down** below a page already read had no visible effect — reported
  directly as "it won't let me edit the range."
- **Decision**: delete all three chat-specific rules. `pageStart`/`pageEnd`
  become **required** on every conversational turn — `MessageRequest`,
  `ConversationRequest`, and the voice multipart endpoint (`VoiceController`,
  `VoiceConversationService.processTurn`) all gained `@NotNull`/required
  `pageStart`/`pageEnd`; there is exactly one range concept in BooKI now, no
  per-message override, and voice quick-actions are wired through (ADR-024 had
  left them out). `ConversationEngine.resolveActivityContent` clamps whatever
  the frontend sends to `1..pageCount` and resolves it through
  `ActivityContentService` — same path as Quiz/Summary. On the frontend,
  `ActivityRangeContext`'s pinned range is now **exactly** what the reader set,
  full stop, re-editable at any time; it no longer silently floors the end at
  the furthest page read. `ActivityRangeBar`'s "reset" is the only way back to
  auto-following reading progress. `ChatPanel`, `QuizPanel`, and `SummaryModal`
  additionally refuse to run an action on a degenerate, never-adjusted 1-page
  range (unpinned, `end - start + 1 < 2`) — the generate/send controls disable
  with a short prompt to adjust the range above, rather than silently running
  on a range nobody chose.
- **Consequence**: one range, one behavior, everywhere — a wide range set once
  is honored by chat, quiz, and summary alike, with no hidden ceiling. The
  trade-off the owner explicitly accepted: a pinned range no longer grows on
  its own as the reader keeps reading past it (the ADR-024 behavior); widening
  it again is a manual edit, or "reset" to resume auto-following. `MessageRequest`
  documents (`docs/openapi.yaml`) and this doc both needed the update — the old
  wording ("optional... plain chat stays on the reading position") describes
  behavior no client should rely on anymore.

## ADR-027: real email delivery for sent reports, degrading to "simulated" when unconfigured

- **Context**: "email" a sent report (progress/quiz/summary PDF) always just
  set `simulated: true` — no SMTP transport existed at all, by design, until
  the owner asked for real delivery once the harder architecture work (ADR-025/026)
  was done.
- **Decision**: `spring-boot-starter-mail` + a new `ReportEmailSender`
  (`com.booki.email`) wrapping `JavaMailSender`. Configured via
  `spring.mail.host/port/username/password` and `booki.email.from`
  (`SMTP_HOST`/`SMTP_PORT`/`SMTP_USERNAME`/`SMTP_PASSWORD`/`EMAIL_FROM`); blank
  `host` or `from` means disabled (`isEnabled()` false), same
  configured-or-gracefully-degraded pattern as `OPENAI_API_KEY` or Ollama —
  never a crash, never a fake "sent". `sent_reports` gains `email_sent`
  (boolean); `SentReportResponse.simulated` is now `email != null &&
  !emailSent` — true delivery status, not just "an address was given". The
  email subject is `{book title} — {metric} — {date} — {reader's name}`, where
  `metric` is the one number specific to the report type (quiz: `Nota: N%`;
  progress: `Progreso: N%`; summary: the fixed label `Resumen` — a summary has
  no numeric grade). A send failure (bad address, provider outage) is caught,
  logged, and swallowed exactly like a disabled setup — the PDF is already
  generated and downloadable either way.
- **Consequence**: reports can be genuinely emailed once SMTP is configured
  (any standard provider — Brevo's free tier is what the owner set up first);
  nothing changes for a deployment that never configures it. The frontend's
  "(simulated)" copy in `SendReportForm`, `QuizPanel`, and `SummaryModal` now
  reflects the real per-report `simulated` flag instead of a hardcoded string.

## ADR-028: a document-capable provider gets a physical slice of the range, not the whole book by reference

- **Context**: ADR-025's mechanism — upload the whole PDF once per provider,
  cache `documents.claude_file_id`/`openai_file_id`, reference the id on every
  later turn — does avoid resending the file's *bytes*, but referencing an id
  does not avoid the provider *re-reading* the whole book on every single
  call: each API call is independent, so the model reprocesses every page of
  the referenced file every time it's mentioned, regardless of how small the
  reader's actual question is. In practice this meant every chat message —
  including a plain "hi" — paid the cost and latency of the entire book,
  reported directly as unacceptably slow and expensive ("cargar todo el
  puto libro por cada chat"). A middle option (skip re-attaching once the
  model had "already seen" a range earlier in the conversation) was designed
  but rejected before being built: BooKI's own history replay
  (`ConversationEngine.recentHistory`) flattens every past turn to plain text
  in `messages`, never the original request's content blocks — so a later
  call genuinely has zero access to the document unless it's attached again;
  "rely on conversation memory" doesn't hold across separate, stateless API
  calls the way it would within one continuous chat UI session.
- **Decision**: drop the upload-and-reference mechanism entirely —
  `documents.claude_file_id`/`openai_file_id`, `AiProvider#uploadDocument`,
  and `ActivityContentService`'s id-caching are all removed. Instead,
  `ActivityContentService#resolve` physically cuts the requested
  `startPage..endPage` out of the stored PDF into a new, small PDF
  (`PDDocument#importPage` page by page) on *every* call, for a document-
  capable provider — never the whole book — and sends those bytes inline as
  base64: Claude via its documented `{"type": "document", "source": {"type":
  "base64", ...}}` block (`docs/build-with-claude/pdf-support`), OpenAI via
  the Responses API's inline `{"type": "input_file", "file_data": "data:
  application/pdf;base64,..."}` (no Files API upload for either anymore).
  `AiProvider#converseWithDocument` takes `byte[] pdfBytes` instead of a file
  id. No caching of any kind: slicing is a fast, local, mechanical operation
  (unlike an AI call), so re-cutting on every turn is cheap enough that
  there's nothing worth caching, and it trivially guarantees freshness.
- **Consequence**: cost and latency now scale with the size of the range the
  reader actually selected (`ActivityRangeBar`), not the size of the book — a
  question about 5 pages is cheap and fast whether it's the first message or
  the fiftieth; a deliberately wide range still costs more, but that's a
  choice visible in the UI, not a hidden tax on every turn. Real pages
  (tables, maps, images) are still what the model sees, so ADR-025's original
  motivation is intact. The trade-off explicitly accepted: a follow-up
  question about a range already shown earlier in the conversation re-sends
  and re-pays for those same pages rather than reusing anything — judged
  acceptable because a request scoped to the reader's chosen range is already
  small, and building real cross-turn memory would mean replaying each past
  turn's original content blocks (not just its flattened text), a larger
  change than the cost of an occasional repeated slice justifies today.

## ADR-029: difficulty, function prompts, routing, and capabilities move off AiProfile to an account-wide App Profile

- **Context**: AiProfile (ADR-015/017's "tutor profile") carried four kinds
  of content: a persona, three difficulty rubrics, five function prompts
  (`fn_quiz_question`, `fn_answer_grading`, `fn_summary`, `fn_explain`,
  `fn_mnemonic`), a capability-routing instruction, and `enabledCapabilities`.
  Every one of the five shipped templates (`catalog.yml`) used the *identical*
  difficulty rubrics, function prompts, and capabilities — only `persona`
  ever varied by template. In practice, duplicating or editing a tutor
  profile to change its difficulty/function behavior had no discoverable
  effect from a different tutor's perspective, and editing one tutor's copy
  silently left every other tutor unaffected — the opposite of what users
  expected ("why doesn't switching tutors change anything?"). Difficulty
  rubrics were also read by every activity (chat, quiz, summary, voice),
  diluting the tutor's persona with competing instructions on every turn even
  though only quiz generation/grading ever needed them.
- **Decision**: `AiProfile` is reduced to exactly a name and a persona
  (`personaText`/`personaOriginalText`); the `ai_profile_slot_prompts` table
  and the generic `SlotPrompt`/`SlotKey` machinery are deleted outright. A new
  account-wide entity, `AppProfileSlot` (table `app_profile_slots`, one row
  per `AppProfileSlotKey`: `EASY`/`MEDIUM`/`HARD` plus the five `FN_*` keys
  plus `CAPABILITY_ROUTING`), holds everything that never varied by tutor —
  seeded from `catalog.yml`'s `difficulty`/`shared` blocks at registration,
  editable from a new "App Profile" screen/tab, with its own
  `GET/PUT /api/app-profile`, `POST /api/app-profile/{key}/revert`,
  `POST /api/app-profile/restore`. `PromptAssembler` now reads the difficulty
  rubric only for `FN_QUIZ_QUESTION`/`FN_ANSWER_GRADING` (never chat, voice,
  or summary), and reads function prompts/routing from the account via
  `session.getUser()`, not `session.getAiProfile()`. `enabledCapabilities`
  moves the same way, from `AiProfile` to `User` (`users.enabled_capabilities`,
  same `CapabilitySetConverter`), exposed via the existing
  `GET/PATCH /api/users/me` instead of the AI Profile endpoints.
  `catalog.yml` templates now define only `persona` (`capabilities:` and the
  template-level `overrides:` mechanism are gone — no template used them for
  anything but the difficulty rubrics this ADR already moved); catalog
  version bumped to `1.5.0`.
- **Consequence**: a tutor profile is now a one-field edit (persona) with no
  left-hand slot navigation; "App Profile" is one screen, the same regardless
  of which tutor is active, with three groups (Difficulty levels / Function
  prompts / Capability routing). `UpdateAiProfileRequest` drops `slots` and
  `enabledCapabilities`, gaining a plain `persona` string;
  `AiProfileResponse`/`AiProfileSummaryResponse` drop `slots`/
  `enabledCapabilities`, gaining `persona`/`personaOriginal`/
  `personaModified`. The one-time cost: `language_learning_guide`'s template
  previously overrode its own difficulty-rubric wording — that per-template
  customization is gone, difficulty now means the same thing for every tutor,
  which was the explicit goal. Quiz/`SubmitQuizAnswerRequest`'s cosmetic
  `aiProfileId` override (never actually changed which profile's content
  reached the model — ADR-015's rubric lookup always used the session's own
  profile) is removed for the same reason.

## ADR-030: editorial page numbers, detected once and cached, plus an accurate quiz report

- **Context**: every page number in BooKI was the raw physical PDF index.
  Many books don't start their printed/editorial numbering at physical page
  1 — a cover, title page, or table of contents pushes it later — so a
  reader asking for "page 10" meant the printed page 10, while BooKI always
  resolved that as the 10th PDF page. This had been discussed before but
  never written down anywhere durable, so the decision kept getting lost
  between conversations. Separately, the quiz report had a real bug, not
  just a formatting gap: `QuizAttempt` rows are written at *answer* time
  (`QuizServiceImpl.submitAnswer`), with no link back to what the quiz
  *generation* call actually requested — so a report built only from
  answered attempts could show the wrong page range (just the pages that
  happened to get answered) and had no way to say how many questions were
  requested versus answered. Reports also had no centralized recipient:
  `SendReportForm` was retyped from scratch every send, and `ReportEmailSender`
  swallowed delivery failures down to `e.toString()` with no stack trace —
  "simulated" looked identical whether SMTP was unconfigured or a real send
  had failed.
- **Decision**: `Document` gains `paginationOffset` (nullable Integer — the
  physical page on which printed page "1" appears; `editorialPage =
  physicalPage - paginationOffset`, deliberately plain arithmetic, no roman
  numeral modeling) and `paginationStatus`
  (`UNKNOWN`/`MANUAL`/`AUTO_DETECTED`/`DETECTION_FAILED`). Every page-range
  endpoint (session creation, chat/explain/quiz/summary turns, report
  generation) gains a `pageMode` (`PHYSICAL`/`EDITORIAL`, default physical)
  alongside its existing start/end pages, resolved through one new
  component, `PageRangeResolver` — replacing three previously-duplicated
  inline clamps in `ConversationEngine`, `QuizServiceImpl`, and
  `ReportServiceImpl`. Editorial→physical conversion only happens there.
  Detection is deliberately **at most once per document, then cached**:
  `PaginationDetectionService` reuses `ActivityContentService.resolve`/
  `converse` exactly as any other activity does (ADR-025 — a document-capable
  provider gets the real first pages, anything else gets extracted text; no
  separate extraction path), asking the model for `OFFSET: <physical page of
  printed page 1>` or `OFFSET: NONE`. It runs from two places: an explicit
  `POST /api/documents/{id}/pagination/detect` button (always runs, can
  overwrite a prior value — the frontend confirms with the user first when
  that value was `MANUAL`), and a lazy fallback inside `PageRangeResolver`
  that fires only when `paginationStatus == UNKNOWN`. Once a document reaches
  `MANUAL`, `AUTO_DETECTED`, or `DETECTION_FAILED`, the lazy path never runs
  again for it — a manual value can therefore never be silently overwritten,
  by construction, not by convention. A manual override is a separate
  `PATCH /api/documents/{id}/pagination`. Session creation's `startPage`/
  `endPage` become optional (default to page 1, resolved the same way as
  everything else) rather than a required, blocking range — the reading
  range was never meant to be fixed at session setup (ADR-021/024/026 already
  established the per-task "activity range" on the frontend; this just stops
  `CreateSessionModal` from pretending otherwise and adds the physical/
  editorial choice to it). For the quiz report: a new `QuizRequest` row is
  written at generation time (range, difficulty, requested question count,
  page mode), `QuizAttempt` gains a nullable `quizRequestId`, and
  `ReportServiceImpl.sendQuizReport` prefers the named (or session's most
  recent) `QuizRequest` as "requested", falling back to the old
  answered-attempts reconstruction only for rounds with no `QuizRequest` on
  record — labeled explicitly as such rather than implying it's the
  requested range. For email: `User.defaultReportEmail` plus a global
  `booki.email.default-report-recipient` fallback (same `application.yml`
  pattern as `booki.email.from` — no new admin-only DB table with nothing to
  edit it from), resolved by `ReportRecipientResolver` into
  `UserResponse.effectiveReportEmail`, which the frontend prefills (still
  editable per send). `ReportEmailSender.send` returns a typed
  `Result(sent, errorMessage)` instead of a bare `boolean`, logs the full
  exception (not just `toString()`), and `SentReportResponse.emailError`
  surfaces the reason transiently on a just-sent report.
- **Consequence**: this is the durable record of the "detect once, cache
  forever, manual always wins" rule — read this ADR, not a chat transcript,
  before touching pagination again. `SessionRequest.startPage` drops its
  `@NotNull`; a session's own start/end pages are now clamped, never
  rejected, matching how every other page range in BooKI already degraded
  (ADR-026). `GenerateQuizRequest`/`GenerateSummaryRequest`/`MessageRequest`/
  `ConversationRequest` all gain `pageMode`. Reports built from a session with
  no `QuizRequest` history (pre-this-change data) keep working, just labeled
  "requested range not recorded" instead of silently guessing. `V1__init.sql`
  is edited in place per its own documented convention (throwaway dev/pilot
  schema, no `V2`), adding `documents.pagination_offset`/`pagination_status`,
  `users.default_report_email`, the `quiz_requests` table, and
  `quiz_attempts.quiz_request_id`.
- **Follow-up**: `defaultReportEmail`/`effectiveReportEmail`, `SendReportRequest.email`
  and `GenerateSummaryRequest.email` all accept one address or several
  separated by `;` (a report may need to reach a parent and a tutor) —
  `com.booki.email.EmailList` is the one place that validates and parses the
  list; `ReportEmailSender` sends to every address via `MimeMessageHelper.setTo(String[])`.
  `CreateSessionModal` no longer asks for a starting page at all (not even as
  an optional field) — a session always opens at page 1; the backend's
  `SessionRequest.startPage` stays optional for API completeness, just unused
  by this UI now.

## ADR-031: prompt catalog v2 — answer first, labeled outside knowledge, five distinct personas, upgrades that follow unedited copies

- **Context**: in real use, replies hedged and handed questions back instead of
  answering ("guide their thinking instead of replacing it" in the core), and
  outside knowledge was blended into page-based answers without any marker.
  A side-by-side test showed `gpt-4o-mini` ignored the source-labeling rule
  while Claude followed it, so Claude became the default chat provider
  (`AI_PROVIDER`). The five shipped personas overlapped (three were variants of
  a patient tutor), and the difficulty rubrics were descriptive rather than
  operational. The easy rubric suggested choice/true-false questions, which
  `fn_quiz_question` forbids. Each account holds its own copies of personas and
  App Profile slots, and the backfills only added missing keys, so a catalog
  change never reached existing accounts.
- **Decision**: catalog `2.0.0`. The core answers first and concretely. Outside
  knowledge is encouraged when it is high quality, and always goes in its own
  paragraph opened by a "beyond your pages" label in the session language.
  When a function defines an exact output format (quiz question, SCORE/FEEDBACK,
  routing JSON), that format wins over the conversational reply shape. Five
  personas with distinct voices: `booki` (default: warm companion and
  narrator), `teacher`, `critic`, `step_by_step` (technical, structured), and
  `specialist` (orthopedagogy, speech-language practice, teaching techniques;
  never diagnoses). Each difficulty level states what is asked, the expected
  answer, the support given, and how strictly to grade. A template's
  `replaces:` list names the retired keys it supersedes.
- **Consequence**: on startup `AiProfileBackfill` does four things. It moves
  unedited copies of live templates to the current persona text. It converts
  unedited copies of retired templates in place, keeping the same row so
  sessions keep their link; a second copy mapping to an already-present
  replacement is deleted when no session uses it. It seeds missing templates.
  If the converted profile was the account default, the default moves to
  `booki`, once, so a default chosen later is never overridden.
  `AppProfileSlotBackfill` updates unedited slots the same way. Edited copies
  are never touched. "Restore" for slots and personas now returns the current
  catalog text, not the text the row was first seeded with. No schema change
  is needed: unedited means `text == originalText`.

## ADR-032: schema changes are incremental migrations; `V1__init.sql` is frozen

- **Context**: until now `V1__init.sql` was rewritten in place and the target
  database was wiped before each deploy (ADR-011/015/017/030). Once a real user
  had data on Neon, an in-place edit to V1 (ADR-030) made the Cloud Run revision
  fail to start: Flyway refused to boot on a checksum mismatch, and Cloud Run
  only reported that the container did not listen on port 8080.
- **Decision**: `V1__init.sql` is frozen. Every schema change goes in a new
  `V2__…`, `V3__…` migration. Wiping a database is an explicit, exceptional
  choice, never part of a routine deploy.
- **Consequence**: V1 is never edited, not even its header comment. That
  comment still describes the old convention, but Flyway's checksum covers the
  whole file, so correcting it would cause the very failure this ADR prevents.
  `docs/deployment.md` documents the rule and keeps the wipe procedure only as
  a historical fallback.
