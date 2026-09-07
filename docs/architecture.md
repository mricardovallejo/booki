# BooKI Architecture

## Repository

Multi-project monorepo:

```
booki/
├── backend/          # Spring Boot 4 + Java 21 + Gradle
├── frontend/         # React + TypeScript + Vite + PWA
├── docs/             # Technical and product memory + the visual guide (booki-guide.html/pdf)
├── scripts/          # build-guide.mjs (renders + syncs the guide PDF), deploy secrets
├── docker-compose.yml
├── .env.example
└── README.md
```

## Stack

| Layer | Technology |
|------|------------|
| Backend | Spring Boot 4.1, Spring Security, JWT Bearer, JPA, Flyway, WebClient (with shared connect/read timeouts on every outbound provider call) |
| Database | PostgreSQL (dev / deployed), H2 in PostgreSQL mode (tests / no-Docker local) |
| PDFs | Apache PDFBox for per-page text extraction; files stored via the `StorageAdapter` seam (local disk today) |
| Frontend | React 18, TypeScript, Tailwind CSS, Vite, PWA, react-pdf |
| AI | `AiProvider` interface with 4 always-registered providers (`claude`, `openai`, `kimi`, `ollama`), chosen per session |
| Voice | Server-side `SpeechToTextProvider` / `TextToSpeechProvider` (OpenAI-compatible impl); browser `SpeechRecognition` only as a fallback |

## Core interaction architecture

The reading **Session** is the context. Every way of talking to BooKI —
typing, a quick-action button, or speaking — converges on one
**`ConversationEngine`**, which composes that session context with conversational
**capabilities** and an **AI provider**.

Sessions are open-ended: `startPage` records where the journey began,
`endPage` is a pure reading-progress marker (grows to the furthest page reached),
and `currentPage` may move anywhere in the PDF. Plain chat sends a bounded window
around the reading position (or an explicit range typed into the message). The AI
*activities* — panel quiz, summary modal, chat quick-actions — instead run on a
reader-controlled **activity page range** (client-side, shared, defaults to
"pages read so far", editable); see ADR-024.

```
  TEXT ─────────────┐
  QUICK ACTION ─────┼──▶  ConversationEngine
  VOICE ─▶ STT ─────┘         │
                              ├─ SessionContext  (document, reading progress, current page,
                              │                   language, difficulty, AI Profile
                              │                   layers, recent history — docs/prompts.md)
                              ├─ Capabilities    (quiz · summary · explain · mnemonic)
                              ├─ AiProvider      (claude / openai / kimi / ollama)
                              │
                              └─▶ reply ──▶ persisted Message ──▶ (optional TTS)
```

- **Transport-neutral.** `ConversationEngine` takes a `ConversationRequest` and
  returns a `ConversationResult`; it has no idea whether the caller was REST, a
  future SSE stream, or a WebSocket. `SessionController` and `VoiceController`
  are thin adapters.
- **One `Message` model** for text and voice (`InputType.TEXT` / `VOICE`), one
  history window, one persistence path.
- **Capabilities are conversational, not separate systems.** The model opts into
  one via a provider-neutral routing directive, or a quick-action button names
  one explicitly (`capabilityHint`). They reuse `QuizService` / `ReportService`.
- **Voice is cloud and provider-agnostic.** The browser only captures and plays
  audio; STT/TTS run on the backend with server-side credentials. Session
  language drives the locale.
- **Streaming-ready, not streaming.** The provider and engine interfaces have
  opt-in streaming companions (`StreamingAiProvider`,
  `ConversationEngine.converseStreaming`) so an SSE path can be added later
  without touching what ships today.

See `docs/decisions.md` (ADR-008 capabilities, ADR-009 voice, ADR-010 streaming)
and `docs/ai-voice.md` for the detail.

## Backend layers

- `controller` → REST controllers (incl. `VoiceController`)
- `service` → service interfaces
- `service/impl` → implementations; the prompt assembly lives in `prompt/PromptAssembler` (`docs/prompts.md`). On sign-up `ReaderProfileProvisioner` seeds the reader profile inline, and `AuthServiceImpl` publishes `service/event/UserRegisteredEvent` which `WelcomeDocumentProvisioner` handles after commit to seed the bundled guide (ADR-020, ADR-022).
- `src/main/resources/prompts/catalog.yml` → versioned canonical core, defaults and tutor personas; loaded and validated by `SlotPromptCatalog`
- `conversation` → `ConversationEngine`, `ConversationRequest/Result/Stream`
- `conversation/capability` → `ConversationCapability` + registry + the 4 capabilities
- `voice` → `SpeechToTextProvider` / `TextToSpeechProvider`, their OpenAI impls, `VoiceConversationService`
- `domain` → JPA entities
- `repository` → Spring Data JPA
- `security` → JWT util + filter (the filter also checks the token's user still exists)
- `ai` → `AiProvider` + `AiProviderRegistry` + `StreamingAiProvider`
- `dto` → request/response (write DTOs carry Bean Validation constraints; see `docs/backend.md` "Request validation")
- `config` → Spring Security config, the global exception handler (sanitizes uncaught errors), `OutboundHttp` (provider timeouts), `AsyncConfig` (one small background pool — welcome-guide seeding), actuator/OpenAPI config
- `util` → cross-cutting helpers (e.g. `SecurityUtil` for the current user id)

## Frontend layers

- `src/pages` → screens (Login, Home eager; Session, AiProfiles, Profile `React.lazy` — Session pulls in react-pdf/pdf.js, kept out of the initial bundle)
- `src/components` → reusable components (Layout, PdfViewer, ChatPanel, VoiceButton, QuizPanel, …) — see `docs/frontend.md`
- `src/api` → backend calls, one file per resource (incl. `voice.ts`)
- `src/hooks` → custom hooks on `src/api` (`useChat` with `send` + `sendVoice`, `useSession`, `useQuiz`, `useVoiceRecorder` for cloud capture, `useVoice` for the browser fallback, …)
- `src/types` → TypeScript types
- `src/context` → React context (AuthContext)
