# Kimi Evaluation — BooKI Project

**Date:** 2026-09-02
**Scope:** backend (Spring Boot) + frontend (React/TypeScript/Vite).
**Mandate:** read-only; no code changes.
**Method:** review of the repo documentation (`docs/*.md`, `README.md`, `openapi.yaml`), manual code exploration, and analysis with specialized subagents.

---

> **Remediation status (2026-09-05) — see ADR-016.** Addressed in 3 stages:
> **(1)** frontend quality/architecture (ESLint, `useOutsideDismiss`, valid HTML in cards, error states in hooks, deduplicated palette, voice robustness);
> **(2)** backend quality (`@Transactional`, `WebClient` timeouts, typed DTOs + `@Valid`/`@Size`/`@Pattern`, `count` queries, `kobi`→`booki` typo);
> **(3)** security + details (JWT with no predictable default, `/actuator` behind auth, `GlobalExceptionHandler` no longer leaks `ex.getMessage()`, CORS with an explicit header list, filter rejects tokens for deleted users, prompt-injection fences, `%PDF-` upload validation, STT MIME allowlist; frontend: demo credentials dev-only, security headers in `firebase.json`, in-memory token, markdown links with `rel`, upload validation; CI: `lint`+`tsc` in `ci.yml`, `verify` gate in `deploy.yml`).
> **Accepted / deferred:** JWT in `localStorage` (no `HttpOnly` cookie), account enumeration on `register`, model-JSON routing, a real `prod` profile, rate-limiting, `react-router` 6→7, `generateSummary: Object`, schema cleanup, and the **backend test suite** (these go with the "reader profiles" refactor and the testing stage).

---

## 1. Executive summary

BooKI is a monorepo with a Spring Boot 4.1 (Java 21) backend and a React 18 + TypeScript + Vite + PWA frontend. The product is a conversational assistant for reading PDFs, with page-range sessions, chat/voice, quizzes, summaries, and "Profile Masters". The overall architecture is well thought out: a transport-agnostic conversation engine, swappable AI providers, storage behind an interface (`StorageAdapter`), and a clear layer separation in the backend.

**Notable strengths:**

- Backend with good separation of responsibilities (controller → service interface → impl → repository → domain).
- `ConversationEngine` is genuinely transport-neutral and handles AI provider failures correctly without persisting fake responses.
- Voice is processed in the backend, keeping credentials server-side and unifying text/voice under a single `Message` model.
- The frontend has strict typing, an api/hooks/components split, and consistent error handling with `getErrorMessage`.
- Flyway, profiles (`dev`/`local`/`test`), a multi-stage Dockerfile, Docker Compose and GitHub Actions are all present.

**Critical risks to address before scaling or opening to more users:**

1. **Security:** JWT in `localStorage`, a predictable default JWT secret, a public `/actuator`, possible prompt injection, and auth endpoints with no rate-limiting.
2. **Quality/robustness:** missing `@Transactional` on many service methods (`LazyInitializationException` and non-atomic writes), no timeouts on any `WebClient`, very low test coverage (~4 test files for ~80 production files).
3. **Maintenance:** no ESLint config (the `npm run lint` script fails), demo credentials in the frontend, and endpoints returning `ResponseEntity<Object>` or `Map<String, Integer>` that lose the typed contract.

In short: the project is **functional and well-architected for an MVP**, but needs security hardening, transactions, tests and technical-debt cleanup before going to public production.

---

## 2. Backend

### 2.1 Architecture and code quality

**What's good:**

- Package structure coherent with `docs/architecture.md` and `docs/backend.md`: `controller`, `service`/`service/impl`, `domain`, `repository`, `dto`, `config`, `security`, `ai`, `conversation`, `voice`, `storage`.
- Consistent use of constructor injection + Lombok `@RequiredArgsConstructor`.
- `ConversationEngine` (`backend/src/main/java/com/booki/conversation/ConversationEngine.java`) centralizes the conversational flow and is transport-neutral; `SessionServiceImpl` and `VoiceConversationService` are thin adapters.
- `AiProviderRegistry` (`backend/src/main/java/com/booki/ai/AiProviderRegistry.java`) enables per-session provider selection.
- `StorageAdapter` (`backend/src/main/java/com/booki/storage/StorageAdapter.java`) is a good seam for switching between local disk and S3/GCS/R2/MinIO without touching the rest of the code.
- `CapabilityRegistry` (`backend/src/main/java/com/booki/conversation/capability/CapabilityRegistry.java`) implements provider-neutral routing of conversational capabilities, though with security limitations (see §3.5).

**Concrete issues:**

