"""Subprocess + HTTP plumbing. No shell is ever used: argv lists only."""
from __future__ import annotations

import re
import subprocess
import time
import urllib.error
import urllib.request
from dataclasses import dataclass

from .config import Config

MAX_OUTPUT_CHARS = 20_000

_REDACTIONS: list[tuple[re.Pattern[str], str]] = [
    (re.compile(r"(postgres(?:ql)?://[^:/\s]+:)[^@\s]+@", re.I), r"\1[REDACTED]@"),
    (re.compile(r"Bearer\s+[A-Za-z0-9._\-]+", re.I), "Bearer [REDACTED]"),
    (re.compile(r"eyJ[A-Za-z0-9_\-]+\.[A-Za-z0-9_\-]+\.[A-Za-z0-9_\-]+"), "[REDACTED_JWT]"),
    (re.compile(r"sk-(?:ant-|proj-)?[A-Za-z0-9_\-]{10,}"), "[REDACTED_API_KEY]"),
    (re.compile(r"GOOG[A-Z0-9]{20,}"), "[REDACTED_HMAC]"),
    (
        re.compile(
            r"(?i)\b([A-Z0-9_]*(?:password|secret|token|api[_-]?key|access[_-]?key)[A-Z0-9_]*)"
            r"(\"?\s*[:=]\s*\"?)[^\s\"',}]+"
        ),
        r"\1\2[REDACTED]",
    ),
]


def redact(text: str) -> str:
    for pattern, replacement in _REDACTIONS:
        text = pattern.sub(replacement, text)
    return text


def truncate(text: str, limit: int = MAX_OUTPUT_CHARS) -> str:
    if len(text) <= limit:
        return text
    return text[:limit] + f"\n... [truncated {len(text) - limit} chars]"


@dataclass
class CommandResult:
    argv: list[str]
    returncode: int
    stdout: str
    stderr: str

    @property
    def ok(self) -> bool:
        return self.returncode == 0


class CommandRunner:
    def __init__(self, cfg: Config):
        self.cfg = cfg

    def gcloud_argv(self, args: list[str]) -> list[str]:
        argv = ["gcloud", *args, "--project", self.cfg.project, "--quiet"]
        if self.cfg.impersonate_sa:
            argv += ["--impersonate-service-account", self.cfg.impersonate_sa]
        return argv

    def gh_argv(self, args: list[str]) -> list[str]:
        return ["gh", *args, "--repo", self.cfg.github_repo]

    def run(self, argv: list[str], timeout: int = 90) -> CommandResult:
        try:
            proc = subprocess.run(argv, capture_output=True, text=True, timeout=timeout, shell=False)
            return CommandResult(argv, proc.returncode, proc.stdout, proc.stderr)
        except FileNotFoundError:
            return CommandResult(argv, 127, "", f"{argv[0]} is not installed or not on PATH")
        except subprocess.TimeoutExpired:
            return CommandResult(argv, 124, "", f"timed out after {timeout}s")

    def gcloud(self, args: list[str], timeout: int = 90) -> CommandResult:
        return self.run(self.gcloud_argv(args), timeout)

    def gh(self, args: list[str], timeout: int = 90) -> CommandResult:
        return self.run(self.gh_argv(args), timeout)

    def http_get(self, url: str, timeout: int = 20) -> dict:
        started = time.monotonic()
        req = urllib.request.Request(url, headers={"User-Agent": "booki-ops-agent"})
        try:
            with urllib.request.urlopen(req, timeout=timeout) as resp:
                body = resp.read(2000).decode("utf-8", "replace")
                status = resp.status
        except urllib.error.HTTPError as e:
            body = e.read(2000).decode("utf-8", "replace")
            status = e.code
        except Exception as e:  # DNS, TLS, timeout...
            return {"url": url, "error": f"{type(e).__name__}: {e}",
                    "ms": int((time.monotonic() - started) * 1000)}
        return {"url": url, "status": status, "ms": int((time.monotonic() - started) * 1000),
                "body": redact(body[:300])}
