# BooKI ops agent

A local CLI that diagnoses BooKI in production and applies a few safe fixes,
**only after you type `si`**. It is an ops tool that lives beside the app; it
does not touch the backend or frontend code.

```
you ──▶ agent (your PC) ──▶ Claude (decides what to look at)
                 │
                 ├─ read freely:  Cloud Run status · revisions · logs · request stats
                 │                /actuator/health · GitHub deploy runs · repo docs
                 │
                 └─ act on "si":  rollback to a Ready revision · traffic to latest
                                  restart · min/max instances (0-1 / 1-3) · rerun deploy
```

## Guarantees

- **No shell.** Claude can only call the 13 tools in `booki_ops/tools.py`; each
  builds an argv list, validated, never run through a shell.
- **Human gate.** Every action prints the exact command and waits for `si`.
  Non-interactive runs auto-decline. `--dry-run` shows approved commands without
  running them.
- **Least privilege in GCP.** It runs as `booki-ops-agent` (custom role: get/list
  and update the Cloud Run service only, no create/delete/IAM) by impersonation,
  so there is no key file. Not the `github-deployer` account.
- **No secrets.** Env var values from Cloud Run never leave the tool (names only);
  logs and command output are redacted (DB URLs, JWTs, API keys, HMAC keys).
- **Out of reach on purpose:** env vars and secrets, the Neon database, code,
  anything that deletes. For those it gives you manual steps.
- **Audit trail.** Every question, tool call and approval goes to `audit.jsonl`.

## Setup (once)

Prerequisites: Python 3.10+, `gcloud` and `gh` installed and logged in as you
(`gcloud auth login`, `gh auth login`), an Anthropic API key.

```bash
cd ops-agent
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env                                    # fill it in
GCP_PROJECT_ID=<your-project-id> ./setup_service_account.sh
# copy the printed OPS_SERVICE_ACCOUNT=... into .env
```

## Use

```bash
python -m booki_ops "los usuarios no pueden subir PDFs desde esta tarde"
python -m booki_ops                  # general health check
python -m booki_ops --dry-run "..."  # rehearse without changing anything
```

Keep talking at the `tú>` prompt; an empty line or `salir` exits.

## Tests

```bash
pip install pytest && python -m pytest -q
```

Offline: a fake `gcloud`/`gh` runner and a scripted Claude client.

## Extending

Add a tool in `Toolbox._build()`: a read tool returns data; an action returns
the argv to run (the gate and audit are automatic). Add new failure patterns
to `booki_ops/knowledge.md` after each real incident.
