---
description: Squash the branch onto origin/main with a message from its diff
agent: build
subagent: true
---

You are running `/squash`. You have no prior conversation. The shell block below
already squashed the branch with `git reset --hard origin/main` followed by
`git merge --squash <former HEAD>`, and restored the former HEAD on failure.
Its output is the only operational input; the staged diff is the only source for
the commit message.

Squash result:

!`bash .opencode/scripts/squash-start.sh`

If the first line is not `SQUASH: OK`, call no tool, change nothing, and reply
with the output unchanged. Stop there.

Otherwise do exactly this:

1. Read the full staged diff against `origin/main` with whitespace ignored:
   `git diff --cached -w origin/main`.
   Use that diff as the only source for the message: no conversation, no commit
   log, no shell output. If the command fails or the diff is empty, run
   `git reset --hard <former HEAD>`, print the former HEAD, and stop.
2. Create exactly one commit with `git commit -F -`, using a heredoc so the
   message keeps its line breaks. Derive the message from the diff:
   - headline: imperative, at most 50 characters, American English, no `!<MR>`
     suffix;
   - a blank line, then a required body limited to the essentials: the final
     state and why it changed. Wrap every body line at 72 characters; count
     them, since visual wrapping does not count. Do not describe intermediate
     or superseded attempts, do not recap files one by one, do not claim tests
     or checks the diff does not show, and do not mention the squash.
3. If the commit fails, run `git reset --hard <former HEAD>`, print the former
   HEAD, and stop.
4. Print exactly:
   - `Former HEAD: <former sha>`
   - `New HEAD: <new sha>`
5. Do nothing else: no push, no amend, no edits, no tests, no knowledge update,
   no second commit.
