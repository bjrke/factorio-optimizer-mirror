// Automatic "persistent task" layer for OpenCode V2.
//
// Discovered from .opencode/plugins/task-context.js (no configuration needed).
// It only *briefs* the agent and watches the context budget; it never reads files
// or runs commands, so it cannot fail on a sandboxed runtime. Durable state itself
// lives under knowledge/.
//
// - context:    reminds every model call where durable state lives, so the reminder
//               survives compaction (it is injected per request, not persisted).
//               It also measures the outgoing request and, past the soft budget,
//               tells the agent to hand off and compact; past the hard budget it
//               requests compaction itself, before OpenCode compacts automatically.
// - compaction: steers the summarizer to keep task-relevant knowledge and drop noise.
// - prompt:     briefs a session once, so a task is created without the user asking.
// - tool:       registers `compact_session`, so the agent can compact on purpose
//               right after a handoff.

let define = (plugin) => plugin;
try {
  const mod = await import("@opencode/plugin");
  if (mod?.Plugin?.define) define = mod.Plugin.define;
} catch {
  // Older/newer runtimes may not expose the helper; plain { id, setup } is the V2 contract.
}

// Context budget in tokens. Soft: ask the agent to hand off and compact at the next
// natural boundary (finish the current step — e.g. let the checks pass — but start no
// new work). Hard: request compaction from the plugin, so automatic compaction is
// never the first thing that happens. Tune these two numbers only.
const SOFT_TOKENS = 200_000;
const HARD_TOKENS = 250_000;
const CHARS_PER_TOKEN = 4;

const CONTEXT_REMINDER = [
  "Task protocol (AGENTS.md): every task lives in knowledge/tasks/; in-progress work is linked under knowledge/tasks/.active/.",
  'Before creating a task, run `.opencode/skills/task-management/scripts/agents find` and reuse one that already covers the work on this branch (`agents resume` or `agents reopen`); otherwise create one with `.opencode/skills/task-management/scripts/agents new "<objective>"`. Do not ask the user for this bookkeeping.',
  "Keep the active checkpoint current, and re-read it (plus knowledge/index.md) after any compaction, because the summary is lossy.",
  "Compact at natural boundaries too: once a unit of work is finished and verified, or before switching to unrelated work, update the checkpoint and call the `compact_session` tool — do not wait for the budget reminder.",
].join("\n");

const COMPACTION_INSTRUCTION = [
  "Summarize so the work can continue after this compaction.",
  "Preserve: the active task id and its checkpoint path, the objective, verified findings, decisions and their rationale, failed approaches, remaining tasks, and the next concrete action.",
  "Drop: superseded experiments, repeated tool output, and resolved questions.",
  "State that the checkpoint under knowledge/tasks/ is the authoritative state and must be re-read before continuing.",
].join("\n");

const BOOTSTRAP = [
  "Persistent task protocol: before doing non-trivial work, read knowledge/index.md and ensure an active task checkpoint exists.",
  'If no task is active, run `.opencode/skills/task-management/scripts/agents find` first: reuse a matching task with `agents resume`/`agents reopen`, else create one with `.opencode/skills/task-management/scripts/agents new "<objective>"`.',
  "Do not ask the user for this bookkeeping.",
].join("\n");

const budgetReminder = (tokens) =>
  [
    `Context budget: this request is ~${Math.round(tokens / 1000)}k tokens, over the ${SOFT_TOKENS / 1000}k soft limit.`,
    "Hand off at the next natural boundary: finish the current step (e.g. let the checks finish), update the active checkpoint, then call the `compact_session` tool — or ask the user to run /compact.",
    "Start no new work first; compacting on purpose keeps the checkpoint authoritative instead of relying on automatic compaction.",
  ].join("\n");

const AUTO_COMPACT_NOTE = [
  `Context budget: ~${HARD_TOKENS / 1000}k tokens reached, so compaction was requested automatically.`,
  "The checkpoint may be stale — update it now, then re-read the active checkpoint and knowledge/index.md before continuing.",
].join("\n");

// Rough character-count estimate; good enough to trigger a budget, not a billing figure.
function estimateTokens(event) {
  let chars = 0;
  for (const part of event.system ?? []) {
    if (part && typeof part.text === "string") chars += part.text.length;
  }
  for (const message of event.messages ?? []) {
    try {
      chars += JSON.stringify(message).length;
    } catch {
      // Nothing serializable to count; the estimate stays a lower bound.
    }
  }
  return Math.ceil(chars / CHARS_PER_TOKEN);
}

export default define({
  id: "task-context",
  async setup(ctx) {
    await ctx.session.hook("context", async (event) => {
      event.system ??= [];
      event.system.push({ type: "text", text: CONTEXT_REMINDER });

      const tokens = estimateTokens(event);
      const key = `budget-hard/${event.sessionID}`;
      const alreadyCompacted = await ctx.storage.get(key);
      if (tokens < SOFT_TOKENS) {
        // After compaction the request shrinks; re-arm the fail-safe for next time.
        if (alreadyCompacted) await ctx.storage.set(key, false);
        return;
      }

      event.system.push({ type: "text", text: budgetReminder(tokens) });
      if (tokens >= HARD_TOKENS && !alreadyCompacted) {
        await ctx.storage.set(key, true);
        await ctx.session.compact({ sessionID: event.sessionID });
        event.system.push({ type: "text", text: AUTO_COMPACT_NOTE });
      }
    });

    await ctx.session.hook("compaction", (event) => {
      event.system ??= [];
      event.system.push({ type: "text", text: COMPACTION_INSTRUCTION });
    });

    await ctx.session.hook("prompt", async (event) => {
      const key = `bootstrapped/${event.sessionID}`;
      if (await ctx.storage.get(key)) return;
      await ctx.storage.set(key, true);
      event.prompt.text = `${BOOTSTRAP}\n\n${event.prompt.text}`;
    });

    if (ctx.tool?.transform) {
      await ctx.tool.transform((editor) => {
        editor.add({
          name: "compact_session",
          description:
            "Request compaction of this session. It runs at the next step boundary. Call it after updating the task checkpoint, when the context budget reminder fires.",
          input: { type: "object", properties: {}, additionalProperties: false },
          execute: async (_input, context) => {
            await ctx.session.compact({ sessionID: context.sessionID });
            return {
              content:
                "Compaction requested; it runs at the next step boundary. Re-read the active checkpoint afterwards.",
            };
          },
        });
      });
    }
  },
});
