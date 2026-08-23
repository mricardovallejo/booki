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
- `QuizPanel`: quiz setup, the question flow, and the full correction report (stats + per-attempt history + email-a-copy) all in one place — these used to be two separate tabs (`QuizPanel` + `QuizReportPanel`) but that was redundant, so the report now lives inside the Quiz tab and `QuizReportPanel` is gone. Also supports an opt-in checkbox that auto-emails the report the moment the last question in a round gets graded.
- `ProgressPanel`: reading progress for the current session.
- `NotificationsBell`: contextual nudges (halfway, done, try a quiz, etc.).
- `SessionSidebar`, `CreateSessionModal`: session creation and in-session navigation.
- `TagsBar`, `TagPickerModal`: filtering and assigning tags (see the backend's `Tag` entity, exposed via `/api/collections`).
- `SendReportForm`, `SummaryModal`: generating/emailing progress or quiz reports and reading summaries.
- `DocumentCard`, `HeroSection`, `HorizontalRow`: home screen library layout.
- `ui/`: shared low-level building blocks (buttons, form fields, etc.).

## Data layer

- `src/api/*.ts`: one file per backend resource (`auth`, `documents`, `profileMasters`, `reports`, `sessions`, `tags`, `users`), all going through the shared Axios instance in `api/client.ts` (adds the JWT header, base URL `/api`, redirects to `/login` on a 401).
- `src/hooks/*.ts`: data-fetching hooks built on top of `src/api` (`useDocuments`, `useSession`, `useChat`, `useQuiz`, `useProgress`, `useNotifications`, `useSessionReports`, `useSummary`, `useTags`, `useProfileMasters`, `useUserProfile`, plus UI-only hooks like `useVoice` and `useScrollToHash`). `useQuiz` owns the quiz report too (`report`/`loadReport`, refetched after every graded answer) — there's no separate `useQuizReport` anymore.
- `src/config/endpoints.ts`: the single source of truth for backend route paths used by the frontend.
- `src/lib/errors.ts`: `getErrorMessage(err, fallback?)` — the one place that knows how to pull `{error: string}` out of a failed Axios call (see `docs/backend.md`'s note on the backend's unified error shape). Every data-fetching hook and every `onSubmit`/action handler goes through this helper and exposes an `error` string, instead of swallowing a rejected promise silently or leaving a panel stuck on its loading spinner forever. This is applied consistently across the whole app now: every `use*` hook in `src/hooks/` that calls the API returns `error` alongside its data (`useDocuments`, `useTags`, `useProfileMasters`, `useSession`, `useChat`, `useQuiz`, `useProgress`, `useNotifications`, `useSessionReports`, `useSummary`, `useUserProfile`), and the page/component consuming it renders a red `<p>` near the relevant button/field (see `LoginPage`, `HomePage`, `ProfilePage`, `MastersPage`, `CreateSessionModal`, `TagsBar`, `TagPickerModal`, `ChatPanel`, `QuizPanel`, `PdfViewer`, `ProgressPanel`, `NotificationsBell` for examples of each shape).

## Voice flow

1. The reader taps the microphone button (`VoiceButton`).
2. `useVoice` uses the browser's `SpeechRecognition` API.
3. The transcript is sent as a `VOICE`-type message.
4. The backend responds with text.
5. `speechSynthesis` for reading the response aloud is not implemented yet.

**Known gap:** `useVoice` currently hardcodes `recognition.lang = 'es-ES'`, regardless of the session's chosen language (English/Spanish/French) — voice input effectively only works reliably for Spanish today, even in English or French sessions.

## Dev proxy

Vite forwards `/api` to `http://localhost:8080` (see `vite.config.ts`) and only accepts the app at `http://localhost:5173` — the backend's CORS config rejects any other origin, including `127.0.0.1:5173`.
