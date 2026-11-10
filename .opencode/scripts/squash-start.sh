#!/usr/bin/env bash
# Starts the /squash operation: validates the worktree, records the former
# HEAD, resets to origin/main, and stages the branch's net changes with a
# squash merge. Any failure after the first mutation restores the former HEAD.
set -u

mutated=0
former=""

fail() {
  printf 'SQUASH: FAIL (%s)\n' "$1"
  shift
  for line in "$@"; do
    printf '%s\n' "$line"
  done
  if [ -n "$former" ]; then
    if [ "$mutated" = 1 ] && git reset --hard "$former" >/dev/null 2>&1; then
      printf 'Former HEAD: %s (restored)\n' "$former"
    else
      printf 'Former HEAD: %s\n' "$former"
    fi
  fi
  exit 0
}

git rev-parse --is-inside-work-tree >/dev/null 2>&1 || fail "not inside a Git worktree"

branch=$(git symbolic-ref --short -q HEAD 2>/dev/null || true)
former=$(git rev-parse --verify -q HEAD 2>/dev/null || true)
base=$(git rev-parse --verify -q origin/main 2>/dev/null || true)
unstaged=$(git diff --name-only 2>/dev/null || true)
staged=$(git diff --cached --name-only 2>/dev/null || true)

[ -n "$branch" ] || fail "detached HEAD"
[ "$branch" != "main" ] || fail "refusing to squash main"
[ -n "$base" ] || fail "origin/main not found"
[ -n "$former" ] || fail "HEAD has no commits"
[ -z "$unstaged" ] || fail "unstaged changes" "Unstaged:" "$unstaged"
[ -z "$staged" ] || fail "staged changes" "Staged:" "$staged"

mutated=1
if ! git reset --hard "$base" >/dev/null; then
  fail "reset to origin/main failed"
fi
if ! git merge --squash "$former" >/dev/null; then
  fail "squash merge failed"
fi
if git diff --cached --quiet; then
  fail "nothing to squash"
fi

printf 'SQUASH: OK\n'
printf 'Former HEAD: %s\n' "$former"
printf 'Base: origin/main\n'
