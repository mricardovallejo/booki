# Compact memory for agents

## What we're building

BooKI: a PDF reader with page-range sessions and a contextual AI assistant over text/voice.

## Fixed stack

- Backend: Spring Boot 4, Java 21, Gradle, JPA, Flyway, MySQL (dev), H2 (tests), Spring Security + JWT.
- Frontend: React + TypeScript + Vite + Tailwind + PWA + react-pdf.
- Voice: browser Web Speech API.
- AI: `AiProvider` interface; OpenAI by default.

## Conventions

- Code in English: `Document`, `Session`, `ProfileMaster`, `Message`.
- Base package: `com.booki`.
- Backend: Controller → Service (interface) → ServiceImpl → Repository.
- Frontend: `pages/` → `components/` → `api/` → `hooks/`.

## What NOT to do without asking

- Don't add microservices.
- Don't change the database without agreement.
- Don't introduce heavy new dependencies.
- Don't change the product principles (reading is never blocked, not an LMS).

## Usual next steps

1. Start MySQL with Docker Compose.
2. Run the backend in dev.
3. Install frontend dependencies with npm and start the dev server.
4. Test the flow: register → upload PDF → create session → chat.
