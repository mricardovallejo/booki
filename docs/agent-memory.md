# Compact memory for agents

## What we're building

BooKI: a cloud conversational reading assistant. Open-ended PDF reading
sessions; one `ConversationEngine` behind text, quick actions and voice;
quiz/summary/explain/mnemonic as conversational capabilities. One responsive
PWA for Android/Windows/Linux.

Session invariant: `startPage` is the reading start, `endPage` is a
reading-progress marker (furthest page reached), and `currentPage` is freely
navigable. Normal chat context contains at most eight pages; a written range may
select up to 20. The AI *activities* (panel quiz, summary modal, chat
quick-actions) run on a client-side **activity page range** — shared per
session, not persisted, default "pages read so far", editable — passed on each
request and clamped to the document. Quiz question count is independent of the
range width. See ADR-024.

## Fixed stack

- Backend: Spring Boot 4, Java 21, Gradle, JPA, Flyway, PostgreSQL (dev/deployed), H2 in PostgreSQL mode (tests/no-Docker), Spring Security + JWT, WebClient (shared timeouts via `config/OutboundHttp`).
- Frontend: React + TypeScript + Vite + Tailwind + PWA + react-pdf.
- AI: `AiProvider` interface, 4 providers (`openai` default, `claude`, `kimi`, `ollama`), per-session choice. `openai` default because the same key also covers cloud voice.
- Voice: server-side `SpeechToTextProvider` / `TextToSpeechProvider` (OpenAI impl); browser `SpeechRecognition` only as a fallback.

## Conventions

- Code in English: `Document`, `Session`, `AiProfile` (UI: "tutor profile"; ADR-018), `ReaderProfile` (who reads), `Message`. `ProfileMaster` is gone (ADR-015 / ADR-017).
- Base package: `com.booki`.
- Backend: Controller → Service (interface) → ServiceImpl → Repository. Write DTOs carry `@Valid` constraints; multi-write services are `@Transactional`; uncaught errors are sanitized. Security posture: ADR-016.
- Frontend: `pages/` → `components/` → `api/` → `hooks/`. `npm run lint` + `tsc --noEmit` + `build` are the CI gate.
- Prompt content: canonical versioned catalog at `backend/src/main/resources/prompts/catalog.yml` (v1.3.0); Java keeps typed slot/output contracts. Quiz questions are open (no multiple choice); grading returns `SCORE` + a teaching `FEEDBACK` that states the answer, and `correct = score ≥ 0.6` (ADR-023). The Node mock is only a UI fixture and does not mirror production prompt quality.
- Visual guide: `docs/booki-guide.html` + `docs/images/` → `docs/booki-guide.pdf` via `scripts/build-guide.mjs`, which also syncs the copies in `frontend/public/` (landing "Learn more") and `backend/src/main/resources/welcome/` (seeded `@Async` into every new account's library on sign-up — ADR-022, `config/AsyncConfig`, toggle `WELCOME_DOCUMENT_ENABLED`). All three PDFs are committed; rerun the script after editing the HTML.

## What NOT to do without asking

- Don't add microservices, Kafka, Redis, a vector DB, an agent framework, or a new client stack (Flutter/RN/Tauri).
- Don't change the database without agreement.
- Don't introduce heavy new dependencies.
- Don't introduce SSE/WebSocket/WebRTC without a concrete streaming requirement.
- Don't change the product principles (reading is never blocked, not an LMS, PDF stays the protagonist).

## Usual next steps

1. Start PostgreSQL with Docker Compose.
2. Run the backend in dev.
3. Install frontend dependencies with npm and start the dev server.
4. Test the flow: register → upload PDF → create session → chat.