| # | File(s) | Line(s) | Issue | Severity |
|---|---------|---------|-------|----------|
| 1 | `service/impl/SessionServiceImpl.java` | 105, 110, 146, 151 | Read methods access LAZY associations (`session.getDocument().getTitle()`, `session.getProfileMaster()`) without `@Transactional(readOnly = true)`. Real risk of `LazyInitializationException` in production. | High |
| 2 | `service/impl/DocumentServiceImpl.java` | 46 | `uploadDocument` is not transactional: it writes to storage, then the document, then pages. A mid-way failure leaves orphaned objects. | High |
| 3 | `service/impl/AuthServiceImpl.java` | 26 | `register` saves the user and then seeds the Profile Masters; if seeding fails, the user is left with no masters. | Medium |
| 4 | `service/impl/ReportServiceImpl.java` | 65, 101, 146 | `sendProgressReport`/`sendQuizReport` generate the PDF and then save the row; they are not transactional and also don't send email (the names are misleading). | Medium |
| 5 | `service/impl/SessionProgressCalculator.java` | 26-27 | Loads full lists of messages and attempts just to count them. Should use `countBySessionId`. | Medium |
| 6 | `service/impl/SessionContextBuilder.java` | 49 | Reloads the `User` from the repository even though `session.getUser()` already has the FK. | Low |
| 7 | `controller/SessionController.java` | 49-53 | `updateCurrentPage` accepts `Map<String, Integer>` instead of a typed DTO. | Medium |
| 8 | `controller/SessionController.java` | 97 | `generateSummary` returns `ResponseEntity<Object>`, losing typing and OpenAPI precision. | Medium |
| 9 | Several controllers | various | Many write endpoints don't use `@Valid` on the body: `ProfileMasterController.update`, `UserController.updateCurrentUser`, `QuizController.submitAnswer`, `TagController.rename`, `SessionController.updateCurrentPage`, etc. | Medium |
| 10 | `config/OpenApiConfig.java` | 27 | The OpenAPI title is `"kobi-backend-api"` instead of `"booki-backend-api"` (typo). | Low |
| 11 | `domain/Session.java` / `domain/ProfileMaster.java` | — | `configJson` exists and is always saved as `"{}"`; dead weight. `Session.completedAt` also exists with no logic. | Low |
| 12 | `domain/*.java` | — | No database-level `CHECK` constraints for enum columns (`messages.speaker`, `sessions.difficulty`, etc.). | Low |

### 2.2 Security

**What's good:**

- Passwords hashed with BCrypt (`config/SecurityConfig.java:59-61`).
- Stateless JWT, no server-side sessions (`SecurityConfig.java:43`).
- Emails normalized to lowercase (`service/impl/AuthServiceImpl.java:53-55`).
- Consistent ownership checks via `findByIdAndUserId` in repositories.
- AI and voice provider keys are server-side only.
- `LocalStorageAdapter` prevents path traversal (`storage/LocalStorageAdapter.java:74-80`).

**Risks and vulnerabilities:**

| # | File(s) | Line(s) | Issue | Severity |
|---|---------|---------|-------|----------|
| 1 | `src/main/resources/application.yml` | 45 | `JWT_SECRET` has a predictable, public default value. If someone deploys without changing it, tokens can be signed/validated by anyone. | Critical |
| 2 | `config/SecurityConfig.java` | 45-50 | `/actuator/**` is public (`anyRequest().permitAll()`), and `management.endpoint.health.show-details: always` exposes DB, storage, disk, SSL details, etc. | High |
| 3 | `security/JwtUtil.java` | 53-63 | `extractEmail`/`extractUserId` call `parseToken` without try/catch; a malformed payload can throw an uncaught exception. | Medium |
| 4 | `security/JwtAuthenticationFilter.java` | 35-46 | Doesn't verify the user still exists in the DB; a token for a deleted user stays valid until it expires. | Medium |
| 5 | `service/impl/AuthServiceImpl.java` | 29 | `"Email already registered"` enables account enumeration; it should return the same generic message as login. | Medium |
| 6 | `config/SecurityConfig.java` | 64-73 | CORS: `allowedHeaders(List.of("*"))` + `allowCredentials(true)` is permissive; it also applies to `/**` including `/actuator`. | Medium |
| 7 | `config/GlobalExceptionHandler.java` | 95-99 | The `RuntimeException` handler returns `ex.getMessage()` to the client, which can leak paths, bucket names, internal classes, etc. | High |
| 8 | `service/impl/SessionContextBuilder.java` / `ai/*` / `conversation/capability/*` | various | PDF content, `User.systemPrompt`, `ProfileMaster.systemPrompt` and user messages are concatenated directly into prompts with no delimiters or defensive instructions. Allows prompt injection. | High |
| 9 | `conversation/capability/CapabilityRegistry.java` | 80-95 | JSON routing (`{"capability":"..."}`) depends on the model; a user message could induce the model to emit that JSON and trigger a capability unintentionally. | Medium |
| 10 | `service/impl/DocumentServiceImpl.java` | 47-86 | Doesn't validate that the uploaded file is a PDF before handing it to PDFBox; a 50 MB non-PDF file consumes memory/CPU. | Medium |
| 11 | `voice/OpenAiSpeechToTextProvider.java` | 55 | Accepts the browser's MIME type without validating against an allowlist. | Low |
| 12 | `ai/OpenAiCompatibleProvider.java` / `ClaudeProvider.java` / `voice/*` | various | No explicit timeouts/retries on the `WebClient`s; a hung call can block the request thread indefinitely. | High |
| 13 | `build.gradle` | 76 | `springdoc-openapi` is on the classpath; `/swagger-ui/**` and `/v3/api-docs/**` are not restricted in `SecurityConfig`. | Medium |

