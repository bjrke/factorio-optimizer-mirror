# Pitfalls

Verified traps, non-obvious behavior and diagnostic dead ends. An entry belongs
here only with evidence, and should say how to recognise it.

- **A missing `AGENTS.md` rule may just be un-loaded.** OpenCode loads nested
  `AGENTS.md` files only when the agent reads or lists a directory below them.
  A rule that must always apply belongs in the root `AGENTS.md`; anything else can
  live in a nested file or in `knowledge/` and be loaded on demand.
- **`opencode.json` is gitignored.** The repository's OpenCode config is local to
  the checkout (verify with `git check-ignore -v opencode.json`). Do not rely on a
  config change being tracked; put shared agent tooling in `.opencode/skills/`
  and `knowledge/`, both of which are committed.
- **Project OpenCode config uses deprecated V1 keys.** The local `opencode.json`
  uses `permission` and `compaction.tail_turns`; V2 spells these `permissions` and
  `compaction.keep.tokens`. It is local-only, so leave it to the user unless asked.
- **There is no commit-trailer hook; task ↔ commit is the task file's history.**
  A commit that adds or changes `knowledge/tasks/<id>.md` is the one that concerns
  that task (`agents commits <id>`), and `agents tasks <rev>` lists the tasks a
  commit touched. An uncommitted task has no commits yet, and a squash commit owns
  every task file it added.
- **`gradlew` tests run with the project root as the working directory.** Tests
  that shell out can resolve repository files relative to `System.getProperty("user.dir")`.
- **A task's "active" state is a symlink, not just `status:`.** `agents` treats a
  file linked under `knowledge/tasks/.active/` as in progress; `agents complete`
  removes the link (and `validate` rejects a completed task that is still linked).
  The `.active/` directory is gitignored, so it never reaches `origin/main` — but
  that also means a symlink added or removed by hand is invisible to Git, and
  `find ... -type f` will not follow it (use `-type l` or `-L`).
- **Local plugins need a service restart, and are verifiable.** `.opencode/plugins/*.js`
  is loaded by the OpenCode server; a direct-file edit does not always hot-reload, so
  run `opencode service restart` (the current TUI connection drops). Confirm the plugin
  is active with
  `opencode api get '/api/plugin?location%5Bdirectory%5D=<abs-project-dir>'` (the
  default location is `~`, so the project directory must be passed). Verified on
  v2.0.32: a plain `{ id, setup }` default export from a `.js` file loads as an active
  `local` plugin, so no `@opencode/plugin` dependency is required. Test the logic
  without a runtime: `node .opencode/skills/task-management/scripts/plugin-test.mjs`.
- **The context budget can compact the session under you.** Past 200k tokens the
  plugin injects a handoff reminder (finish the current step, start no new work,
  update the checkpoint, then compact); past 250k it calls `session.compact` itself
  (once per session). Compaction runs at the next step boundary, so it will not
  interrupt an in-flight request, but it can land between steps: keep the checkpoint
  current — and compact deliberately at a natural boundary rather than waiting for
  the hard limit. The estimate is `JSON.stringify(messages)` characters ÷ 4, so it tracks
  the outgoing request size, not the model's true token count.
