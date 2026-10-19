// Steer OpenCode's compaction summary, and nothing else.
//
// Discovered from .opencode/plugins/ (no configuration needed). It never reads
// files or runs commands, and it never requests compaction: OpenCode decides when
// to compact (opencode.json: compaction.auto). The only job here is to keep the
// facts a continuing session needs and drop the transcript noise.
//
// A local plugin edit may need `opencode service restart` to take effect.

const COMPACTION_INSTRUCTION = [
  "Summarize so the work can continue after this compaction.",
  "Preserve: the branch and objective; verified findings with the evidence that proves them; decisions and their rationale; failed approaches; open questions; the next concrete action; and the paths of any knowledge/* files written or consulted.",
  "Drop: tool transcripts, repeated command output, superseded experiments and resolved questions.",
].join("\n");

export default {
  id: "compaction-steering",
  async setup(ctx) {
    await ctx.session.hook("compaction", (event) => {
      event.system ??= [];
      event.system.push({ type: "text", text: COMPACTION_INSTRUCTION });
    });
  },
};
