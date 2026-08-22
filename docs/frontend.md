# BooKI Frontend

## Technologies

- React 18 + TypeScript
- Vite (dev server + build)
- Tailwind CSS
- react-pdf for PDF rendering
- Web Speech API for voice (STT/TTS)
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
- `ChatPanel`: message history and text/voice input for talking with BooKI.
- `VoiceButton`: triggers `SpeechRecognition` and hands off the transcript.
- `QuizPanel` / `QuizReportPanel`: quiz question flow and quiz attempt history.
- `ProgressPanel`: reading progress for the current session.
- `NotificationsBell`: contextual nudges (halfway, done, try a quiz, etc.).
- `SessionSidebar`, `CreateSessionModal`: session creation and in-session navigation.
- `TagsBar`, `TagPickerModal`: filtering and assigning tags (see the backend's `Tag` entity, exposed via `/api/collections`).
- `SendReportForm`, `SummaryModal`: generating/emailing progress or quiz reports and reading summaries.
- `DocumentCard`, `HeroSection`, `HorizontalRow`: home screen library layout.
- `ui/`: shared low-level building blocks (buttons, form fields, etc.).

## Data layer

- `src/api/*.ts`: one file per backend resource (`auth`, `documents`, `profileMasters`, `reports`, `sessions`, `tags`, `users`), all going through the shared Axios instance in `api/client.ts` (adds the JWT header, base URL `/api`, redirects to `/login` on a 401).
- `src/hooks/*.ts`: data-fetching hooks built on top of `src/api` (`useDocuments`, `useSession`, `useChat`, `useQuiz`, `useQuizReport`, `useProgress`, `useNotifications`, `useSessionReports`, `useSummary`, `useTags`, `useProfileMasters`, `useUserProfile`, plus UI-only hooks like `useVoice` and `useScrollToHash`).
- `src/config/endpoints.ts`: the single source of truth for backend route paths used by the frontend.

**Known gap:** `useQuiz.ts` (`generate`, `submitAnswer`, `loadReport`) has no `catch` — a failed request there fails silently instead of surfacing an error, unlike the pages/components that already went through the error-handling pass below. Worth fixing when Quiz gets its own review pass.

- `src/lib/errors.ts`: `getErrorMessage(err, fallback?)` — the one place that knows how to pull `{error: string}` out of a failed Axios call (see `docs/backend.md`'s note on the backend's unified error shape). Every `catch` block that shows an error to the user should go through this helper rather than re-deriving `err.response?.data?.error` inline. A page-level `error` state + a red `<p>` under the relevant button/field is the established pattern (see `LoginPage`, `HomePage`, `ProfilePage`, `MastersPage`, `CreateSessionModal`) — don't let a form `onSubmit` swallow a rejected promise silently.

## Voice flow

1. The reader taps the microphone button (`VoiceButton`).
2. `useVoice` uses the browser's `SpeechRecognition` API.
3. The transcript is sent as a `VOICE`-type message.
4. The backend responds with text.
5. `speechSynthesis` for reading the response aloud is not implemented yet.

**Known gap:** `useVoice` currently hardcodes `recognition.lang = 'es-ES'`, regardless of the session's chosen language (English/Spanish/French) — voice input effectively only works reliably for Spanish today, even in English or French sessions.

## Dev proxy

Vite forwards `/api` to `http://localhost:8080` (see `vite.config.ts`) and only accepts the app at `http://localhost:5173` — the backend's CORS config rejects any other origin, including `127.0.0.1:5173`.
