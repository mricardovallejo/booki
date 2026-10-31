"""CLI: python -m booki_ops "descripción del problema"  [--dry-run]"""
from __future__ import annotations

import argparse
import sys

from .agent import AuditLog, OpsAgent, terminal_approver
from .config import Config
from .runner import CommandRunner
from .tools import Toolbox

DEFAULT_PROMPT = "Revisa la salud de BooKI en producción y dime si algo está mal."


def main() -> None:
    parser = argparse.ArgumentParser(prog="booki_ops", description="BooKI on-call agent")
    parser.add_argument("problem", nargs="*", help="what you are seeing (optional)")
    parser.add_argument("--dry-run", action="store_true",
                        help="approved actions are shown but not executed")
    args = parser.parse_args()

    try:
        import anthropic
    except ImportError:
        sys.exit("Missing dependency: pip install -r requirements.txt")

    cfg = Config.from_env(dry_run=args.dry_run)
    audit = AuditLog(cfg.audit_log)
    agent = OpsAgent(cfg, Toolbox(cfg, CommandRunner(cfg)), anthropic.Anthropic(default_headers=({'anthropic-workspace-id': cfg.anthropic_workspace_id} if cfg.anthropic_workspace_id else None)),
                     terminal_approver(audit), audit)

    print(f"BooKI ops agent · {cfg.project}/{cfg.service} · modelo {cfg.model}"
          + (" · DRY RUN" if cfg.dry_run else ""))
    prompt = " ".join(args.problem) or DEFAULT_PROMPT
    while True:
        print()
        agent.run_turn(prompt)
        try:
            prompt = input("\ntú> ").strip()
        except (EOFError, KeyboardInterrupt):
            break
        if prompt.lower() in {"", "salir", "exit", "q"}:
            break


if __name__ == "__main__":
    main()
