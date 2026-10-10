# BooKI Frontend

## Technologies

- React 18 + TypeScript (`strict`, `noUnusedLocals`, `noUnusedParameters`)
- Vite (dev server + build). `App.tsx` `React.lazy`-loads the heavy screens (Session — react-pdf/pdf.js — plus AiProfiles and Profile) behind a `<Suspense>`, so the login/library entry chunk stays small.
- ESLint — `.eslintrc.cjs` (`@typescript-eslint` + `react-hooks` + `react-refresh`); `npm run lint` is a CI gate (see `.github/workflows/ci.yml`)
- Tailwind CSS — the palette lives once as RGB channels in `src/index.css` (`:root`), referenced from `tailwind.config.js` via `rgb(var(--color-*) / <alpha-value>)`
- react-pdf for PDF rendering
- react-markdown + remark-gfm for rendering BooKI's chat replies (real AI output routinely comes back with `**bold**`, lists, headings, and tables — `ChatPanel` renders it through `ReactMarkdown` with a small Tailwind class set (`MARKDOWN_CLASSES`), not plain `<p>`; user-typed messages stay plain text). Base markdown (CommonMark, what `react-markdown` supports out of the box) has no table syntax at all — tables are a GitHub-specific extension (GFM), hence the separate `remark-gfm` plugin; without it a table comes back as literal `|` characters, not a parse error.
- `getUserMedia` + `MediaRecorder` for voice capture (STT/TTS run on the backend — see "Voice flow")
- PWA via vite-plugin-pwa

## Screens (`src/pages`)

- **LoginPage**: sign in / sign up (email, password, optional name). The "use demo account" shortcut is gated behind `import.meta.env.DEV`, so it (and the demo credentials) are stripped from production builds.
- **HomePage**: list of the user's PDFs, tag filtering, and the upload flow. A new account's library already contains the BooKI guide (ADR-022).
- **SessionPage**: open-ended PDF reader + chat, quiz, progress, and notifications for one session. The starting page is not a navigation boundary. Wraps the sidebar and viewer in an `ActivityRangeProvider` (keyed by session id) that holds the shared **activity page range** — see `ActivityRangeContext` below and ADR-024.
- **AiProfilesPage** — "Reading setup" (`/ai-profiles`, `?tab=tutor|reader|app`): three tabs, in order **Tutor profile**, **Reader profile**, **App Profile**.
  - *Tutor profile* tab: a selector + `New` / `Duplicate` / `Delete`, a name field, and a single persona textarea with its own Edited/Original badge + `Restore to original` — no slot navigation, there is only one field. `New` = a fresh copy of the user's default (there is no blank template); `Duplicate` = a copy of the selected one, always named `"<source name> (copy)"` even when the source is a shipped read-only template. Selection is a query param.
  - *Reader profile* tab: `Name` / `Starting level for quizzes` / shared `context`. Every account starts with an editable, default `My reader profile` copied from the General reader scaffold, so the editor has a usable form on first visit. Shipped templates such as "General reader" and "Language-support reader" remain read-only; `New` or `Duplicate` makes another editable profile (same `"<source name> (copy)"` naming as Tutor).
  - *App Profile* tab: account-wide, the same regardless of which tutor is active (ADR-029) — a "Capabilities allowed" toggle row (saved immediately on click, via `useUserProfile`) plus the three App Profile groups (Difficulty levels / Function prompts / Capability routing), each with slot navigation (flat, no Advanced fold), a draft, `Save changes`, and `Restore to original` (per-slot and whole-profile). `?key=<slot>` deep-links into one slot.
  - Long help text is a small "?" disclosure (`Explainer`) per tab.
  - "AI Profile" is called **tutor profile** and its persona just **persona** throughout the UI; routes and API names are unchanged. See `docs/prompts.md`.
- **ProfilePage** — "Account details": edit the current user's name and `defaultReportEmail` (one address, or several separated by `;` — pre-fills the "email report" box everywhere else; learning preferences live in reader profiles — `docs/prompts.md`).

All routes except `/login` are wrapped in `ProtectedRoute`, which redirects to `/login` when there's no authenticated user (see `context/AuthContext`).

## Main components (`src/components`)

