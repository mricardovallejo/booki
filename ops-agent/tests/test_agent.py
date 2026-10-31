"""Offline tests: fake gcloud/gh runner and fake Claude client. No cloud, no API key."""
import json
from pathlib import Path
from types import SimpleNamespace as NS

import pytest

from booki_ops.agent import AuditLog, OpsAgent
from booki_ops.config import Config, _load_dotenv
from booki_ops.runner import CommandResult, CommandRunner, redact
from booki_ops.tools import Toolbox

DESCRIBE = {
    "metadata": {"name": "booki-backend"},
    "status": {
        "url": "https://booki-backend-abc.a.run.app",
        "latestCreatedRevisionName": "booki-backend-00042-new",
        "latestReadyRevisionName": "booki-backend-00041-old",
        "conditions": [{"type": "Ready", "status": "False", "message": "Revision failed"}],
        "traffic": [{"revisionName": "booki-backend-00041-old", "percent": 100}],
    },
    "spec": {"template": {
        "metadata": {"annotations": {"autoscaling.knative.dev/maxScale": "2"}},
        "spec": {"containers": [{"image": "img", "env": [
            {"name": "DB_PASSWORD", "value": "hunter2-super-secret"},
            {"name": "OPENAI_API_KEY", "value": "sk-proj-abcdefghijklmnop"},
        ]}]},
    }},
}
REVISIONS = [
    {"metadata": {"name": "booki-backend-00042-new"},
     "status": {"conditions": [{"type": "Ready", "status": "False"}]}},
    {"metadata": {"name": "booki-backend-00041-old"},
     "status": {"conditions": [{"type": "Ready", "status": "True"}]}},
]


class FakeRunner(CommandRunner):
    def __init__(self, cfg):
        super().__init__(cfg)
        self.calls: list[list[str]] = []

    def run(self, argv, timeout=90):
        self.calls.append(argv)
        joined = " ".join(argv)
        if "services describe" in joined:
            return CommandResult(argv, 0, json.dumps(DESCRIBE), "")
        if "revisions list" in joined:
            return CommandResult(argv, 0, json.dumps(REVISIONS), "")
        if "logging read" in joined:
            return CommandResult(argv, 0, json.dumps([{
                "timestamp": "t", "severity": "ERROR",
                "resource": {"labels": {"revision_name": "booki-backend-00042-new"}},
                "textPayload": "FlywayValidateException jdbc postgresql://neondb_owner:pw123@host/db",
            }]), "")
        return CommandResult(argv, 0, "ok", "")

    def http_get(self, url, timeout=20):
        return {"url": url, "status": 200, "ms": 5, "body": '{"status":"UP"}'}


@pytest.fixture
def cfg(tmp_path):
    return Config(project="booki-test", region="us-east1", service="booki-backend",
                  frontend_url="https://booki-test.web.app", backend_url=None,
                  github_repo="o/r", deploy_workflow="deploy.yml",
                  impersonate_sa="ops@booki-test.iam.gserviceaccount.com",
                  repo_path=Path(__file__).resolve().parents[2], model="m", max_steps=5,
                  audit_log=tmp_path / "audit.jsonl")


@pytest.fixture
def box(cfg):
    return Toolbox(cfg, FakeRunner(cfg))


def yes(*_):
    return True


def no(*_):
    return False


def test_redact_common_secrets():
    text = ("postgresql://u:pw@h/db Bearer abc.def sk-ant-1234567890abcd "
            "DB_PASSWORD=hunter2 GOOG1EABCDEFGHIJKLMNOPQRSTUV")
    out = redact(text)
    for secret in ("pw@", "abc.def", "sk-ant-1234567890abcd", "hunter2", "GOOG1EABCDEFGHIJ"):
        assert secret not in out


def test_service_status_exposes_env_names_not_values(box):
    out, err = box.execute("get_service_status", {}, yes)
    assert not err
    assert "DB_PASSWORD" in out and "hunter2" not in out and "sk-proj" not in out


