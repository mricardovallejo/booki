# BooKI Architecture

## Repository

Multi-project monorepo:

```
booki/
├── backend/          # Spring Boot 3.3 + Java 21 + Gradle
├── frontend/         # React + TypeScript + Vite + PWA
├── docs/             # Technical and product memory
├── docker-compose.yml
├── .env.example
└── README.md
```

## Stack

| Layer | Technology |
|------|------------|
| Backend | Spring Boot 3.3, Spring Security, JWT Bearer, JPA, Flyway, WebClient |
| Database | MySQL (dev/prod), H2 (tests) |
| PDFs | Apache PDFBox for text extraction; file stored on disk |
| Frontend | React 18, TypeScript, Tailwind CSS, Vite, PWA, react-pdf |
| Voice | Browser Web Speech API (STT/TTS) |
| AI | `AiProvider` interface; initial provider OpenAI, ready for Kimi |

## Backend layers

- `controller` → REST controllers
- `service` → service interfaces
- `service/impl` → implementations
- `domain` → JPA entities
- `repository` → Spring Data JPA
- `security` → JWT util and filter
- `ai` → AI providers
- `dto` → request/response
- `config` → Spring configuration

## Frontend layers

- `src/pages` → screens (Home, Session)
- `src/components` → reusable components (Layout, PdfViewer, ChatDrawer, VoiceButton)
- `src/api` → backend calls
- `src/hooks` → custom hooks (useVoice)
- `src/types` → TypeScript types
