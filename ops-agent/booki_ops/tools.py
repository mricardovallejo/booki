"""The agent's whole reach into the world.

Two kinds of tool:
  * read   - runs immediately, never changes anything.
  * action - builds an exact argv, shows it to the human, runs only on approval.

There is deliberately no free-form shell, no env-var / secret editing, no
database access and nothing that deletes. Those stay manual.
"""
from __future__ import annotations

import json
import re
import shlex
import statistics
import time
from collections import Counter
from dataclasses import dataclass, field
from typing import Any, Callable

from .config import Config
from .runner import CommandRunner, redact, truncate

Approver = Callable[[str, str, list[str]], bool]  # (tool, reason, argv) -> approved?

SEVERITIES = ["DEFAULT", "INFO", "WARNING", "ERROR", "CRITICAL"]
_REVISION_RE = re.compile(r"^[a-z0-9][a-z0-9-]{0,62}$")
_SEARCH_RE = re.compile(r"^[\w .:/\-=]{1,80}$")

DOCS = {
    "deployment": "docs/deployment.md",
    "decisions": "docs/decisions.md",
    "backend": "docs/backend.md",
    "architecture": "docs/architecture.md",
    "local-dev": "docs/local-dev.md",
    "deploy-workflow": ".github/workflows/deploy.yml",
    "application-yml": "backend/src/main/resources/application.yml",
}


class ToolError(Exception):
    """Bad input or a failed precondition; reported back to the model."""


@dataclass
class Tool:
    name: str
    description: str
    schema: dict
    fn: Callable[[dict], Any]
    kind: str = "read"  # "read" | "action"

    def spec(self) -> dict:
        return {"name": self.name, "description": self.description, "input_schema": self.schema}


def _obj(props: dict, required: list[str] | None = None) -> dict:
    return {"type": "object", "properties": props, "required": required or []}


REASON = {"type": "string", "description": "Why, citing the evidence. Shown to the human."}


