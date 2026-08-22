# BooKI Architecture

## Repository

Multi-project monorepo:

```
booki/
├── backend/          # Spring Boot 4 + Java 21 + Gradle
├── frontend/         # React + TypeScript + Vite + PWA
├── docs/             # Technical and product memory
├── docker-compose.yml
├── .env.example
└── README.md
```

## Stack

| Layer | Technology |
|------|------------|
| Backend | Spring Boot 4.1, Spring Security, JWT Bearer, JPA, Flyway, WebClient |
| Database | MySQL (dev/prod), H2 (tests) |
| PDFs | Apache PDFBox for text extraction; file stored on disk |
| Frontend | React 18, TypeScript, Tailwind CSS, Vite, PWA, react-pdf |
| Voice | Browser Web Speech API (STT/TTS) |
| AI | `AiProvider` interface; only `OpenAiProvider` is implemented today — Kimi has config plumbing (`AI_PROVIDER=kimi`) but no provider class yet |

## Backend layers

- `controller` → REST controllers
- `service` → service interfaces
- `service/impl` → implementations
- `domain` → JPA entities
- `repository` → Spring Data JPA
- `security` → JWT util and filter
- `ai` → AI providers
- `dto` → request/response
- `config` → Spring Security config and the global exception handler
- `util` → cross-cutting helpers (e.g. `SecurityUtil` for reading the current user id from the JWT)

## Frontend layers

- `src/pages` → screens (Login, Home, Session, Masters, Profile)
- `src/components` → reusable components (Layout, PdfViewer, ChatPanel, VoiceButton, QuizPanel, and others — see `docs/frontend.md`)
- `src/api` → backend calls, one file per resource
- `src/hooks` → custom hooks built on `src/api` (useVoice, useSession, useChat, useQuiz, …)
- `src/types` → TypeScript types
- `src/context` → React context (AuthContext)
