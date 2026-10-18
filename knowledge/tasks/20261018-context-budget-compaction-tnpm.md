---
task: 20261018-context-budget-compaction-tnpm
title: "Nudge and trigger compaction at a context budget"
created: 2026-10-18
updated: 2026-10-18
status: completed
branch: bjrke/task-knowledge-system
issue: -
completed: 2026-10-18
---

## Objective

Make the agent hand off and compact before OpenCode's automatic compaction, driven
by a measurable context size, instead of relying on a handoff that cannot be
enforced. Two budgets: 200k (soft — hand off at the next natural boundary) and
250k (hard — the plugin compacts for the agent). Compacting deliberately at any
natural boundary is also allowed.

## Acceptance criteria

- [x] The agent is told to hand off and compact once the context passes the budget.
- [x] Compaction can be requested by the agent itself, not only by the user.
- [x] A fail-safe compacts before the context overflows, and it is re-armed after
      a compaction.
- [x] The soft limit leaves enough room to finish the current step (up to passing
      checks) before the fail-safe fires — 50k of headroom (200k → 250k).
- [x] The agent is told that compacting at a natural boundary is always allowed,
      budget or not.
- [x] Unit-tested without a live OpenCode runtime, and documented.

## Current state

Implemented, green and committed (pre-squash `6644170`, `11756f1`); reopened to widen the hard
budget from 220k to 250k and to state the natural-boundary rule. The plugin measures
the outgoing request and acts on the two budgets; a `compact_session` tool lets the
agent compact deliberately after a handoff.

## Verified findings

- `ctx.session.compact({ sessionID })` exists and "runs at the next step boundary
  by default" (plugin docs, `/home/agent/.local/share/opencode/tool-output/tool_14eb54bf1001mARsc1Q7366OFG`
  around line 831); the API equivalent is `POST /api/session/{sessionID}/compact`.
- The `context` hook receives the assembled outgoing `event.system` and
  `event.messages` before model dispatch, so the request size is measurable there.
- Plugins can register tools with `ctx.tool.transform((editor) => editor.add({...}))`;
  the executor context exposes `sessionID`.
- `plugin-test.mjs`: 28 passed, 0 failed (incl. a 240k case that stays below the
  hard budget). `agents selftest`: 40 passed, 0 failed. `agents validate`: OK.

## Hypotheses / unverified

- Auto-compaction at 250k from inside the `context` hook is queued (next step
  boundary) and therefore does not disturb the in-flight request. The docs state
  the delivery, but this has not been observed with a real overflow.
- The character estimate (JSON length ÷ 4) tracks true tokens within ~±20%;
  enough for a budget, not a billing figure.

## Decisions

- Two budgets: soft 200k → injected reminder to hand off at the next natural
  boundary (finish the current step, start no new work) and compact, repeating until
  the context shrinks; hard 250k → the plugin calls `session.compact` itself, once
  per session, re-armed below the soft limit. The 50k gap leaves room to finish the
  current step up to passing checks.
- Compacting at a natural boundary is always allowed — a finished, verified unit of
  work before switching to unrelated work — so the budget is a backstop, not a
  precondition.
- Register a zero-argument `compact_session` tool so the agent can compact after a
  handoff instead of only asking the user to run `/compact`.
- Keep the plugin injecting instructions only; the estimate and the storage flags
  are the only state it keeps.

## Attempts and outcomes

- First cut: 220k hard (20k headroom); the user asked for 250k because a step
  (including `./gradlew check`) did not reliably fit in 20k. Widened to 250k.
- First cut said only "hand off now"; the user wanted the reminder to allow finishing
  the current step and to allow deliberately compacting at a natural boundary. Both
  are now in the soft reminder, the per-request context reminder and the docs.
- Considered making the hard path only inject a stronger reminder; rejected because
  nothing then prevents an overflow (the user asked for agent-driven compaction).
- Considered a soft-only README instruction (agent asks the user to compact);
  rejected as unenforceable — hence the tool plus the fail-safe.

## Failed approaches (do not repeat without new evidence)

- none — this change is tooling/docs only, so `plugin-test.mjs`, `agents selftest`
  and `agents validate` are the full verification; the Kotlin suite never touches
  `.opencode/` and needs no `./gradlew check` for it.

## Open questions

- Whether the thresholds should be configurable (env/plugin options) rather than
  constants in the file.

## Remaining tasks

- [x] Plugin: budget reminder + fail-safe compaction + `compact_session` tool.
- [x] Widen the hard budget to 250k and add the natural-boundary rule to the plugin.
- [x] `plugin-test.mjs`: budget, headroom, re-arm, and tool coverage.
- [x] Docs: `SKILL.md`, `AGENTS.md`, `knowledge/index.md`, `decisions.md`,
      `pitfalls.md`.

## Tests and verification

- `node .opencode/skills/task-management/scripts/plugin-test.mjs` → 28 passed, 0 failed.
- `.opencode/skills/task-management/scripts/agents selftest` → 40 passed, 0 failed.
- `.opencode/skills/task-management/scripts/agents validate` → OK.

## Next action

- none — closed; the user squashes and pushes `bjrke/task-knowledge-system`.

## Completion report

- Outcome: the hard budget is 250k (was 220k), leaving 50k of headroom between the
  soft and hard limits so a step — including `./gradlew check` — can finish before
  the fail-safe compacts. The soft reminder now says to hand off at the next natural
  boundary: finish the current step, start no new work, update the checkpoint, then
  compact. The per-request context reminder and the docs (`SKILL.md`, `AGENTS.md`,
  `knowledge/index.md`, `decisions.md`, `pitfalls.md`) state that compacting at a
  natural boundary is always allowed, budget or not.
- Verification: `plugin-test.mjs` 28 passed / 0 failed (adds a 240k case that stays
  below the hard budget and checks the reminder wording); `agents selftest` 40/0;
  `agents validate` OK.
- Lessons: the two budgets need a wide enough gap that the current step fits — the
  earlier 20k was not; and the soft reminder must explicitly permit finishing the
  step, or the agent stops mid-check.
- Follow-up: the thresholds stay constants in the plugin; make them configurable
  only if a model with a very different window needs it.

<!-- Previous completion (reopened 2026-10-18 to widen the hard budget):
     plugin soft-reminds at 200k, fails safe at 220k, exposes `compact_session`;
     plugin-test 25/0, selftest 40/0, validate OK; pre-squash commits 6644170,
     11756f1.
     Hard budget now 250k and the natural-boundary rule is documented. -->
