#!/usr/bin/env bash
# Commits a staged squash with the commit message on stdin.
#
# Usage: bash squash-finish.sh <former HEAD> [failure reason]
#
# The caller runs squash-start.sh first, which resets to origin/main and stages
# the branch's net changes. A missing message or a failed commit restores the
# former HEAD. The only output is the failure or the final Former/New HEAD pair.
set -u

former=${1:-}
reason=${2:-no commit message}

fail() {
  printf 'SQUASH: FAIL (%s)\n' "$1"
  if [ -n "$former" ]; then
    if git reset --hard "$former" >/dev/null 2>&1; then
      printf 'Former HEAD: %s (restored)\n' "$former"
    else
      printf 'Former HEAD: %s (restore failed)\n' "$former"
    fi
  fi
  exit 1
}

[ -n "$former" ] || fail "no former HEAD"

message=$(cat)
[ -n "$message" ] || fail "$reason"

printf '%s\n' "$message" | git commit -F - >/dev/null || fail "commit failed"

printf 'Former HEAD: %s\n' "$former"
git show --stat HEAD
