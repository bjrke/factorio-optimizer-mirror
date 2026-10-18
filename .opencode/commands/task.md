---
description: Create or resume the active task checkpoint
---

Follow the `task-management` skill (`.opencode/skills/task-management/SKILL.md`).

Current task state:

!`.opencode/skills/task-management/scripts/agents status`

Request: $ARGUMENTS

If the request names an active task ID, or `status` already points at the task
this request belongs to, resume it: read its checkpoint and continue from
verified facts. Otherwise run
`.opencode/skills/task-management/scripts/agents find` first: if an existing task
on this branch covers the request, `resume` (or `reopen`) it instead of creating a
duplicate. Only then create one with
`.opencode/skills/task-management/scripts/agents new "<objective>"`, fill in the
objective and acceptance criteria, and start working. State the task ID in your
reply.
