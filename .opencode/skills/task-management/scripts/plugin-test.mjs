// Unit test for .opencode/plugins/task-context.js.
//
// Run directly with `node plugin-test.mjs`, or via
// `.opencode/skills/task-management/scripts/agents selftest` (which skips it when
// node is unavailable). The plugin is an ESM module with no relative imports, so it
// is evaluated from a data URL: this also exercises the `@opencode/plugin`
// fallback path.
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";

const here = dirname(fileURLToPath(import.meta.url));
const source = readFileSync(resolve(here, "../../../plugins/task-context.js"), "utf8");
const plugin = (await import("data:text/javascript;base64," + Buffer.from(source).toString("base64")))
  .default;

let pass = 0;
const failures = [];
const check = (name, condition) => (condition ? pass++ : failures.push(name));

check("default export has the task-context id", plugin.id === "task-context");
check("default export has a setup function", typeof plugin.setup === "function");

const hooks = {};
const stored = new Map();
const compactions = [];
const tools = {};
const ctx = {
  session: {
    hook: async (name, callback) => {
      hooks[name] = callback;
      return { dispose() {} };
    },
    compact: async (input) => {
      compactions.push(input.sessionID);
      return { id: "inbox_1" };
    },
  },
  storage: {
    get: async (key) => stored.get(key),
    set: async (key, value) => void stored.set(key, value),
    remove: async (key) => void stored.delete(key),
  },
  tool: {
    transform: async (edit) => {
      const editor = {
        namespace: () => {},
        add: (tool) => {
          tools[tool.name] = tool;
        },
      };
      edit(editor);
      return { dispose() {} };
    },
  },
};

await plugin.setup(ctx);

check("registers a context hook", typeof hooks.context === "function");
check("registers a compaction hook", typeof hooks.compaction === "function");
check("registers a prompt hook", typeof hooks.prompt === "function");
check("registers the compact_session tool", typeof tools.compact_session?.execute === "function");

// --- task reminder (no budget pressure) ---
const contextEvent = { system: [] };
await hooks.context(contextEvent);
check("context hook injects exactly one system block when small", contextEvent.system.length === 1);
check(
  "context reminder points at the agents CLI",
  contextEvent.system[0].text.includes("scripts/agents new"),
);
check(
  "context reminder mentions re-reading after compaction",
  contextEvent.system[0].text.toLowerCase().includes("compaction"),
);
check(
  "context reminder encourages compacting at natural boundaries",
  contextEvent.system[0].text.includes("natural boundaries"),
);

const compactionEvent = { system: [] };
await hooks.compaction(compactionEvent);
check("compaction hook injects a summarizer instruction", compactionEvent.system.length === 1);
check(
  "compaction instruction keeps the checkpoint authoritative",
  compactionEvent.system[0].text.includes("knowledge/tasks/"),
);

const first = { sessionID: "ses_test", prompt: { text: "do the thing" } };
await hooks.prompt(first);
check("prompt hook prepends the bootstrap", first.prompt.text.startsWith("Persistent task protocol"));
check("prompt hook keeps the original request", first.prompt.text.includes("do the thing"));

const second = { sessionID: "ses_test", prompt: { text: "again" } };
await hooks.prompt(second);
check("prompt hook bootstraps once per session", second.prompt.text === "again");

const other = { sessionID: "ses_other", prompt: { text: "hello" } };
await hooks.prompt(other);
check("prompt hook bootstraps each new session", other.prompt.text.startsWith("Persistent task protocol"));

// --- context budget ---
const message = (tokens) => ({ role: "user", content: "x".repeat(tokens * 4 + 1000) });
const budgetEvent = (tokens, sessionID = "ses_budget") => ({
  sessionID,
  system: [],
  messages: [message(tokens)],
});

const soft = budgetEvent(200_000);
await hooks.context(soft);
check("soft budget adds one reminder", soft.system.length === 2);
check("soft reminder names the tool", soft.system[1].text.includes("compact_session"));
check("soft reminder allows finishing the current step", soft.system[1].text.includes("natural boundary"));
check("soft budget does not compact", compactions.length === 0);

const headroom = budgetEvent(240_000);
await hooks.context(headroom);
check(
  "between the soft and hard budgets: reminder but no compaction",
  headroom.system.length === 2 && compactions.length === 0,
);

const hard = budgetEvent(255_000);
await hooks.context(hard);
check("hard budget adds the reminder and the auto note", hard.system.length === 3);
check(
  "hard budget requests compaction once",
  compactions.length === 1 && compactions[0] === "ses_budget",
);

await hooks.context(budgetEvent(255_000));
check("hard budget compacts only once per session", compactions.length === 1);

await hooks.context(budgetEvent(255_000, "ses_other"));
check(
  "hard budget is tracked per session",
  compactions.length === 2 && compactions[1] === "ses_other",
);

await hooks.context(budgetEvent(1_000));
await hooks.context(budgetEvent(255_000));
check("hard budget re-arms after the context shrinks", compactions.length === 3);

// --- the tool ---
const toolResult = await tools.compact_session.execute({}, { sessionID: "ses_tool" });
check("compact_session tool requests compaction", compactions[compactions.length - 1] === "ses_tool");
check(
  "compact_session tool explains the outcome",
  String(toolResult?.content).includes("Compaction requested"),
);

console.log(`plugin-test: ${pass} passed, ${failures.length} failed`);
for (const failure of failures) console.error("FAIL: " + failure);
if (failures.length > 0) process.exit(1);
