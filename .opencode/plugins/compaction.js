// Steer OpenCode's compaction summary, and nothing else.
//
// Discovered from .opencode/plugins/ (no configuration needed). It never reads
// files and it never requests compaction: OpenCode decides when to compact
// (opencode.json: compaction.auto).
//
// OpenCode's summary covers the transcript *before* the retained recent tail,
// so a /handoff reply (the newest message) is never summarized. When the newest
// assistant reply carries the handoff marker, use that block as the summary
// instead of asking the model to summarize stale history. Otherwise fall back to
// the normal summarizer with a short steering instruction.
//
// A local plugin edit may need `opencode service restart` to take effect.

const HANDOFF_MARKER = "## Handoff state";

const COMPACTION_INSTRUCTION = [
  "Summarize so the work can continue after this compaction.",
  "Preserve: the branch and objective; verified findings with the evidence that proves them; decisions and their rationale; failed approaches; open questions; the next concrete action; and the paths of any knowledge/* files written or consulted.",
  "Drop: tool transcripts, repeated command output, superseded experiments and resolved questions.",
].join("\n");

function textOf(message) {
  if (message.type === "user") return message.text ?? "";
  if (message.type !== "assistant") return "";
  return (message.content ?? [])
    .filter((part) => part.type === "text")
    .map((part) => part.text)
    .join("\n");
}

/**
 * The handoff is only valid when it is the newest assistant reply: an older one
 * would resurrect state that later work already superseded.
 */
async function latestHandoff(ctx, sessionID) {
  try {
    const messages = await ctx.session.context({ sessionID });
    for (let index = messages.length - 1; index >= 0; index--) {
      const message = messages[index];
      if (message.type !== "assistant") continue;
      const text = textOf(message);
      return text.includes(HANDOFF_MARKER)
        ? text.slice(text.indexOf(HANDOFF_MARKER))
        : null;
    }
  } catch {
    // A failed read falls back to the normal summary.
  }
  return null;
}

export default {
  id: "compaction-steering",
  async setup(ctx) {
    await ctx.session.hook("compaction", async (event) => {
      const handoff = await latestHandoff(ctx, event.sessionID);
      if (handoff) {
        event.result = { summary: handoff };
        return;
      }
      event.system ??= [];
      event.system.push({ type: "text", text: COMPACTION_INSTRUCTION });
    });
  },
};