@dataclass
class Toolbox:
    cfg: Config
    runner: CommandRunner
    tools: dict[str, Tool] = field(default_factory=dict)
    _backend_url: str | None = None

    def __post_init__(self) -> None:
        self._backend_url = self.cfg.backend_url
        for t in self._build():
            self.tools[t.name] = t

    # ------------------------------------------------------------------ public
    def specs(self) -> list[dict]:
        return [t.spec() for t in self.tools.values()]

    def execute(self, name: str, args: dict, approver: Approver) -> tuple[str, bool]:
        """Returns (json_text, is_error)."""
        tool = self.tools.get(name)
        if tool is None:
            return json.dumps({"error": f"unknown tool {name!r}"}), True
        try:
            if tool.kind == "read":
                result = tool.fn(args)
            else:
                result = self._run_action(tool, args, approver)
            return truncate(json.dumps(result, ensure_ascii=False, default=str)), False
        except ToolError as e:
            return json.dumps({"error": str(e)}), True

    # ----------------------------------------------------------------- helpers
    def _gcloud_json(self, args: list[str]) -> Any:
        res = self.runner.gcloud([*args, "--format", "json"])
        if not res.ok:
            raise ToolError(f"gcloud failed ({res.returncode}): {redact(res.stderr.strip())[:1500]}")
        try:
            return json.loads(res.stdout or "null")
        except json.JSONDecodeError as e:
            raise ToolError(f"could not parse gcloud output: {e}") from e

    def _region(self) -> list[str]:
        return ["--region", self.cfg.region]

    def _describe(self) -> dict:
        return self._gcloud_json(["run", "services", "describe", self.cfg.service, *self._region()])

    def _backend(self) -> str:
        if not self._backend_url:
            self._backend_url = (self._describe().get("status") or {}).get("url")
        if not self._backend_url:
            raise ToolError("could not determine the backend URL; set BACKEND_URL")
        return self._backend_url.rstrip("/")

    @staticmethod
    def _int(args: dict, key: str, default: int, lo: int, hi: int) -> int:
        try:
            value = int(args.get(key, default))
        except (TypeError, ValueError) as e:
            raise ToolError(f"{key} must be an integer") from e
        if not lo <= value <= hi:
            raise ToolError(f"{key} must be between {lo} and {hi}")
        return value

    # -------------------------------------------------------------- read tools
    def get_service_status(self, _args: dict) -> dict:
        svc = self._describe()
        status, spec = svc.get("status") or {}, svc.get("spec") or {}
        tmpl = (spec.get("template") or {})
        ann = (tmpl.get("metadata") or {}).get("annotations") or {}
        container = ((tmpl.get("spec") or {}).get("containers") or [{}])[0]
        # Env VALUES hold DB password, API keys, JWT secret: only names leave here.
        env_names = sorted(e.get("name", "") for e in container.get("env") or [])
        return {
            "service": svc.get("metadata", {}).get("name"),
            "url": status.get("url"),
            "latestCreatedRevision": status.get("latestCreatedRevisionName"),
            "latestReadyRevision": status.get("latestReadyRevisionName"),
            "conditions": [
                {k: c.get(k) for k in ("type", "status", "reason", "message", "lastTransitionTime")}
                for c in status.get("conditions") or []
            ],
            "traffic": status.get("traffic"),
            "image": container.get("image"),
            "resources": container.get("resources"),
            "minInstances": ann.get("autoscaling.knative.dev/minScale"),
            "maxInstances": ann.get("autoscaling.knative.dev/maxScale"),
            "envVarNames": env_names,
        }

    def list_revisions(self, args: dict) -> list[dict]:
        limit = self._int(args, "limit", 8, 1, 30)
        revs = self._gcloud_json(["run", "revisions", "list", "--service", self.cfg.service,
                                  *self._region(), "--limit", str(limit)])
        out = []
        for r in revs or []:
            meta, st = r.get("metadata") or {}, r.get("status") or {}
            ready = next((c for c in st.get("conditions") or [] if c.get("type") == "Ready"), {})
            out.append({
                "name": meta.get("name"),
                "created": meta.get("creationTimestamp"),
                "ready": ready.get("status"),
                "readyMessage": ready.get("message"),
                "imageDigest": st.get("imageDigest"),
            })
        return out

    def _log_filter(self, extra: str = "") -> str:
        return (f'resource.type="cloud_run_revision" '
                f'AND resource.labels.service_name="{self.cfg.service}"{extra}')

    def read_logs(self, args: dict) -> list[dict]:
        severity = str(args.get("min_severity", "WARNING")).upper()
        if severity not in SEVERITIES:
            raise ToolError(f"min_severity must be one of {SEVERITIES}")
        minutes = self._int(args, "minutes", 60, 1, 1440)
        limit = self._int(args, "limit", 50, 1, 200)
        extra = f" AND severity>={severity}"
        if rev := args.get("revision"):
            if not _REVISION_RE.match(rev):
                raise ToolError("invalid revision name")
            extra += f' AND resource.labels.revision_name="{rev}"'
        if text := args.get("contains"):
            if not _SEARCH_RE.match(text):
                raise ToolError("contains: letters, digits, spaces and . : / - = only (max 80)")
            extra += f' AND "{text}"'
        entries = self._gcloud_json(["logging", "read", self._log_filter(extra),
                                     "--freshness", f"{minutes}m", "--limit", str(limit)])
        out = []
        for e in entries or []:
            payload = e.get("jsonPayload") or {}
            msg = e.get("textPayload") or payload.get("message") or json.dumps(payload)[:600]
            item = {
                "time": e.get("timestamp"),
                "severity": e.get("severity"),
                "revision": (e.get("resource") or {}).get("labels", {}).get("revision_name"),
                "message": redact(str(msg))[:800],
            }
            if http := e.get("httpRequest"):
                item["http"] = {k: http.get(k) for k in ("requestMethod", "status", "latency")}
                item["http"]["path"] = str(http.get("requestUrl", "")).split("?")[0][-120:]
            out.append(item)
        return out

    def get_request_stats(self, args: dict) -> dict:
        minutes = self._int(args, "minutes", 60, 1, 1440)
        log = f"projects/{self.cfg.project}/logs/run.googleapis.com%2Frequests"
        entries = self._gcloud_json(["logging", "read", self._log_filter(f' AND logName="{log}"'),
                                     "--freshness", f"{minutes}m", "--limit", "1000"])
        classes, by_rev, failing, latencies = Counter(), Counter(), Counter(), []
        for e in entries or []:
            http = e.get("httpRequest") or {}
            code = int(http.get("status") or 0)
            classes[f"{code // 100}xx" if code else "unknown"] += 1
            by_rev[(e.get("resource") or {}).get("labels", {}).get("revision_name")] += 1
            if code >= 500:
                failing[str(http.get("requestUrl", "")).split("?")[0].split("/", 3)[-1][:80]] += 1
            lat = str(http.get("latency", "")).rstrip("s")
            try:
                latencies.append(float(lat))
            except ValueError:
                pass
        total = sum(classes.values())
        stats: dict[str, Any] = {
            "windowMinutes": minutes, "requests": total, "sampledMax": 1000,
            "byStatusClass": dict(classes), "byRevision": dict(by_rev),
            "top5xxPaths": failing.most_common(5),
            "errorRate5xx": round(classes.get("5xx", 0) / total, 3) if total else None,
        }
        if len(latencies) >= 2:
            q = statistics.quantiles(latencies, n=20)
            stats["latencySeconds"] = {"p50": round(statistics.median(latencies), 3),
                                       "p95": round(q[18], 3), "max": round(max(latencies), 3)}
        return stats

    def check_endpoints(self, _args: dict) -> list[dict]:
        base = self._backend()
        return [
            self.runner.http_get(f"{base}/actuator/health"),
            self.runner.http_get(f"{base}/actuator/health/readiness"),
            self.runner.http_get(self.cfg.frontend_url),
        ]

    def list_deploy_runs(self, args: dict) -> list[dict]:
        limit = self._int(args, "limit", 5, 1, 20)
        res = self.runner.gh(["run", "list", "--workflow", self.cfg.deploy_workflow, "--limit",
                              str(limit), "--json",
                              "databaseId,status,conclusion,headSha,displayTitle,createdAt,url,event"])
        if not res.ok:
            raise ToolError(f"gh failed: {redact(res.stderr.strip())[:800]}")
        return json.loads(res.stdout or "[]")

    def get_deploy_failure_log(self, args: dict) -> dict:
        run_id = self._int(args, "run_id", 0, 1, 10**12)
        res = self.runner.gh(["run", "view", str(run_id), "--log-failed"], timeout=120)
        if not res.ok:
            raise ToolError(f"gh failed: {redact(res.stderr.strip())[:800]}")
        lines = res.stdout.splitlines()
        return {"runId": run_id, "lines": len(lines), "tail": redact("\n".join(lines[-150:]))}

    def read_repo_doc(self, args: dict) -> dict:
        if not self.cfg.repo_path:
            raise ToolError("BOOKI_REPO_PATH is not configured")
        doc = args.get("doc")
        if doc not in DOCS:
            raise ToolError(f"doc must be one of {sorted(DOCS)}")
        path = (self.cfg.repo_path / DOCS[doc]).resolve()
        if not path.is_file():
            raise ToolError(f"{DOCS[doc]} not found under BOOKI_REPO_PATH")
        lines = path.read_text(encoding="utf-8").splitlines()
        term = (args.get("search") or "").strip().lower()
        if not term:
            return {"doc": DOCS[doc], "text": truncate("\n".join(lines[:250]), 12_000)}
        hits = [i for i, line in enumerate(lines) if term in line.lower()][:8]
        chunks = ["\n".join(lines[max(0, i - 12): i + 15]) for i in hits]
        return {"doc": DOCS[doc], "matches": len(hits),
                "excerpts": truncate("\n---\n".join(chunks), 12_000)}

    # ------------------------------------------------------------ action plans
    # Each returns the exact argv; nothing runs until the human approves it.
    def plan_rollback(self, args: dict) -> list[str]:
        rev = str(args.get("revision", ""))
        if not _REVISION_RE.match(rev) or not rev.startswith(self.cfg.service + "-"):
            raise ToolError("revision must be a revision name of this service")
        known = {r["name"]: r for r in self.list_revisions({"limit": 30})}
        if rev not in known:
            raise ToolError(f"revision {rev} not found among the last 30")
        if known[rev]["ready"] != "True":
            raise ToolError(f"revision {rev} is not Ready; refusing to send traffic to it")
        return self.runner.gcloud_argv(["run", "services", "update-traffic", self.cfg.service,
                                        *self._region(), "--to-revisions", f"{rev}=100"])

    def plan_traffic_latest(self, _args: dict) -> list[str]:
        return self.runner.gcloud_argv(["run", "services", "update-traffic", self.cfg.service,
                                        *self._region(), "--to-latest"])

    def plan_restart(self, _args: dict) -> list[str]:
        # A label change creates a fresh revision from the same image + config.
        return self.runner.gcloud_argv(["run", "services", "update", self.cfg.service,
                                        *self._region(), "--update-labels",
                                        f"ops-restart={int(time.time())}"])

    def plan_instance_limits(self, args: dict) -> list[str]:
        lo = self._int(args, "min_instances", 1, 0, 1)
        hi = self._int(args, "max_instances", 2, 1, 3)  # cost guard
        if lo > hi:
            raise ToolError("min_instances cannot exceed max_instances")
        return self.runner.gcloud_argv(["run", "services", "update", self.cfg.service,
                                        *self._region(), "--min-instances", str(lo),
                                        "--max-instances", str(hi)])

    def plan_rerun_deploy(self, args: dict) -> list[str]:
        run_id = self._int(args, "run_id", 0, 1, 10**12)
        extra = ["--failed"] if args.get("failed_only", True) else []
        return self.runner.gh_argv(["run", "rerun", str(run_id), *extra])

    def _run_action(self, tool: Tool, args: dict, approver: Approver) -> dict:
        reason = str(args.get("reason", "")).strip()
        if not reason:
            raise ToolError("actions require a 'reason' citing the evidence")
        argv = tool.fn(args)
        if not approver(tool.name, reason, argv):
            return {"approved": False, "message": "The human declined. Do not retry the same "
                    "action; ask what they prefer or suggest a manual step."}
        if self.cfg.dry_run:
            return {"approved": True, "dryRun": True, "command": shlex.join(argv)}
        res = self.runner.run(argv, timeout=300)
        return {"approved": True, "command": shlex.join(argv), "returncode": res.returncode,
                "stdout": redact(res.stdout)[-3000:], "stderr": redact(res.stderr)[-3000:],
                "next": "Verify now: check_endpoints, then get_request_stats / read_logs."}

    # ---------------------------------------------------------------- registry
    def _build(self) -> list[Tool]:
        return [
            Tool("get_service_status",
                 "Cloud Run service state: conditions, latest created vs ready revision, traffic "
                 "split, image, memory, min/max instances and env var NAMES (never values).",
                 _obj({}), self.get_service_status),
            Tool("list_revisions", "Recent Cloud Run revisions, newest first, with Ready status.",
                 _obj({"limit": {"type": "integer", "minimum": 1, "maximum": 30}}),
                 self.list_revisions),
            Tool("read_logs",
                 "Backend logs from Cloud Logging, newest first. Secrets are redacted.",
                 _obj({"min_severity": {"type": "string", "enum": SEVERITIES},
                       "minutes": {"type": "integer", "minimum": 1, "maximum": 1440},
                       "limit": {"type": "integer", "minimum": 1, "maximum": 200},
                       "revision": {"type": "string"},
                       "contains": {"type": "string", "description": "plain text to match"}}),
                 self.read_logs),
            Tool("get_request_stats",
                 "Aggregate HTTP request logs: counts by status class and revision, 5xx rate, "
                 "top failing paths, latency p50/p95.",
                 _obj({"minutes": {"type": "integer", "minimum": 1, "maximum": 1440}}),
                 self.get_request_stats),
            Tool("check_endpoints",
                 "Live HTTP check of backend /actuator/health, /actuator/health/readiness and the "
                 "frontend. Anonymous: health shows only the aggregate UP/DOWN.",
                 _obj({}), self.check_endpoints),
            Tool("list_deploy_runs", "Recent runs of the GitHub Actions deploy workflow.",
                 _obj({"limit": {"type": "integer", "minimum": 1, "maximum": 20}}),
                 self.list_deploy_runs),
            Tool("get_deploy_failure_log", "Tail of the failed steps' logs of one workflow run.",
                 _obj({"run_id": {"type": "integer"}}, ["run_id"]), self.get_deploy_failure_log),
            Tool("read_repo_doc",
                 "Read or search BooKI's own docs (runbook, ADRs, config) in the local repo.",
                 _obj({"doc": {"type": "string", "enum": sorted(DOCS)},
                       "search": {"type": "string"}}, ["doc"]),
                 self.read_repo_doc),
            Tool("rollback_to_revision",
                 "ACTION (human approval): send 100% traffic to an earlier Ready revision.",
                 _obj({"revision": {"type": "string"}, "reason": REASON}, ["revision", "reason"]),
                 self.plan_rollback, "action"),
            Tool("route_traffic_to_latest",
                 "ACTION (human approval): send 100% traffic back to the latest Ready revision.",
                 _obj({"reason": REASON}, ["reason"]), self.plan_traffic_latest, "action"),
            Tool("restart_service",
                 "ACTION (human approval): new revision from the same image and config "
                 "(clears a stuck or leaking instance).",
                 _obj({"reason": REASON}, ["reason"]), self.plan_restart, "action"),
            Tool("set_instance_limits",
                 "ACTION (human approval): set min (0-1) and max (1-3) instances.",
                 _obj({"min_instances": {"type": "integer", "minimum": 0, "maximum": 1},
                       "max_instances": {"type": "integer", "minimum": 1, "maximum": 3},
                       "reason": REASON}, ["min_instances", "max_instances", "reason"]),
                 self.plan_instance_limits, "action"),
            Tool("rerun_deploy",
                 "ACTION (human approval): re-run a GitHub Actions deploy run (default: failed jobs "
                 "only). Only for transient failures, never for a real test or config failure.",
                 _obj({"run_id": {"type": "integer"}, "failed_only": {"type": "boolean"},
                       "reason": REASON}, ["run_id", "reason"]),
                 self.plan_rerun_deploy, "action"),
        ]
