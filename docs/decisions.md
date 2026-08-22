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
