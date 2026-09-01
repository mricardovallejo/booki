# BooKI Deployment Plan

Status: **Phases 1–6 in place.** One deployed environment, plus local
development. Scope is a **minimal first deployment** — stand the app up for a
small trusted group. Sentry, backups, rate-limiting and the rest are deferred
(Phase 7) until there's real traffic; the architecture makes adding them a
config change, not a redesign.

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

## Phase 4 — Backend Dockerfile ✅ done

`backend/Dockerfile` (build context `backend/`), multi-stage:
- **build**: `eclipse-temurin:21-jdk` — deps on their own layer, then
  `./gradlew --no-daemon clean bootJar -x test`. `build.gradle` now does
  `jar { enabled = false }` so `build/libs/` holds only the boot jar.
- **run**: `eclipse-temurin:21-jre`, non-root `booki` user, `EXPOSE 8080`,
  `ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar app.jar"]` with
  `JAVA_OPTS=-XX:MaxRAMPercentage=75.0` (heap tracks the container limit;
  overridable at deploy).

`backend/.dockerignore` keeps the context to sources + the Gradle wrapper.

Build and run locally:

```bash
docker build -t booki-backend backend/
docker run --rm -p 8080:8080 --env-file .env \
  -e DB_HOST=host.docker.internal booki-backend
```

Verified: image builds; the container starts against the Docker Postgres,
Flyway runs, `/actuator/health` is 200, register/login/PDF-upload work.

**Cloud Run** (Phase 5 wires it up): port 8080, `--min-instances=0` (free, cold
starts), `--memory=512Mi`, `SPRING_PROFILES_ACTIVE=dev`, startup probe on
`/actuator/health`. Cloud Run ignores any Docker `HEALTHCHECK`; it uses its own
HTTP probe, so none is set in the Dockerfile.

---

## Phase 5 — Actuator ✅ + provision runbook

### Actuator (done — commit on branch)

`application.yml` exposes `health` + `info` (public — `SecurityConfig` ends with
`anyRequest().permitAll()`; gate `/actuator` before going wide).
`/actuator/health` shows one entry per dependency — `db`, `diskSpace`,
`ssl`, and a custom **`storage`** (a `HealthIndicator` calling
`StorageAdapter.ping()` — `Files.isWritable` for local, `HeadBucket` for S3).
`probes.enabled: true` adds `/actuator/health/{liveness,readiness}` for the
Cloud Run startup probe.

### Setup runbook — one-time, done in the browser

No local `gcloud` needed — the workflow runs it in CI. Everything below is the
console. Copy `.env.deploy.example` → `.env.deploy` (gitignored) and fill each
value in as you go; Step 5 pushes them all to GitHub in one command.

Nothing here bills anything by existing — enabling an API and creating a bucket
or service account is free. Cost comes only from *usage*, and Step 0 caps it.

#### Step 0 — Spend caps (do this first)

- **GCP budget alert.** Billing → *Budgets & alerts* → *Create budget* →
  amount **$5/month**, alert thresholds 50 / 90 / 100 %. This *emails* you; it
  does not hard-stop (that needs a billing-disable Cloud Function — out of scope
  at this size). Catching it at $5 is enough.
- **OpenAI hard limit.** platform.openai.com → *Settings → Limits* →
  *Monthly budget* **$10**. This one *does* stop — past the limit the API
  returns errors instead of charging. This is the real runaway-cost guard;
  BooKI's Google side is ~$0–2/month.

#### Step 1 — Database (Neon) — done

Neon project `patient-field-87633175`, region `us-east-2`, branch `production`.
From the dashboard's *Connect* panel, use the **direct** connection string (host
**without** `-pooler` — Flyway needs a real session, not the transaction
pooler). Split into `DB_HOST`, `DB_PORT` (`5432`), `DB_NAME`, `DB_USER`,
`DB_PASSWORD`. The workflow adds `DB_SSLMODE=require`.

#### Step 2 — GCP project + APIs

1. console.cloud.google.com → project picker → **New Project**, name `booki`.
   Note the generated **Project ID** (`booki-xxxxxx`, not the name) → `GCP_PROJECT_ID`.
2. Billing → link a card to the project (required for Cloud Run even on free tier).
3. Enable these APIs (each link has an **ENABLE** button; the project must be
   selected in the top bar):
   - Cloud Run Admin — `console.cloud.google.com/apis/library/run.googleapis.com`
   - Cloud Build — `.../cloudbuild.googleapis.com`
   - Artifact Registry — `.../artifactregistry.googleapis.com`
   - Firebase Hosting — `.../firebasehosting.googleapis.com`
   - (Cloud Storage is on by default.)
4. **Add Firebase to the project:** console.firebase.google.com → *Add project* →
   pick the **existing** `booki-xxxxxx` from the dropdown (do not create a new
   one) → finish the wizard.