### 2.3 Tests

- Only 5 test files for the whole backend:
  - `BackendApplicationTests.java` (context load only).
  - `ConversationEngineTest.java` and `ConversationEngineStreamingTest.java` (well covered).
  - `CapabilityRegistryTest.java`.
  - `VoiceConversationServiceTest.java`.
- **Missing:** controller tests, security tests, repository tests (`@DataJpaTest`), service tests (`DocumentServiceImpl`, `QuizServiceImpl`, `ReportServiceImpl`, etc.), AI provider tests, storage tests.
- The `Dockerfile` runs `-x test`, which is acceptable if CI runs them first, but risky if someone builds straight for production.

### 2.4 Config, build and deployment

- `application.yml` is well structured with `dev`/`local`/`test` profiles.
- `build.gradle` loads the repo-root `.env` in `bootRun`/`bootRunLocal`, useful for local development.
- The `Dockerfile` is multi-stage with a non-root user; good practice.
- `.github/workflows/ci.yml` runs `./gradlew test` and `npm ci && npm run build`.
- `.github/workflows/deploy.yml` deploys the backend to Cloud Run and the frontend to Firebase Hosting.
- `docker-compose.yml` brings up PostgreSQL 16 and MinIO with weak documented credentials (`booki`/`bookibooki`), local only.

**Issues:**

- `spring.profiles.active: dev` by default (`application.yml:5`) could activate dev credentials in production if not overridden.
- `Dockerfile:18` uses `-x test`.
- `deploy.yml:55` uses `--allow-unauthenticated`; this is correct for a public API but must be paired with JWT authentication.
- `max-instances: 2` in Cloud Run caps cost, but `min-instances: 0` implies cold starts.

---

## 3. Frontend

### 3.1 Architecture and code quality

**What's good:**

- Vite + React 18 + TypeScript with `strict`, `noUnusedLocals`, `noUnusedParameters`.
- Clear separation: `pages` → `components` → `api` → `hooks`, as documented in `docs/frontend.md`.
- `src/api/client.ts` centralizes Axios, adds the Bearer token and redirects to `/login` on 401.
- `src/lib/errors.ts` normalizes Axios error handling.
- `src/config/endpoints.ts` is the single source of truth for backend routes.
- `ProtectedRoute` and `AuthContext` handle authentication centrally.
- `tsc --noEmit` passes.

**Concrete issues:**

| # | File(s) | Line(s) | Issue | Severity |
|---|---------|---------|-------|----------|
| 1 | `package.json` | 10 | The `lint` script calls ESLint, but **there is no ESLint config** in the repo. `npm run lint` fails immediately. | High |
| 2 | `src/pages/LoginPage.tsx` | 11-12, 45-50, 101-102 | Demo credentials (`demo@booki.app` / `password`) embedded in source. If the account exists in production, anyone can get in. | High |
| 3 | `src/pages/HomePage.tsx` | — | Very large component mixing upload, search, sorting, tags, modals and deletion. Hard to maintain. | Medium |
| 4 | `src/components/DocumentCard.tsx` | 24-67 | Nests `<span role="button">` inside a `<button>`, producing invalid HTML and unreliable keyboard behavior. | Medium |
| 5 | `src/components/Layout.tsx` | 46-97 | User menu doesn't close on outside click or Escape; missing `aria-expanded`. | Medium |
| 6 | `src/components/NotificationsBell.tsx` / `ContextInfoButton.tsx` | — | Popovers with no outside/Escape close. | Low |
| 7 | `src/pages/LoginPage.tsx` | 18 | `location.state as { from?: Location }` uses the DOM `Location` type instead of `react-router-dom`'s; works by accident. | Low |
| 8 | `src/App.tsx` | 16 | Uses hardcoded `"/"` while `ROUTES.home` exists; inconsistent. | Low |
| 9 | `index.html` | 11 | Loads Google Fonts without `&display=swap`, blocking first paint. | Low |
| 10 | `src/index.css` | 5-14 | CSS variables duplicated in `tailwind.config.js`; two sources of truth. | Low |
| 11 | `src/pages/MastersPage.tsx` | — | Also a very large page; could be split into subcomponents. | Low |

