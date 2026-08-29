# Compact memory for agents

## What we're building

BooKI: a cloud conversational reading assistant. PDF reading in page-range
sessions; one `ConversationEngine` behind text, quick actions and voice;
quiz/summary/explain/mnemonic as conversational capabilities. One responsive
PWA for Android/Windows/Linux.

## Fixed stack

- Backend: Spring Boot 4, Java 21, Gradle, JPA, Flyway, MySQL (dev), H2 (tests/no-Docker), Spring Security + JWT, WebClient.
- Frontend: React + TypeScript + Vite + Tailwind + PWA + react-pdf.
- AI: `AiProvider` interface, 4 providers (`claude` default, `openai`, `kimi`, `ollama`), per-session choice.
- Voice: server-side `SpeechToTextProvider` / `TextToSpeechProvider` (OpenAI impl); browser `SpeechRecognition` only as a fallback.

## Conventions

- Code in English: `Document`, `Session`, `ProfileMaster`, `Message`.
- Base package: `com.booki`.
- Backend: Controller → Service (interface) → ServiceImpl → Repository.
- Frontend: `pages/` → `components/` → `api/` → `hooks/`.

## What NOT to do without asking

- Don't add microservices, Kafka, Redis, a vector DB, an agent framework, or a new client stack (Flutter/RN/Tauri).
- Don't change the database without agreement.
- Don't introduce heavy new dependencies.
- Don't introduce SSE/WebSocket/WebRTC without a concrete streaming requirement.
- Don't change the product principles (reading is never blocked, not an LMS, PDF stays the protagonist).

## Usual next steps

1. Start MySQL with Docker Compose.
2. Run the backend in dev.
3. Install frontend dependencies with npm and start the dev server.
4. Test the flow: register → upload PDF → create session → chat.