- `Layout`: top bar and route container for authenticated pages.
- `PdfViewer`: renders the PDF and lets the reader navigate the whole document. Its header shows the range reached so far; moving backward never reduces that range. Feeds the document length + reading progress into `ActivityRangeContext`. When `ActivityRangeContext`'s `pageMode` is `editorial`, the page-jump control and the "pages read" line switch to showing the book's own printed page number as primary, with the raw PDF page as a small secondary annotation — the same conversion `ActivityRangeBar` uses (`lib/pagination.ts`), so a given number never means two different physical pages depending on which control you're looking at (ADR-030).
- `ActivityRangeBar`: the one place the reader sets the **activity page range** (a bar above the sidebar tabs, since the range governs quiz + summary + chat quick-actions). Steppers hold a free-text draft and commit on blur/Enter; default follows reading progress, editing pins it, `reset` returns to auto (ADR-024). Once a `Document` has a known `paginationOffset`, a PDF/Printed toggle switches the steppers between physical and editorial page numbers (`lib/pagination.ts`, ADR-030); while the offset is still unknown, an inline "Use printed page numbers" link triggers `POST /documents/{id}/pagination/detect` instead.
- `ChatPanel`: the conversation with BooKI — message history, text input, voice, and the quick-action row. A quick action is hidden when its capability isn't in `session.enabledCapabilities` (`docs/prompts.md`). It owns the voice state and picks the cloud path or the browser fallback. Quick actions send the shared activity range; free-text messages do not (they stay on the reading position).
- `ContextInfoButton`: the ℹ popup — the assembled prompt layers (`docs/prompts.md`). Uses `useOutsideDismiss` (below).
- `DocumentCard`: a library card. The whole card is a full-bleed `<button>` for "open"; the corner tag/delete/settings actions are real sibling `<button>`s stacked above it (no nested interactive elements). On touch screens those corner actions stay visible with a 44px tap target (on desktop they fade in on hover). The settings (gear) action opens `DocumentSettingsModal`.
- `DocumentSettingsModal`: a book's pagination settings — "Detect automatically" (`POST /documents/{id}/pagination/detect`, confirms before overwriting a manually-set offset) or type by hand the PDF page that shows the book's printed page "1" (`PATCH /documents/{id}/pagination`; empty clears it). ADR-030.
- `VoiceButton`: presentational mic button (supported / recording / busy) — all voice logic lives in `ChatPanel`.
- `QuizPanel`: quiz setup, the question flow, and the full correction report (stats + per-attempt history + email-a-copy) all in one place. Each round runs on the shared activity range (set in `ActivityRangeBar`, not here); the number of questions (1–20) is independent of the range width. The result shows the effective range returned by the backend, plus the round's `quizRequestId` (threaded into each answer and into the report send, so the correction report shows what was actually requested — ADR-030). The email box pre-fills from `user.effectiveReportEmail` and accepts several addresses separated by `;`; a failed send shows the real reason instead of a generic "not configured" message. Also supports an opt-in checkbox that auto-emails the report the moment the last question in a round gets graded.
- `ProgressPanel`: reading progress from the session's starting page to the document's final page. Progress is based on the furthest page reached, so reviewing an earlier page does not move it backward.
- `NotificationsBell`: contextual nudges (halfway, done, try a quiz, etc.).
- `SessionSidebar`, `CreateSessionModal`: session creation and in-session navigation. Creation doesn't ask for a page at all anymore — a session just opens at page 1; the reading range was never meant to be locked in at setup, only the per-task activity range (`ActivityRangeBar`) matters (ADR-030).
- `TagsBar`, `TagPickerModal`: filtering and assigning tags (see the backend's `Tag` entity, exposed via `/api/collections`).
- `SendReportForm`, `SummaryModal`: generating/emailing progress or quiz reports and reading summaries. Summary generation runs on the shared activity range (set in `ActivityRangeBar`). Both email boxes pre-fill from `user.effectiveReportEmail`, accept several addresses separated by `;`, and surface the real failure reason on a send that was attempted but failed (ADR-030).
- `DocumentCard`, `HeroSection`, `HorizontalRow`: home screen library layout. `HeroSection`'s "Learn more" opens the visual guide at `/booki-guide.pdf` (served from `public/`; the same PDF every new account gets in its library — ADR-022).
- `ui/`: shared low-level building blocks (buttons, form fields, etc.).

## Data layer

- `src/api/*.ts`: one file per backend resource (`auth`, `documents`, `aiProfiles`, `readerProfiles`, `reports`, `sessions`, `tags`, `users`, `voice`), all going through the shared Axios instance in `api/client.ts`. The client reads the token from an **in-memory holder** (`src/lib/authToken.ts`) that `AuthContext` keeps in sync — not `localStorage` on every request — and redirects to `/login` on a 401. Base URL is `API_BASE` from `config/endpoints.ts` — `VITE_API_BASE_URL` if set (deployed build, separate origin), otherwise `/api` (local dev, via the Vite proxy). `API_BASE_IS_SECURE` is false only when a *production* build points at a plaintext `http://` backend; when it's false the bearer token is not attached (client.ts and `PdfViewer`).
- `src/hooks/*.ts`: data-fetching hooks built on top of `src/api` (`useDocuments`, `useDocument` (one document's metadata incl. `paginationOffset`/`paginationStatus`), `useSession`, `useSessionContext` → `{context, loading, error}`, `useChat`, `useQuiz`, `useProgress`, `useNotifications`, `useSessionReports`, `useSummary`, `useTags`, `useAiProfiles`, `useAiProfile` (persona draft + save/revertPersona/restore), `useAppProfile` (all 9 account-wide slots), `useReaderProfiles`, `useUserProfile` (account details incl. `enabledCapabilities`, `defaultReportEmail`), plus UI hooks `useVoiceRecorder` (cloud audio capture), `useVoice` (browser fallback), `useScrollToHash`, and `useOutsideDismiss(open, onClose)` — closes a popover/menu on outside-click or `Escape`, used by `Layout`, `NotificationsBell`, `ContextInfoButton`). `useChat` exposes both `send` (text / quick-action, with an optional `capabilityHint` and an optional page range — sent only for quick-actions) and `sendVoice` (uploads a clip, returns the persisted messages + optional spoken reply). `useQuiz` owns the quiz report too (`report`/`loadReport`, refetched after every graded answer) and threads `quizRequestId` from generation through to answers.
- `src/context/ActivityRangeContext.tsx`: `ActivityRangeProvider` + `useActivityRange()` — the shared **activity page range** (`{start, end}`) for one session, held in React state only (not persisted, re-derived from reading progress on reload), **always physical** regardless of display mode. Default is page 1 → furthest page read and follows as the reader advances; `setRange` pins it (the end still extends past a pin if they read further), `resetRange` returns to auto. Also holds `pageMode`/`setPageMode` (`physical` | `editorial`) — a pure display/input concern; `lib/pagination.ts`'s `toDisplayPage`/`toPhysicalPage` convert at the edges (`ActivityRangeBar`, `PdfViewer`) so `range` itself never needs to change meaning. `PdfViewer` feeds `initTotalPages` / `setReadEnd`; `ActivityRangeBar`, `QuizPanel`, `SummaryModal` and `ChatPanel` consume it. See ADR-024, ADR-030.
- `src/lib/pagination.ts`: `toDisplayPage`/`toPhysicalPage` — the one shared physical↔editorial conversion, mirroring the backend's `PageReference`. Used by both `ActivityRangeBar` and `PdfViewer` so the same page number never silently means two different physical pages depending on which control you're looking at. ADR-030.
- `src/config/endpoints.ts`: the single source of truth for backend route paths used by the frontend.
- `src/lib/errors.ts`: `getErrorMessage(err, fallback?)` — the one place that knows how to pull `{error: string}` out of a failed Axios call (see `docs/backend.md`'s note on the backend's unified error shape). Every data-fetching hook and every `onSubmit`/action handler goes through this helper and exposes an `error` string, instead of swallowing a rejected promise silently or leaving a panel stuck on its loading spinner forever. This is applied consistently across the whole app now: every `use*` hook in `src/hooks/` that calls the API returns `error` alongside its data (`useDocuments`, `useTags`, `useAiProfiles`, `useAppProfile`, `useSession`, `useChat`, `useQuiz`, `useProgress`, `useNotifications`, `useSessionReports`, `useSummary`, `useUserProfile`), and the page/component consuming it renders a red `<p>` near the relevant button/field (see `LoginPage`, `HomePage`, `ProfilePage`, `AiProfilesPage`, `CreateSessionModal`, `TagsBar`, `TagPickerModal`, `ChatPanel`, `QuizPanel`, `PdfViewer`, `ProgressPanel`, `NotificationsBell` for examples of each shape).

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

## Security notes

- **Token storage**: the JWT is in `localStorage` (`booki-auth`), mirrored into the in-memory holder above. `AuthContext` validates the stored object's shape on load and discards a malformed entry. Moving to an `HttpOnly` cookie is a known follow-up (it complicates local dev, so it's deferred while the product isn't public).
- **Markdown links** in chat replies render with `target="_blank" rel="noopener noreferrer nofollow"` (model output can contain links — tabnabbing guard).
- **Uploads** are checked client-side for `application/pdf` type and a 40 MB size cap before the request; the backend re-checks (`%PDF-` magic bytes).
- **Deployed headers** (`frontend/firebase.json`): CSP (`script-src 'self'`, no inline scripts in the built `index.html`; `img-src 'self' data: blob:` — the login/hero backdrops are pure CSS gradients now, no third-party image), `Strict-Transport-Security`, `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy`, `Permissions-Policy`. Verify against the real deploy — a too-strict CSP fails silently; `Content-Security-Policy-Report-Only` is the safe way to iterate.
- `<select>` values are narrowed through guards (`toSessionLanguage`) rather than `as` casts.

## Dev proxy vs. deployed origin

Local dev: `API_BASE` is `/api` and Vite forwards `/api` to `http://localhost:8080`
(see `vite.config.ts`); the app is only accepted at `http://localhost:5173` —
the backend's CORS config rejects any other origin, including `127.0.0.1:5173`.

Deployed: the frontend is a static build on its own origin, so it's built with
`VITE_API_BASE_URL` pointing at the backend's absolute API root, and the
backend's `CORS_ALLOWED_ORIGINS` must list the frontend's origin. See
`docs/deployment.md` Phase 3.