### 3.2 Security

**What's good:**

- No `dangerouslySetInnerHTML` or `eval` in source.
- `react-markdown` disallows raw HTML by default.
- The token is not logged in development (`client.ts:26-54`).
- Vite only exposes `VITE_*` variables to the bundle; secrets in other variables don't leak.

**Risks and vulnerabilities:**

| # | File(s) | Line(s) | Issue | Severity |
|---|---------|---------|-------|----------|
| 1 | `src/context/AuthContext.tsx` | 15, 28, 42, 55 | JWT stored in `localStorage`. Any XSS on the origin can exfiltrate it. | High |
| 2 | `src/api/client.ts` | 11-24 | Reads `localStorage` on every request instead of using an in-memory reference. | Medium |
| 3 | `src/context/AuthContext.tsx` | 27-39 | Validates `JSON.parse` but not the shape of the stored object. | Low |
| 4 | `src/components/ChatPanel.tsx` | 198-209 | `react-markdown` + `remark-gfm` renders links without `rel="noopener noreferrer"` or `target="_blank"`, allowing tabnabbing. | Medium |
| 5 | `src/config/endpoints.ts` / `vite.config.ts` | — | `API_BASE` uses `VITE_API_BASE_URL` in production or `/api` (proxy) in dev. No HTTPS validation in production. | Medium |
| 6 | `src/pages/LoginPage.tsx` | 11-12, 45-50 | Demo credentials in the production bundle. | High |
| 7 | `src/components/CreateSessionModal.tsx` | 48-72, 139, 160 | Validates `startPage` and `endPage` separately; allows `startPage > endPage`. Also casts `as SessionLanguage`/`as AiProvider` without validating against a whitelist. | Medium |
| 8 | `src/pages/ProfilePage.tsx` / `src/pages/MastersPage.tsx` | 59-66 / 137-145 | No length limits on `systemPrompt`, `bio`, etc., before sending to the backend. | Low |
| 9 | `src/pages/HomePage.tsx` / `src/api/documents.ts` | 26-37 / 7-13 | Upload doesn't validate real size or MIME; relies on `accept="application/pdf"`, which is trivial to bypass. | Medium |
| 10 | `src/components/PdfViewer.tsx` | 63-65 | Sends the Bearer token in the PDF's `httpHeaders`; if `API_BASE` were HTTP, the token would travel unencrypted. | Medium |
| 11 | `firebase.json` | 1-7 | No security headers configured (CSP, X-Frame-Options, X-Content-Type-Options, HSTS). | High |
| 12 | `vite.config.ts` | 24-25 | `registerType: 'autoUpdate'` and `devOptions: { enabled: true }`. In production, autoUpdate can push code without user confirmation; verify `devOptions` doesn't affect production builds. | Low |
| 13 | `package.json` | — | Dependencies like `axios ^1.7.4`, `react-pdf ^9.1.0`, `vite ^5.4.1` and `vite-plugin-pwa ^0.20.1` are over a year old. Review with `npm audit`. | Medium |

### 3.3 Data and hooks

- No caching/SWR library. Several components call `useSession(sessionId)` independently, producing duplicate requests (`ChatPanel.tsx:88`, `PdfViewer.tsx:20`).
- `useSessionContext.ts` doesn't return `error` or `loading`; failures are silent.
- `useDocuments.ts` `remove()` doesn't handle error or loading.
- `api/voice.ts:34-38` sends `wantsAudioReply` as a form field, but that field is **not documented in `docs/openapi.yaml`** for `POST /sessions/{id}/voice`.
- `types/index.ts` has minor discrepancies with OpenAPI: `Message` doesn't include `sessionId`, `Tag` doesn't include `createdAt`.

### 3.4 Voice

