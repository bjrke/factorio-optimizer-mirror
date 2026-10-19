---
description: Summarize the session and promote durable findings before compaction
---

Before the context is compacted, or the session is handed off or paused:

1. Promote any reusable, stable, verified finding into `knowledge/` (see
   `knowledge/conventions.md`) and remove contradictions.
2. State the following in your reply, concisely, so the compaction summary keeps
   it:
   - the branch and objective;
   - verified state and findings, with the evidence that proves them;
   - decisions made and their rationale;
   - failed approaches, so they are not retried without new evidence;
   - open questions and assumptions;
   - the single next concrete action.

Do not create a handoff file: durable facts live in `knowledge/`, and per-change
history belongs in the commit message.
