# Conventions

How work is done in this repository. These rules are detailed enough that they do
not belong in the always-loaded `AGENTS.md`, but they are binding when they apply.
Read this file before writing or changing tests, and when a workflow question comes
up.

## Testing

- Every behavior change or new logic gets tests **without being asked**, in the
  matching source set (`commonMain`/`jsMain`/`jvmMain` →
  `commonTest`/`jsTest`/`jvmTest`); a missing test means the task is incomplete.
  Exception: pure formatting/docs. Use `kotlin.test`.
- Multiple independent assertions MUST use the `de.bjrke.test` helpers —
  `assertAll`, and `assertTable`/`assertTableOf` for table-driven cases.
  Descriptive names stating expected behavior (e.g.
  `vulcanusSteelUsesLavaNotChestOre`).
- Never express a collection check with `assertTrue`; it hides the failing values:
  - membership → `assertContains(collection, element, message)` (works for
    `Iterable`, `Map` keys, arrays, sequences, strings and ranges). For strings the
    message is the fourth parameter, so pass it by name:
    `assertContains(text, part, message = text)`.
  - absence → `assertContainsNot(collection, element, message)` from
    `de.bjrke.test` (`kotlin.test` has no `assertNotContains`).
  - emptiness → `assertEquals(emptyList(), actual)`, `emptySet()`/`emptyMap()`,
    `assertEquals("", string)`, `assertContentEquals(emptyArray(), array)`, or
    `assertEquals(emptyList(), sequence.toList())`. Never `assertTrue(actual.isEmpty())`.
- `assertTrue`/`assertFalse` are only for genuine predicates (comparisons, flags,
  `any`/`none` domain rules); use `assertIs`, `assertNotNull`, `assertNull`,
  `assertEquals` when they express the check.
- `assertAll` is only for independent assertions about one subject. Repeated
  assertions over cases MUST use `assertTable`/`assertTableOf` with the case as the
  element (`String`, `Pair`, `Triple`, local data class). `assertTable` labels each
  failure with `case: <value>`, so no manual counting is needed; do not collect case
  lambdas in `assertAll`.
- Do not append the actual value's `.toString()` to a message; equality assertions
  already print it. Keep only messages that add context.
- `commonTest` runs on JVM **and** JS: keep it platform-neutral; target specifics
  go in `src/jvmTest` / `src/jsTest`.
- Iterate with `./gradlew jvmTest`; finish with `./gradlew check` +
  `./gradlew spotlessApply`.
- **Never test agent tooling from the app suites.** Everything outside the
  application — `AGENTS.md`, `knowledge/`, `.opencode/`, `tmp/` — is verified by
  the tooling's own checks (`agents selftest`, `plugin-test.mjs`), not by
  `src/*Test` that shells out to `bash` or reaches into `.opencode/`.
  `./gradlew check` covers the app only.

## Component tests (jsTest)

- React components are tested in `src/jsTest` with
  [React Testing Library](https://testing-library.com/docs/react-testing-library/intro/),
  running in the real headless-Chrome karma browser used by `jsBrowserTest`.
- Facade + `ComponentTest` base live in
  `src/jsTest/kotlin/de/bjrke/test/ReactTestingLibrary.kt`: `render(...)`, `screen`,
  `fireEvent`, `within(...)`, `changeTo(...)` / `key(...)`, per-test
  `IS_REACT_ACT_ENVIRONMENT` setup + RTL `cleanup()`. Extend `ComponentTest`.
- Inner component `FC`s are `internal` so tests render them via
  `component.create { ... }`, not the `url...` wrappers (which drag in
  router/recipe context).
- npm test deps: `implementation(npm("<pkg>", "<version>"))` in `jsTest`, refresh
  with `./gradlew kotlinUpgradeYarnLock`; run
  `./gradlew jsBrowserTest --tests "*MaterialSelectorTest*"`.

## Git

- Commit after the work is done (no explicit request needed). Headline ≤ 50 chars;
  body wrapped at 72 chars; keep the existing `<Subject> !<MR>` headline style. No
  task trailer: task ↔ commit is read from the task file's own history
  (`agents commits <TASK-ID>` / `agents tasks <rev>`).
- When asked to squash, choose the upstream that is ahead:
  `base=main; git merge-base --is-ancestor main origin/main && base=origin/main`
  — rebase the branch onto `$base`, then fold the branch's own commits with
  `git reset --soft "$base"` plus a single `git commit`. The squash commit must
  contain only the branch's changes; commits already in `$base` are never
  re-added. After the squash, `agents tasks <squash-commit>` lists every task the
  branch added.

## Knowledge promotion

Promote a finding into `knowledge/` only when it is reusable, stable and verified:

- reusable across tasks, likely to prevent repeated investigation, a stable
  architectural/behavioral fact, a verified convention, or a consequential decision.
- Put task-specific detail in the checkpoint, not in `knowledge/`.
- Mark uncertain items as hypotheses until verified. Cite the evidence (file path,
  test name, command) where practical.
- Prefer updating the existing authoritative file over adding a new one; check for
  duplication and contradictions first; remove guidance that is demonstrably
  outdated.
- Keep `knowledge/index.md` a short navigation aid — it should help decide what to
  load, not require loading everything.
