# Compact memory for agents

## What we're building

BooKI: a cloud conversational reading assistant. PDF reading in page-range
sessions; one `ConversationEngine` behind text, quick actions and voice;
quiz/summary/explain/mnemonic as conversational capabilities. One responsive
PWA for Android/Windows/Linux.

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
- Prompt content: canonical versioned catalog at `backend/src/main/resources/prompts/catalog.yml`; Java keeps typed slot/output contracts. The Node mock is only a UI fixture and does not mirror production prompt quality.

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
