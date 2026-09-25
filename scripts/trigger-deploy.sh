#!/usr/bin/env bash
# Run on nelsen-server AFTER the Actions runner prints "Listening for Jobs".
set -euo pipefail
REPO="${REPO:-tomleejumah/nelsen-savannah}"
TARGET="${1:-all}"
if ! command -v gh >/dev/null 2>&1; then
  echo "Install GitHub CLI (gh) or set GH_TOKEN and use curl."
  echo "curl -fsS -X POST \\
    -H \"Authorization: Bearer \$GH_TOKEN\" \\
    -H \"Accept: application/vnd.github+json\" \\
    https://api.github.com/repos/${REPO}/actions/workflows/deploy.yml/dispatches \\
    -d '{\"ref\":\"main\",\"inputs\":{\"target\":\"${TARGET}\"}}'"
  exit 1
fi
gh workflow run Deploy --repo "$REPO" --ref main -f "target=$TARGET"
echo "Triggered Deploy target=$TARGET — watch: gh run list --repo $REPO --workflow=Deploy --limit 3"
