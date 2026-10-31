# BooKI production facts and known failure modes

Distilled from docs/deployment.md, docs/decisions.md and .github/workflows/deploy.yml.
If a doc and this file disagree, the doc wins (use read_repo_doc).

## Topology
- Frontend: static React/PWA on Firebase Hosting. It calls the backend directly
  (separate origin, CORS) using a URL baked in at build time (VITE_API_BASE_URL).
- Backend: Spring Boot 4 / Java 21 container on Cloud Run, service `booki-backend`.
  Deployed by GitHub Actions on push to main: `verify` (tests + lint) -> `backend`
  (`gcloud run deploy --source backend`) -> `frontend` (firebase deploy).
  Workflow settings: min 1 / max 2 instances, 1Gi memory, port 8080, profile `dev`.
- Database: PostgreSQL on Neon (external to GCP, free tier, compute suspends when
  idle). Flyway runs migrations at startup.
- Files: Google Cloud Storage via the S3 API, HMAC key (S3_ACCESS_KEY / S3_SECRET_KEY).
- AI: OpenAI is the default provider (also covers voice STT/TTS). Claude is a
  per-session secondary provider (ANTHROPIC_API_KEY). The latest commit set the
  default back to openai "until the Anthropic key is fixed".
- Health: /actuator/health and /readiness are public but show only the aggregate.
  Per-dependency detail (db, diskSpace, ssl, storage) needs a JWT; when it is DOWN,
  the logs usually name the failing component.
- Request log line format: `http.request method=.. path=.. status=.. userId=.. durationMs=..`.

## Known failure modes -> signal -> fix
1. Flyway checksum mismatch: `FlywayValidateException: Migration checksum mismatch`.
   New revision never becomes Ready; the old one keeps serving if traffic was not moved.
   Cause: someone edited V1__init.sql (frozen since ADR-032; even comments count).
   Fix: revert that edit in git and redeploy; schema changes go in V2__*.sql.
   NEVER suggest DROP SCHEMA: Neon now holds real user data.
2. Startup bind failure on a numeric property (historic: blank SMTP_PORT ->
   "Failed to bind properties"). Fix: correct the secret / workflow env and redeploy.
3. Missing or placeholder JWT_SECRET: backend runs with an ephemeral per-instance key
   and logs a warning; users get logged out on every restart or new instance (401s).
   Fix: set a real secret in GitHub and redeploy.
4. Database: connection refused / timeouts / `password authentication failed`.
   Causes: Neon password rotated but DB_PASSWORD secret not updated; host is the
   `-pooler` endpoint (Flyway needs the direct host); Neon project suspended or
   free-tier quota hit. Fix is in the Neon console + GitHub secrets, then redeploy.
   A restart does not fix bad credentials.
5. Storage: `SignatureDoesNotMatch`, `AccessDenied`, `NoSuchBucket`, StorageException.
   Causes: HMAC key deleted/rotated, wrong bucket, SA lost Storage Object Admin.
   Symptom: uploads and opening PDFs fail while chat may still work.
6. AI provider: 401 invalid key, 429 rate limit or `insufficient_quota`.
   OpenAI has a $10/month hard limit: past it every chat/quiz/voice call fails while
   health stays UP. Fix: provider dashboard (billing/key), not Cloud Run.
7. Memory: "Memory limit of ... exceeded" / OOMKilled / container restarts.
   PDFs up to 50 MB are held in heap during upload/download. A restart is a
   temporary relief; the real fix is more memory or streaming (code change).
8. Cold start / slowness: first request slow after Neon suspends; if min instances
   dropped to 0, every idle period costs 10-30 s. set_instance_limits(1, 2) fixes the latter.
9. CORS: browser console shows CORS errors, backend logs 403 on OPTIONS; cause is
   CORS_ALLOWED_ORIGINS not matching the frontend origin. Manual secret fix + redeploy.
10. Deploy pipeline: `verify` failing (tests, lint, tsc) means nothing was deployed;
    production still runs the previous revision. Fix the code; re-running will not help.
    Transient failures (network, registry timeouts, GitHub outage) are the only case
    where rerun_deploy is appropriate.
11. Bad new revision that is Ready but broken (5xx spike starting exactly at its
    creation time, previous revision was clean): rollback_to_revision to the last good
    one, then fix forward in code. Note the next push to main deploys again and moves
    traffic to the new latest revision.
