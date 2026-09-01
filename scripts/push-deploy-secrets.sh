#!/usr/bin/env bash
# Push the values in .env.deploy to GitHub as repository secrets (and GCP_REGION
# as a repository variable). Re-run after changing any value — e.g. rotating the
# database password.
#
# Needs: gh CLI, authenticated (`gh auth login`), with `repo` scope.
# Usage:  scripts/push-deploy-secrets.sh [path-to-env-file]   (default: .env.deploy)

set -euo pipefail

ENV_FILE="${1:-.env.deploy}"
cd "$(dirname "$0")/.."

[ -f "$ENV_FILE" ] || { echo "No $ENV_FILE — copy .env.deploy.example and fill it in."; exit 1; }
command -v gh >/dev/null || { echo "gh CLI not found (https://cli.github.com)"; exit 1; }
gh auth status >/dev/null 2>&1 || { echo "Run 'gh auth login' first."; exit 1; }

while IFS= read -r line; do
  case "$line" in ''|\#*) continue ;; esac
  key=${line%%=*}
  val=${line#*=}
  case "$key" in [A-Za-z_]*) : ;; *) continue ;; esac
  [ -n "$val" ] || { echo "skip   $key (empty)"; continue; }
  if [ "$key" = "GCP_REGION" ]; then
    gh variable set "$key" --body "$val" >/dev/null && echo "var    $key"
  else
    gh secret set "$key" --body "$val" >/dev/null && echo "secret $key"
  fi
done < "$ENV_FILE"

echo "done."