def test_gcloud_calls_use_project_and_impersonation(box):
    box.execute("list_revisions", {}, yes)
    argv = box.runner.calls[-1]
    assert argv[:4] == ["gcloud", "run", "revisions", "list"]
    assert "--impersonate-service-account" in argv and "booki-test" in argv


def test_logs_are_redacted_and_filter_input_validated(box):
    out, err = box.execute("read_logs", {"min_severity": "ERROR"}, yes)
    assert not err and "pw123" not in out and "Flyway" in out
    _, err = box.execute("read_logs", {"contains": 'x" OR severity>=DEFAULT'}, yes)
    assert err


def test_declined_action_runs_nothing(box):
    before = len(box.runner.calls)
    out, err = box.execute("restart_service", {"reason": "OOM in logs"}, no)
    assert not err and json.loads(out)["approved"] is False
    assert len(box.runner.calls) == before


def test_approved_rollback_runs_exact_command(box):
    out, err = box.execute("rollback_to_revision",
                           {"revision": "booki-backend-00041-old", "reason": "5xx since 00042"}, yes)
    assert not err, out
    argv = box.runner.calls[-1]
    assert argv[:5] == ["gcloud", "run", "services", "update-traffic", "booki-backend"]
    assert "booki-backend-00041-old=100" in argv


def test_rollback_refuses_unready_revision(box):
    _, err = box.execute("rollback_to_revision",
                         {"revision": "booki-backend-00042-new", "reason": "x"}, yes)
    assert err


def test_action_requires_reason_and_bounds(box):
    assert box.execute("restart_service", {}, yes)[1]
    assert box.execute("set_instance_limits",
                       {"min_instances": 0, "max_instances": 10, "reason": "x"}, yes)[1]


def test_dry_run_never_executes(cfg):
    from dataclasses import replace
    dry = replace(cfg, dry_run=True)
    b = Toolbox(dry, FakeRunner(dry))
    out, _ = b.execute("route_traffic_to_latest", {"reason": "x"}, yes)
    assert json.loads(out)["dryRun"] is True and b.runner.calls == []


def test_unknown_tool_is_an_error(box):
    assert box.execute("run_shell", {"cmd": "rm -rf /"}, yes)[1]


def test_read_repo_doc_search(box):
    out, err = box.execute("read_repo_doc", {"doc": "deployment", "search": "checksum"}, yes)
    assert not err and json.loads(out)["matches"] > 0


def test_dotenv_inline_comments(tmp_path, monkeypatch):
    env = tmp_path / ".env"
    env.write_text("A_KEY=            # comment\nB_KEY=val  # c\n")
    monkeypatch.delenv("A_KEY", raising=False)
    monkeypatch.delenv("B_KEY", raising=False)
    _load_dotenv(env)
    import os
    assert os.environ["A_KEY"] == "" and os.environ["B_KEY"] == "val"


class FakeClient:
    """Scripted Claude: one tool call, then a final answer."""

    def __init__(self):
        self.calls = 0
        self.messages = self

    def create(self, **kw):
        self.calls += 1
        if self.calls == 1:
            return NS(stop_reason="tool_use", content=[
                NS(type="text", text="Reviso el servicio."),
                NS(type="tool_use", id="t1", name="get_service_status", input={})])
        last = kw["messages"][-1]["content"][0]
        assert last["type"] == "tool_result" and last["tool_use_id"] == "t1"
        return NS(stop_reason="end_turn", content=[NS(type="text", text="Diagnóstico listo.")])


def test_agent_loop_feeds_tool_results_back(cfg, box):
    out = []
    agent = OpsAgent(cfg, box, FakeClient(), no, AuditLog(cfg.audit_log), out.append)
    assert agent.run_turn("el chat no responde") == "Diagnóstico listo."
    events = [json.loads(line)["event"] for line in cfg.audit_log.read_text().splitlines()]
    assert events == ["user", "tool", "answer"]
