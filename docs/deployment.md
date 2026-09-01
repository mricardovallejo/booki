# BooKI Deployment Plan

Status: **planning**. One deployed environment ("dev, done well"), plus local
development. No staging/prod split yet — the architecture below makes adding one
later a config change, not a redesign.

Guiding priorities: **industry-standard, flexible, vendor-neutral, free right
now**. Frontend, backend, database and file storage are **four separate,
independently deployable pieces** — the conventional shape for a SPA + API + DB.
As much as possible runs inside **one Google Cloud project**; the only piece
that cannot be both on GCP and free is the database, which lives on Neon.

---

## Target architecture

```
   Browser
     │
     ├───────────────▶  FRONTEND  — static build on a CDN
     │                  Firebase Hosting            (GCP · free)
     │
     └──/api───────────▶  BACKEND  — Spring Boot container
                          Google Cloud Run          (GCP · free tier, scales to zero)
                            │
                            ├──▶  DATABASE — managed PostgreSQL
                            │     Neon                (external · free tier)
                            │
                            └──▶  FILE STORAGE — S3-compatible object storage
                                  Google Cloud Storage (GCP · Always Free 5 GB, US regions)
```

Frontend and backend are separate origins ⇒ CORS is configured on the backend
(a few lines).

### Why "all Google except the DB"

Google Cloud has **no free managed PostgreSQL** (Cloud SQL starts ~10 $/mo). So
compute (Cloud Run), storage (GCS), the image registry (Artifact Registry) and
the frontend (Firebase Hosting) all sit in one GCP project with one console,
one bill, one IAM — and the database is the single external account, on Neon,
because Neon's Postgres is genuinely free.

### Why each choice is portable (not lock-in)

| Piece | Choice | Escape hatch |
|---|---|---|
| Frontend | Firebase Hosting | Plain static files — move to Cloudflare Pages / Vercel / Netlify anytime. |
| Backend | Cloud Run | Just an OCI container — runs on Render, Fly, DO, ECS, k8s unchanged. |
| Database | Neon (PostgreSQL) | Standard Postgres wire protocol — `pg_dump`/restore to Cloud SQL, RDS, Supabase, a VPS. |
| File storage | **S3-compatible API** | "S3" is the de-facto standard protocol, not an AWS product. GCS speaks it (XML API + HMAC keys); the same adapter also targets Cloudflare R2, DO Spaces, Backblaze, MinIO, AWS — swap one env var. |

### What is Neon

Managed **serverless PostgreSQL**. A normal Postgres database (any Postgres
client connects), but Neon operates it — backups, patching, HA. "Serverless" =
the compute suspends when idle and resumes in ~1 s, which is what makes the free
tier viable. Also has **branching** (an instant copy-on-write clone of the DB,
data included, to test a migration safely). Equivalent alternative: Supabase.

### What is Sentry (Phase 7)

An **error-monitoring** service. When the backend throws an uncaught exception
(or the frontend crashes), Sentry receives it with the full stack trace, the
user, the request, and the "breadcrumbs" of what happened just before — and
alerts you. Without it, a production error only exists if a user bothers to
report it. Free tier: 5 000 events/mo. Integrates via an SDK + a `SENTRY_DSN`
env var — not business logic. Self-hosted alternative: GlitchTip.

### Cost

Everything above is free for dev + a handful of pilot users. The only line that
can bill anything is Cloud Run with `min-instances=1` to avoid cold starts
(~5–10 $/mo); at `min-instances=0` it is free and pays a ~10–30 s cold start on
the first request after idle — fine for dev.

Note: GCS "Always Free" (5 GB) is **US regions only**. EU-region storage costs a
few cents/month for a few GB — trivial, but not zero. If the US-region
constraint (latency/residency) matters, Cloudflare R2 gives 10 GB free in any
region with no egress fees, and the adapter reaches it by changing
`booki.storage.s3.endpoint`.

Explicitly **not** doing: Kubernetes, autoscaling beyond Cloud Run defaults, a
staging environment, GraalVM native image, private VPC networking (dev-only;
Neon/GCS over TLS on the public internet is acceptable here).

