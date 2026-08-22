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

## ADR-006: Profile Masters are per-user, not global

- **Context**: Profile Masters originally had no owner — one shared list of 4 built-in personas, visible and editable by every account (`isActive` was the only real state). Once editing was added, that meant one user's edit would silently change what every other user sees.
- **Decision**: add `user_id` to `profile_masters`. The original 4 seed rows stay as templates (`user_id IS NULL`, never returned directly by the API); at registration, each new user gets their own copy of all 4. Every read/write (`list`, `create`, `update`, `delete`) is scoped by owner (`findByIdAndUserId`), the same pattern already used for `Document`/`Tag`/`Session`.
- **Reasons**: lets a reader freely rename/tweak "their" Grade-3-teacher persona without touching anyone else's; keeps the "pick a Master" UX at session/quiz creation unchanged (still just a list to choose from) — it's simply each user's own list now instead of one global list.
- **Consequence**: deleting a Master a user already used in a `Session`/`QuizAttempt` had to stop being a raw DB delete — those rows now get their `profileMasterId` cleared (history kept, persona tag dropped) before the delete, instead of hitting a foreign-key constraint violation.
