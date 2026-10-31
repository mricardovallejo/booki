"""Runtime configuration, read once from environment variables (.env supported)."""
from __future__ import annotations

import os
import re
from dataclasses import dataclass
from pathlib import Path


def _load_dotenv(path: Path) -> None:
    """Tiny .env loader so we don't need python-dotenv. Existing env wins."""
    if not path.is_file():
        return
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, _, value = line.partition("=")
        value = re.sub(r"\s+#.*$", "", value.strip())  # inline comment
        if value.startswith("#"):
            value = ""
        os.environ.setdefault(key.strip(), value.strip('"').strip("'"))


@dataclass(frozen=True)
class Config:
    project: str
    region: str
    service: str
    frontend_url: str
    backend_url: str | None
    github_repo: str
    deploy_workflow: str
    impersonate_sa: str | None
    repo_path: Path | None
    model: str
    max_steps: int
    audit_log: Path
    dry_run: bool = False

    @staticmethod
    def from_env(dry_run: bool = False) -> "Config":
        _load_dotenv(Path.cwd() / ".env")
        project = os.environ.get("GCP_PROJECT_ID", "").strip()
        if not project:
            raise SystemExit("GCP_PROJECT_ID is not set (copy .env.example to .env).")
        repo = os.environ.get("BOOKI_REPO_PATH", "").strip()
        return Config(
            project=project,
            region=os.environ.get("GCP_REGION", "us-east1"),
            service=os.environ.get("CLOUD_RUN_SERVICE", "booki-backend"),
            frontend_url=os.environ.get("FRONTEND_URL", f"https://{project}.web.app"),
            backend_url=os.environ.get("BACKEND_URL") or None,
            github_repo=os.environ.get("GITHUB_REPO", "mricardovallejo/booki"),
            deploy_workflow=os.environ.get("DEPLOY_WORKFLOW", "deploy.yml"),
            impersonate_sa=os.environ.get("OPS_SERVICE_ACCOUNT") or None,
            repo_path=Path(repo).expanduser() if repo else None,
            model=os.environ.get("OPS_AGENT_MODEL", "claude-sonnet-5-5"),
            max_steps=int(os.environ.get("OPS_AGENT_MAX_STEPS", "25")),
            audit_log=Path(os.environ.get("OPS_AUDIT_LOG", "audit.jsonl")),
            dry_run=dry_run,
        )
