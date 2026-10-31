"""The agent loop: Claude decides which tool to call; actions go through a human."""
from __future__ import annotations

import json
import shlex
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable

from .config import Config
from .tools import Toolbox

KNOWLEDGE = (Path(__file__).parent / "knowledge.md").read_text(encoding="utf-8")


def system_prompt(cfg: Config) -> str:
    return f"""You are BooKI's on-call operations agent. You diagnose production problems
and, only with the human's explicit approval, apply a small set of safe fixes.

Environment: GCP project `{cfg.project}`, region `{cfg.region}`, Cloud Run service
`{cfg.service}`, frontend {cfg.frontend_url}, GitHub repo `{cfg.github_repo}`.

Rules
- Read-only tools: use freely. Action tools: the human sees the exact command and must
  approve it. Propose an action only when the evidence supports it, and put that
  evidence in `reason`.
- If the human declines, do not retry the same action; offer alternatives.
- You cannot change env vars, secrets, the database or code. When the fix needs one of
  those, give precise manual steps (where to click, which secret, then redeploy).
- Never ask for, print or guess secret values.
- Never suggest DROP SCHEMA, editing V1__init.sql, or deleting anything.
- After any approved action, verify (check_endpoints, then stats/logs) and report.

Playbook (stop early once the cause is clear)
1. check_endpoints and get_service_status: is it down, degraded, or fine?
2. get_request_stats: 5xx rate, which revision, which paths.
3. read_logs (ERROR first, widen to WARNING) around when it started.
4. list_revisions and list_deploy_runs: did a deploy coincide with the start?
5. Match against the known failure modes below; read_repo_doc when unsure.

Answer format (the human is learning cloud diagnosis, so teach briefly)
- What is failing, the evidence (timestamps, revisions, numbers), probable cause and
  your confidence.
- One line on what each signal means when it is not obvious.
- The recommended next step: an action for approval, or manual steps.
Reply in the human's language (usually Spanish). Be direct; no filler.

{KNOWLEDGE}"""


class AuditLog:
    def __init__(self, path: Path):
        self.path = path

    def write(self, event: str, **data: Any) -> None:
        record = {"ts": datetime.now(timezone.utc).isoformat(), "event": event, **data}
        with self.path.open("a", encoding="utf-8") as f:
            f.write(json.dumps(record, ensure_ascii=False, default=str) + "\n")


def terminal_approver(audit: AuditLog) -> Callable[[str, str, list[str]], bool]:
    def approve(tool: str, reason: str, argv: list[str]) -> bool:
        print("\n" + "=" * 72)
        print(f"  ACCIÓN PROPUESTA: {tool}")
        print(f"  Motivo: {reason}")
        print(f"  Comando exacto:\n    {shlex.join(argv)}")
        print("=" * 72)
        if not sys.stdin.isatty():
            print("  (sin terminal interactiva: rechazada automáticamente)")
            ok = False
        else:
            ok = input("  Escribe 'si' para aprobar, cualquier otra cosa para rechazar: ") \
                .strip().lower() in {"si", "sí", "yes"}
        audit.write("approval", tool=tool, reason=reason, command=shlex.join(argv), approved=ok)
        print("  -> aprobada\n" if ok else "  -> rechazada\n")
        return ok

    return approve


class OpsAgent:
    def __init__(self, cfg: Config, toolbox: Toolbox, client: Any,
                 approver: Callable[[str, str, list[str]], bool], audit: AuditLog,
                 out: Callable[[str], None] = print):
        self.cfg, self.toolbox, self.client = cfg, toolbox, client
        self.approver, self.audit, self.out = approver, audit, out
        self.messages: list[dict] = []
        self.system = [{"type": "text", "text": system_prompt(cfg),
                        "cache_control": {"type": "ephemeral"}}]

    def run_turn(self, user_text: str) -> str:
        self.messages.append({"role": "user", "content": user_text})
        self.audit.write("user", text=user_text)
        final_text = ""
        for _ in range(self.cfg.max_steps):
            resp = self.client.messages.create(
                model=self.cfg.model, max_tokens=4096, system=self.system,
                tools=self.toolbox.specs(), messages=self.messages)
            self.messages.append({"role": "assistant", "content": resp.content})
            text = "".join(b.text for b in resp.content if b.type == "text").strip()
            if text:
                self.out(text)
                final_text = text
            if resp.stop_reason != "tool_use":
                self.audit.write("answer", text=final_text)
                return final_text
            results = []
            for block in resp.content:
                if block.type != "tool_use":
                    continue
                self.out(f"  · {block.name}({json.dumps(block.input, ensure_ascii=False)})")
                content, is_error = self.toolbox.execute(block.name, block.input, self.approver)
                self.audit.write("tool", tool=block.name, input=block.input,
                                 error=is_error, result=content[:2000])
                results.append({"type": "tool_result", "tool_use_id": block.id,
                                "content": content, "is_error": is_error})
            self.messages.append({"role": "user", "content": results})
        note = f"Límite de {self.cfg.max_steps} pasos alcanzado; dime si continúo."
        self.out(note)
        return note
