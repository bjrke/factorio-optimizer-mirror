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

Start the durable part of the reply with the exact heading `## Handoff state`.
The compaction plugin uses the newest assistant reply's block from that heading
as the summary, so run `/compact` immediately after this reply and do no other
work in between. Without the heading the plugin falls back to steering the
normal summarizer, which only covers the transcript before the retained tail and
therefore keeps stale state.

Do not create a handoff file: durable facts live in `knowledge/`, and per-change
history belongs in the commit message.