---

## Scope discipline (Java changes)

Java may be edited, but **only where it touches the database or file storage**:

- ✅ Redirect the direct disk calls in `DocumentServiceImpl` / `ReportServiceImpl`
  to go through `StorageAdapter`.
- ✅ Add `StorageAdapter`, `LocalStorageAdapter`, `S3StorageAdapter` + their config.
- ✅ The Flyway migration and the `columnDefinition` strings in 5 domain entities.
- ❌ No changes to the conversation engine, AI providers, quiz, auth, voice,
  controllers (beyond wiring), or anything unrelated.

---

## Phase 1 — Migrate the database MySQL → PostgreSQL ✅ done

Free managed databases are Postgres, and there is no production data anywhere.
Because Flyway has never run against a real deployed database, the 9 existing
migrations are **collapsed into a single Postgres-native `V1__init.sql`** — the
final schema (the net result of today's V1→V9) plus the translated Profile
Master seed. V2–V9 are deleted. Cleaner to review, less to get wrong. Any future
change (once Neon holds real data) is a normal incremental `V2`.

1. **`backend/build.gradle`**
   - `com.mysql:mysql-connector-j` → `org.postgresql:postgresql`.
   - `org.flywaydb:flyway-mysql` → `org.flywaydb:flyway-database-postgresql`.
   - Add `software.amazon.awssdk:s3` (BOM-managed) for Phase 2.
2. **`backend/src/main/resources/application.yml`**
   - `dev` datasource → `jdbc:postgresql://${DB_HOST}:${DB_PORT:5432}/${DB_NAME}`,
     driver `org.postgresql.Driver`, `?sslmode=require` for Neon.
   - `local` and `test` H2 URLs → `MODE=PostgreSQL` (was `MODE=MySQL`).
3. **New `db/migration/V1__init.sql`** (Postgres), replacing all 9:
   - `BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY` (not `AUTO_INCREMENT`).
   - `TEXT` everywhere (no `LONGTEXT`).
   - `TIMESTAMP WITH TIME ZONE` for every `Instant`-backed column (`created_at`,
     `completed_at`, …) — Hibernate maps `Instant` → `timestamp with time
     zone`; `ddl-auto: validate` fails on a plain `TIMESTAMP`.
   - `DOUBLE PRECISION` for `DOUBLE`; `INTEGER` for `INT`.
   - Fold in all later columns: user profile fields (V2), session language (V3),
     tags (V4), quiz_attempts (V5), sent_reports (V6), per-user profile_masters
     (V8), session ai_provider (V9). Seed = the V7 translated Profile Masters.
4. **Entity `columnDefinition`** — `"LONGTEXT"` → `"TEXT"` in `User`,
   `ProfileMaster`, `DocumentPage`, `QuizAttempt` (×3), `Message`
   (`backend/src/main/java/com/booki/domain/*.java`).
5. **Local DB** — replace the `mysql` service in `docker-compose.yml` with
   `postgres:16` (named volume + healthcheck). H2 stays for unit tests, in
   PostgreSQL compatibility mode.
6. **Verify**: `./gradlew clean test`, then `./gradlew bootRunLocal` (Flyway runs
   V1 on H2/PG-mode, Hibernate validates), then `docker compose up -d &&
   ./gradlew bootRun` against real Postgres — upload → session → chat → quiz →
   report end to end.

---

## Phase 2 — File storage: S3-compatible adapter ✅ done

A container's disk is wiped on every deploy, so files move to object storage.
Done in two commits: `09153e6` (the seam + local impl) and the S3 impl + MinIO.

### The seam (`com.booki.storage`)

```
StorageAdapter (interface)
  void      put(String key, byte[] content, String contentType)
  Resource  get(String key)          // throws StorageException if missing
  void      delete(String key)

LocalStorageAdapter   — booki.storage.driver=local (default, and the `test` profile)
  one directory (booki.storage.local-path, default ./storage), key = relative
  path, with a path-traversal guard.

S3StorageAdapter      — booki.storage.driver=s3
  AWS SDK v2 S3Client (sync, url-connection-client; the Netty async client is
  excluded). endpointOverride + forcePathStyle. Objects fetched into a
  ByteArrayResource (BooKI files are whole PDFs ≤ 50 MB).
```

Keys: `documents/<uuid>_<name>.pdf`, `reports/<uuid>.pdf`. The key is persisted
in `documents.file_path` / `sent_reports.file_name` — never an absolute path.
No `@Configuration` needed: each adapter is a `@Component` with
`@ConditionalOnProperty(name = "booki.storage.driver", …)`.

### Config (`application.yml`)

```yaml
booki:
  storage:
    driver: ${STORAGE_DRIVER:local}          # local | s3
    local-path: ${STORAGE_LOCAL_PATH:./storage}
    s3:
      endpoint:   ${S3_ENDPOINT:}             # blank = real AWS; set for MinIO / R2 / GCS
      region:     ${S3_REGION:us-east-1}
      bucket:     ${S3_BUCKET:booki}
      access-key: ${S3_ACCESS_KEY:}
      secret-key: ${S3_SECRET_KEY:}
      path-style: ${S3_PATH_STYLE:true}       # true for MinIO / R2 / GCS
```

### Deployed target — Google Cloud Storage

GCS speaks the S3 XML API. Create a bucket, then an **HMAC key** for a service
account — that key pair is `S3_ACCESS_KEY` / `S3_SECRET_KEY`, with
`S3_ENDPOINT=https://storage.googleapis.com`. GCS + AWS SDK v2 works but has
minor request-signing quirks; if you hit friction, point the same adapter at
**Cloudflare R2** (`https://<accountid>.r2.cloudflarestorage.com`) — no code
change, just env vars.

### Running the S3 path locally — MinIO

`docker compose up -d` now also starts **MinIO** (an S3-compatible server) plus a
one-shot `minio-setup` that creates the `booki` bucket. MinIO is idle unless you
opt in — day-to-day dev stays on `driver=local` and doesn't need it.

To run the backend against MinIO, set (already in `.env.example`):

```
STORAGE_DRIVER=s3
S3_ENDPOINT=http://localhost:9000
S3_ACCESS_KEY=booki
S3_SECRET_KEY=bookibooki
S3_BUCKET=booki
S3_PATH_STYLE=true
S3_REGION=us-east-1
```

MinIO console: <http://localhost:9001> (booki / bookibooki).

### Known limitation

`put` takes the whole file as `byte[]` and `get` returns a `ByteArrayResource`,
so a download or upload holds the file (≤ 50 MB, the multipart cap) in heap.
Fine for a pilot on a 512 MB instance with light traffic; the fix if it bites is
a presigned-URL redirect on download and streamed multipart on upload.

---

## Phase 3 — Frontend API base URL + CORS ✅ done

In production the frontend (Firebase Hosting) and backend (Cloud Run) are
separate origins, so the frontend needs the backend's absolute URL and the
backend needs to allow the frontend's origin.

**Frontend (commit on branch):** `frontend/src/config/endpoints.ts` exports
`API_BASE = import.meta.env.VITE_API_BASE_URL || '/api'`, used by
`api/client.ts` (axios `baseURL`) and `api/documents.ts` (`getDocumentFileUrl`,
a plain URL react-pdf fetches directly). Unset → `/api` → the Vite dev proxy,
unchanged for local dev. `frontend/.env.example` documents it; `src/vite-env.d.ts`
types it. Verified: `npm run build` with `VITE_API_BASE_URL` set bakes the
absolute URL into the bundle.

**At deploy time, set:**
- Firebase Hosting build var `VITE_API_BASE_URL = https://<cloud-run-url>/api`
  (no trailing slash).
- Backend env `CORS_ALLOWED_ORIGINS = https://<project>.web.app,https://<project>.firebaseapp.com`
  (+ any custom domain). No backend code change — `booki.cors.allowed-origins`
  is already comma-split into `CorsConfiguration.setAllowedOrigins`
  ([SecurityConfig.java:66](../backend/src/main/java/com/booki/config/SecurityConfig.java#L66)).

**Check on first deploy:** `vite-plugin-pwa`'s service worker registers on the
Hosting HTTPS origin (voice mic needs HTTPS — no self-signed certs anymore).

---

## Phase 4 — Backend Dockerfile

1. Multi-stage `backend/Dockerfile`:
   - Stage `build`: `eclipse-temurin:21-jdk` → `./gradlew bootJar -x test`.
   - Stage `run`: `eclipse-temurin:21-jre` → copy jar, non-root user,
     `ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-jar","/app/app.jar"]`,
     `EXPOSE 8080`.
2. `.dockerignore`: `build/`, `.gradle/`, `uploads/`, `reports/`, `.env`, `*.md`.
3. Cloud Run service config: region `europe-west1`, port 8080,
   `--min-instances=0` (free, cold starts), `--memory=512Mi`,
   `SPRING_PROFILES_ACTIVE=dev` (the profile that points at Postgres),
   health check on `/actuator/health`.

---

## Phase 5 — Provision (one-time)

1. **Neon**: create project, copy the pooled connection string → `DB_*`.
2. **GCP project**: enable Cloud Run, Artifact Registry, Cloud Storage, Firebase
   Hosting.
   - GCS: create bucket `booki`, create an HMAC key → `S3_ACCESS_KEY` /
     `S3_SECRET_KEY`.
   - Cloud Run: deploy the image (pushed to Artifact Registry), set env vars /
     secrets (`DB_*`, `JWT_SECRET`, `OPENAI_API_KEY`, `ANTHROPIC_API_KEY`,
     `STORAGE_DRIVER=s3`, `S3_*`). Flyway creates the schema on first boot.
   - Firebase Hosting: `firebase init hosting`, `public = frontend/dist`,
     set `VITE_API_BASE_URL` for the build.
3. **Rotate `JWT_SECRET`** — fresh 32+ byte random value, only in the platform
   secret stores, never git. Update `.env.example` for the Postgres + S3 vars.
4. Smoke-test the whole chain from a phone on mobile data.

---

## Phase 6 — CI/CD (GitHub Actions)

`.github/workflows/ci.yml` — on every pull request:
- `backend`: `./gradlew test` (H2, no external services).
- `frontend`: `npm ci && npm run lint && npm run build`.

`.github/workflows/deploy.yml` — on push to `main`:
- Build the backend image → push to Artifact Registry → `gcloud run deploy`
  (`google-github-actions/deploy-cloudrun`).
- Build the frontend → `FirebaseExtended/action-hosting-deploy`.

Secrets: GCP auth via Workload Identity Federation (preferred over a JSON key)
in GitHub repo secrets. Flyway migrations run at startup — fine with one
instance; with 2+ it locks and the others wait.

---

## Phase 7 — Hardening (before inviting anyone)

1. **AI cost guard** — the real financial risk. Per-user rate limit on the
   conversation + voice endpoints (Bucket4j in-memory, or a messages-per-hour
   count). Hard monthly spend alert on the OpenAI account.
2. **DB backups** — Neon keeps 7 days of history on the free tier; add a weekly
   `pg_dump` to GCS via a GitHub Actions cron for longer retention.
3. **Sentry** — Spring + React SDKs, DSN via env var.
4. **Security headers** — confirm Spring Security sets `X-Content-Type-Options` /
   `X-Frame-Options`; add a basic CSP on the frontend.
5. **Auth review** — decide whether the pilot needs email verification /
   password reset, or if a 24 h JWT with no refresh is acceptable.
6. Run `/security-review` on the deployment branch.

---

## Order & sizing

| Phase | Depends on | Effort |
|---|---|---|
| 1 — Postgres (collapsed V1) | — | ✅ done |
| 2 — S3 storage adapter + MinIO | 1 | ✅ done |
| 3 — Frontend URL + CORS | — | ✅ done |
| 4 — Dockerfile | — | ~2–3 h |
| 5 — Provision | 1–4 | ~2–3 h |
| 6 — CI/CD | 4, 5 | ~2–3 h |
| 7 — Hardening | 5 | ongoing |
