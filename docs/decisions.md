# Architecture decisions (ADRs)

## ADR-001: React + PWA instead of Flutter

- **Context**: cross-platform support was required (Linux, Raspberry Pi, Android, Mac tablet).
- **Decision**: React + Vite + PWA.
- **Reasons**: the developer already knows React/Angular, deployment is instant in any browser, no app store submissions are needed, and the Web Speech API simplifies voice.
- **Consequence**: the experience isn't 100% native, but it's stable and accessible enough to validate the MVP.

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
- **Consequence (learned when first actually run)**: on a slow disk, MySQL's first-time volume initialization can take minutes instead of seconds and, if interrupted, leaves a half-initialized DB (missing app user, empty root password) — the container reports `healthy` even in that broken state, since the healthcheck only confirms the server accepts connections, not that init finished. Fix is `docker compose down -v` (wipes the volume) and a clean `up -d`, waited out fully this time. See `docs/local-dev.md` for the step-by-step.

## ADR-004: Local storage for PDFs

- **Context**: PDF file uploads in the MVP.
- **Decision**: store the file on disk (`./uploads`) and extract text into the database per page.
- **Reasons**: simple, avoids MinIO/S3 in the MVP, allows serving the PDF directly.
- **Consequence**: doesn't scale to multiple replicas; will migrate to object storage later.

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
  - `Session` gained a nullable `aiProvider` column, set once at `POST /sessions` and used for that session's entire lifetime (chat, quiz generation, quiz grading, summary). Omitting it falls back to `booki.ai.default-provider`, which is set per Spring profile: `ollama` for `local`, `claude` for `dev` (env var `AI_PROVIDER` still overrides either).
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

## ADR-010: streaming-ready interfaces, but no streaming transport yet

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