- `useVoiceRecorder.ts` cleans up the `MediaStream` correctly.
- `useVoice.ts` follows the session language.
- Issues:
  - `useVoiceRecorder.ts:61-79` `stop()` doesn't handle the `MediaRecorder` `onerror` event.
  - `useVoice.ts:30-59` `onerror` resolves `null` with no user feedback.
  - `ChatPanel.tsx:121-144` doesn't explicitly handle microphone permission denial.
  - `ChatPanel.tsx:115-119` creates a new `Audio` each turn without pausing the previous one; they can overlap.

---

## 4. Critical errors and risks, summarized

1. **Insecure JWT by default** (`application.yml:45`). Deployed without `JWT_SECRET`, the app is trivially vulnerable.
2. **Token storage in `localStorage`**. XSS → session theft.
3. **Public Actuator with details**. Internal information exposed without authentication.
4. **No transactions** in services that do multiple writes. Risk of inconsistent data.
5. **No WebClient timeouts**. Calls to AI/voice providers can hang.
6. **Prompt injection**. User/PDF content is concatenated directly into prompts.
7. **Demo credentials in the frontend**. Easily exploitable account if it exists in production.
8. **No ESLint**. The lint script fails, and there's no code-quality gate in CI.
9. **Very low test coverage**. 5 test files for the whole backend; no controller/security/storage/provider tests.
10. **Missing security headers on Firebase Hosting**.

---

## 5. Prioritized recommendations

### Immediate (before any public deployment)

1. **Require a secure `JWT_SECRET`:** remove the default in `application.yml` and fail on startup if it isn't configured (or generate a random one and warn). Minimum 256 bits.
2. **Move the token to an `HttpOnly` + `Secure` + `SameSite=Strict` cookie:** the backend sets it and the frontend reads auth state via `/users/me` or a similar endpoint, eliminating `localStorage`.
3. **Protect `/actuator`:** require authentication or restrict by network; reduce `show-details` to `when-authorized`/`never`.
4. **Remove demo credentials from the frontend** or gate them behind `import.meta.env.DEV`.
5. **Add an ESLint config** and make CI fail if `npm run lint` fails.
6. **Add `@Transactional`** to multi-step services and to reads that walk LAZY associations.
7. **Add timeouts** to all AI/voice provider `WebClient`s.
8. **Sanitize the `RuntimeException` handler** in `GlobalExceptionHandler.java` so it doesn't return `ex.getMessage()` to the client.

### Short term (1-2 sprints)

9. **Mitigate prompt injection:** delimit context/document/user blocks in `SessionContextBuilder`, add defensive instructions, and validate/escape user content before including it.
10. **Rate-limiting** on auth endpoints (Bucket4j / Spring Cloud Gateway / reverse proxy).
11. **`@Valid`** on all write endpoints and add `@Size`/`@Pattern` to free-text DTOs.
12. **Validate uploads:** reject files whose content-type isn't `application/pdf` and add a page / extracted-text-size limit.
13. **Improve CORS:** an explicit header allowlist, and validate that `*` isn't used with credentials.
14. **Add security headers** in `firebase.json`: CSP, X-Frame-Options, X-Content-Type-Options, HSTS.
15. **Expand test coverage:** controller tests (`@WebMvcTest`), repository tests (`@DataJpaTest`), service tests with Mockito, and security tests (JWT, CORS, ownership).
16. **Add `HttpOnly` cookie auth** in the backend and adapt the Axios interceptor to not send `Authorization` manually.

### Medium term

17. Implement token revocation or refresh tokens.
18. Add retry/backoff on AI provider calls.
19. Consider replacing model-JSON routing with a more deterministic approach (explicit client `capabilityHint` only).
20. Introduce SWR/React Query to deduplicate requests and improve UX.
21. Review and update dependencies (`npm audit`, `npm outdated`).

---

## 6. Qualitative metrics

| Area | Backend | Frontend | Notes |
|------|---------|----------|-------|
| Architecture | 8/10 | 7/10 | Backend very well structured; frontend clear but with large components. |
| Code quality | 6/10 | 6/10 | Clean code, but missing transactions, loose typing on some endpoints, and ESLint. |
| Security | 5/10 | 5/10 | Basic auth works, but multiple critical hardening risks. |
| Tests | 3/10 | 2/10 | Very few tests; no ESLint and no UI tests in the frontend. |
| Best practices | 6/10 | 6/10 | Docker, CI, Flyway, profiles; but missing quality gates and security headers. |
| Documentation | 9/10 | 8/10 | Extensive, useful docs; OpenAPI and ADRs in good shape (aside from the title typo). |

**Overall verdict:** BooKI is a solid, well-conceived MVP with an extensible architecture and clear documentation. To go to public production or scale, the priority work is **security hardening, transactions, tests and removing technical debt** (ESLint, demo credentials, typing). The recommendations are ordered by impact and effort.
