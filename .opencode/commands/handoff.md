---
description: Update the active checkpoint before compaction or a session handoff
---

Before the context is compacted, or the session is handed off or paused:

1. Show the current state: !`.opencode/skills/task-management/scripts/agents status`
2. Update the active checkpoint (`knowledge/tasks/<id>.md`, the one linked under
   `knowledge/tasks/.active/`) so a fresh session can continue without replaying
   this conversation. Record, concisely:
   - verified state and important findings, with the evidence that proves them;
   - decisions made and their rationale;
   - failed approaches, so they are not retried without new evidence;
   - open questions and assumptions;
   - remaining tasks (keep genuinely unfinished work unfinished);
   - tests and their results, and anything not yet verified;
   - the single next concrete action.
3. Promote any reusable, verified finding into `knowledge/` (see
   `knowledge/conventions.md`) and remove contradictions.
4. Report the checkpoint path when done.
