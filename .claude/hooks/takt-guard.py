#!/usr/bin/env python3
"""Argosy takt guard. PreToolUse hook on Bash: refuses `gh pr create` unless
`scripts/review.sh --pr-body` recorded an APPROVE summary for HEAD.

The decision lives in scripts/ci/takt_gate.py, shared with the takt-summary GitHub workflow.
ARGOSY_SKIP_TAKT=1 passes only with a `Takt-ack:` trailer on HEAD.

Fails open on any internal error.
"""

import importlib
import json
import os
import subprocess
import sys


def load_ci_module(root, name):
    ci_dir = os.path.join(root, "scripts", "ci")
    if ci_dir not in sys.path:
        sys.path.insert(0, ci_dir)
    return importlib.import_module(name)


def head_sha(root):
    try:
        r = subprocess.run(
            ["git", "rev-parse", "HEAD"], cwd=root, capture_output=True, text=True, timeout=15
        )
        return r.stdout.strip() if r.returncode == 0 else None
    except Exception:
        return None


def main():
    try:
        payload = json.load(sys.stdin)
    except Exception:
        return 0

    if (payload.get("tool_name") or "") != "Bash":
        return 0
    cmd = (payload.get("tool_input") or {}).get("command", "") or ""

    root = os.environ.get("CLAUDE_PROJECT_DIR") or os.getcwd()
    try:
        git_command = load_ci_module(root, "git_command")
        takt_gate = load_ci_module(root, "takt_gate")
    except Exception:
        return 0

    if not git_command.runs_gh_pr_create(cmd):
        return 0

    sha = head_sha(root)
    if sha is None:
        return 0
    skip = "ARGOSY_SKIP_TAKT=1" in cmd or os.environ.get("ARGOSY_SKIP_TAKT") == "1"

    allowed, message = takt_gate.decide(root, sha, skip)
    if not allowed:
        sys.stderr.write(message + "\n")
        return 2
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception:
        sys.exit(0)