5. Region: **`us-east1`** (South Carolina — close to Neon's AWS `us-east-2`) →
   `GCP_REGION`.

#### Step 3 — Storage bucket + HMAC key

1. Cloud Storage → *Buckets* → *Create* → name (globally unique, e.g.
   `booki-<project-id>`), location **`us-east1`**, uniform access, keep it
   **private**. → `S3_BUCKET`.
2. Cloud Storage → *Settings* → *Interoperability* tab → *Access keys for service
   accounts* → *Create key for a service account* → pick (or create) a service
   account → this yields an **Access key** and **Secret**. → `S3_ACCESS_KEY`,
   `S3_SECRET_KEY`. (These are the S3-protocol credentials for GCS; the workflow
   sets `S3_ENDPOINT=https://storage.googleapis.com`.)

#### Step 4 — Service account for GitHub

1. IAM & Admin → *Service Accounts* → *Create* → name `github-deployer`.
2. Grant roles: **Cloud Run Admin**, **Cloud Build Editor**, **Service Account
   User**, **Storage Admin**, **Firebase Hosting Admin**, **Artifact Registry
   Administrator** (Admin, not just Writer — `run deploy --source` auto-creates
   the `cloud-run-source-deploy` repo on the first run).
3. Open it → *Keys* → *Add key* → *JSON* → download. The whole file's contents →
   `GCP_SA_KEY`.

#### Step 5 — Push secrets to GitHub

With `.env.deploy` filled in:

```bash
gh auth login          # once, needs 'repo' scope
scripts/push-deploy-secrets.sh
```

This creates 13 repository **secrets** and one **variable** (`GCP_REGION`).
Re-run it any time a value changes (e.g. after rotating the DB password).

To add them by hand instead: repo → *Settings* → *Secrets and variables* →
*Actions* — one *New repository secret* per key in `.env.deploy`, and
`GCP_REGION` under the *Variables* tab.

| From | Keys |
|---|---|
| Neon (Step 1) | `DB_HOST` `DB_PORT` `DB_NAME` `DB_USER` `DB_PASSWORD` |
| GCP project (Step 2) | `GCP_PROJECT_ID`, `GCP_REGION` (variable) |
| GCS (Step 3) | `S3_BUCKET` `S3_ACCESS_KEY` `S3_SECRET_KEY` |
| Service account (Step 4) | `GCP_SA_KEY` (JSON, one line) |
| You | `JWT_SECRET` (`openssl rand -base64 32`), `OPENAI_API_KEY`, `CORS_ALLOWED_ORIGINS` = `https://<project-id>.web.app` |

#### Step 6 — First deploy

Merge `deploy/postgres` into `main` (or run the *Deploy (dev)* workflow via
*Actions → Run workflow*). Order is automatic: backend deploys → its stable URL
is passed to the frontend build as `VITE_API_BASE_URL` → frontend deploys to
`https://<project-id>.web.app`. Flyway creates the schema on the backend's first
boot.

#### Step 7 — Verify

- `https://<project-id>.web.app` on a phone (mobile data, not home WiFi):
  register, upload a PDF, chat.
- `https://<cloud-run-url>/actuator/health` → every component `UP`.

#### After it works — rotate the DB password

The Neon password was pasted into a setup chat. Neon dashboard → *Roles* →
`neondb_owner` → *Reset password* → update the `DB_PASSWORD` secret → re-run the
deploy workflow.

---

## Phase 6 — GitHub Actions ✅

- **`.github/workflows/ci.yml`** — on PRs / non-`main` pushes: `./gradlew test`
  and `npm ci && npm run build`. (No `npm run lint` — the repo has no ESLint
  config; wire that up if/when it matters.)
- **`.github/workflows/deploy.yml`** — on push to `main` (or manual):
  - `backend`: `gcloud run deploy booki-backend --source backend` — Cloud Build
    builds `backend/Dockerfile`, deploys with
    `--min-instances 0 --max-instances 2 --memory 512Mi`, env from a generated
    `env.yaml`. Outputs the service URL. `max-instances 2` is the guard against
    a runaway scale-out bill.
  - `frontend`: `needs: backend`, builds with
    `VITE_API_BASE_URL=<backend-url>/api`, then `firebase deploy --only hosting`
    (config in `frontend/firebase.json`, SPA rewrite to `index.html`).
  - Auth: the one `GCP_SA_KEY` JSON key for both `gcloud` and `firebase-tools`.

Flyway runs at startup — fine with one instance (`min-instances 0`, and Cloud
Run won't run two at this traffic); with 2+ it locks and the others wait.

---

## Phase 7 — Deferred until it grows

Not now — the first deployment doesn't need it. Revisit when BooKI has real traffic:

1. **AI cost guard** — per-user rate limit on the conversation + voice endpoints;
   a hard monthly spend alert on the OpenAI account. *(The one item worth a
   glance even now: set a billing alert on the OpenAI key.)*
2. **DB backups** beyond Neon's free 7-day history (weekly `pg_dump` to GCS).
3. **Sentry** (or GlitchTip) for error monitoring.
4. **Security headers** / CSP; gate `/actuator` behind auth.
5. **Auth**: email verification / password reset / refresh tokens.
6. `/security-review` on the branch; Workload Identity Federation instead of the
   SA key.

---

## Order & sizing

| Phase | Depends on | Effort |
|---|---|---|
| 1 — Postgres (collapsed V1) | — | ✅ done |
| 2 — S3 storage adapter + MinIO | 1 | ✅ done |
| 3 — Frontend URL + CORS | — | ✅ done |
| 4 — Dockerfile | — | ✅ done |
| 5 — Actuator + provision runbook | 1–4 | ✅ code; runbook = you, ~1 h in GCP |
| 6 — GitHub Actions (ci + deploy) | 4, 5 | ✅ |
| 7 — Hardening | 5 | deferred until it grows |
