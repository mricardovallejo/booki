# BooKI Frontend

## Technologies

- React 18 + TypeScript
- Vite (dev server + build)
- Tailwind CSS
- react-pdf for PDF rendering
- react-markdown + remark-gfm for rendering BooKI's chat replies (real AI output routinely comes back with `**bold**`, lists, headings, and tables — `ChatPanel` renders it through `ReactMarkdown` with a small Tailwind class set (`MARKDOWN_CLASSES`), not plain `<p>`; user-typed messages stay plain text). Base markdown (CommonMark, what `react-markdown` supports out of the box) has no table syntax at all — tables are a GitHub-specific extension (GFM), hence the separate `remark-gfm` plugin; without it a table comes back as literal `|` characters, not a parse error.
- `getUserMedia` + `MediaRecorder` for voice capture (STT/TTS run on the backend — see "Voice flow")
- PWA via vite-plugin-pwa

## Screens (`src/pages`)

- **LoginPage**: sign in / sign up (email, password, optional name); also offers a "use demo account" shortcut.
- **HomePage**: list of the user's PDFs, tag filtering, and the upload flow.
- **SessionPage**: PDF reader + chat, quiz, progress, and notifications for one session.
- **MastersPage**: browse/create/edit/delete the current user's own Profile Masters — each user has their own private set (see `docs/backend.md`).
- **ProfilePage**: edit the current user's name, bio, and personal system prompt.

All routes except `/login` are wrapped in `ProtectedRoute`, which redirects to `/login` when there's no authenticated user (see `context/AuthContext`).

## Main components (`src/components`)

- `Layout`: top bar and route container for authenticated pages.
- `PdfViewer`: renders the PDF and lets the reader move between pages.
- `ChatPanel`: the conversation with BooKI — message history, text input, voice, and the quick-action row (Ask me / Explain / Summarize / Memorize). It owns the voice state and picks the cloud path or the browser fallback.
- `VoiceButton`: presentational mic button (supported / recording / busy) — all voice logic lives in `ChatPanel`.
- `QuizPanel`: quiz setup, the question flow, and the full correction report (stats + per-attempt history + email-a-copy) all in one place — Also supports an opt-in checkbox that auto-emails the report the moment the last question in a round gets graded.
- `ProgressPanel`: reading progress for the current session.
- `NotificationsBell`: contextual nudges (halfway, done, try a quiz, etc.).
- `SessionSidebar`, `CreateSessionModal`: session creation and in-session navigation.
- `TagsBar`, `TagPickerModal`: filtering and assigning tags (see the backend's `Tag` entity, exposed via `/api/collections`).
- `SendReportForm`, `SummaryModal`: generating/emailing progress or quiz reports and reading summaries.
- `DocumentCard`, `HeroSection`, `HorizontalRow`: home screen library layout.
- `ui/`: shared low-level building blocks (buttons, form fields, etc.).

## Data layer

- `src/api/*.ts`: one file per backend resource (`auth`, `documents`, `profileMasters`, `reports`, `sessions`, `tags`, `users`, `voice`), all going through the shared Axios instance in `api/client.ts` (adds the JWT header, base URL `/api`, redirects to `/login` on a 401).
- `src/hooks/*.ts`: data-fetching hooks built on top of `src/api` (`useDocuments`, `useSession`, `useChat`, `useQuiz`, `useProgress`, `useNotifications`, `useSessionReports`, `useSummary`, `useTags`, `useProfileMasters`, `useUserProfile`, plus UI hooks `useVoiceRecorder` (cloud audio capture), `useVoice` (browser fallback), and `useScrollToHash`). `useChat` exposes both `send` (text / quick-action, with an optional `capabilityHint`) and `sendVoice` (uploads a clip, returns the persisted messages + optional spoken reply). `useQuiz` owns the quiz report too (`report`/`loadReport`, refetched after every graded answer).
- `src/config/endpoints.ts`: the single source of truth for backend route paths used by the frontend.
- `src/lib/errors.ts`: `getErrorMessage(err, fallback?)` — the one place that knows how to pull `{error: string}` out of a failed Axios call (see `docs/backend.md`'s note on the backend's unified error shape). Every data-fetching hook and every `onSubmit`/action handler goes through this helper and exposes an `error` string, instead of swallowing a rejected promise silently or leaving a panel stuck on its loading spinner forever. This is applied consistently across the whole app now: every `use*` hook in `src/hooks/` that calls the API returns `error` alongside its data (`useDocuments`, `useTags`, `useProfileMasters`, `useSession`, `useChat`, `useQuiz`, `useProgress`, `useNotifications`, `useSessionReports`, `useSummary`, `useUserProfile`), and the page/component consuming it renders a red `<p>` near the relevant button/field (see `LoginPage`, `HomePage`, `ProfilePage`, `MastersPage`, `CreateSessionModal`, `TagsBar`, `TagPickerModal`, `ChatPanel`, `QuizPanel`, `PdfViewer`, `ProgressPanel`, `NotificationsBell` for examples of each shape).

## Voice flow

STT and TTS run on the **backend** — the browser only captures and plays audio
(ADR-009). `ChatPanel` calls `GET /api/voice/capabilities` on load and picks:

**Cloud path** (default when the browser can record and the backend has an STT
provider):

1. The reader taps the mic (`VoiceButton`); `useVoiceRecorder` records a clip with `getUserMedia` + `MediaRecorder`.
2. Tapping again stops; `useChat.sendVoice` uploads the clip to `POST /api/sessions/{id}/voice`.
3. The backend transcribes it, runs the **same** `ConversationEngine` as a typed message (`InputType.VOICE`), and returns the persisted user + bot messages plus an optional base64 MP3 reply.
4. `ChatPanel` refreshes the transcript and plays the reply audio if present.

**Fallback path** (browser without `MediaRecorder`, or backend with no STT
provider): `useVoice` uses the browser `SpeechRecognition` API (Chromium only,
now following the session language) and posts the transcript through
`POST /messages` as a `VOICE` message. This is plan B, not the architecture.

Session language drives recognition on both paths — the old hardcoded `es-ES`
is gone.

Streaming voice (incremental STT / TTS) is not built — see `docs/ai-voice.md`
"Streaming".

## Dev proxy

Vite forwards `/api` to `http://localhost:8080` (see `vite.config.ts`) and only accepts the app at `http://localhost:5173` — the backend's CORS config rejects any other origin, including `127.0.0.1:5173`.
