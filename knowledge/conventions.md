# Conventions

How work is done in this repository: code shape, Detekt rules, naming, refactors
and Git. These rules are detailed enough that they do not belong in the
always-loaded `AGENTS.md`, but they are binding when they apply. Load this file
when you write or restructure code; testing rules are in `testing.md` and the
area-specific facts are in the other knowledge files.

## Code size and file layout

- Detekt enforces the quality gates and is part of `./gradlew check`. `check` runs
  the type-resolved `detektMainJvm`/`detektTestJvm` (detekt's KMP support registers
  them but does not wire them into `check`, so `build.gradle.kts` does) plus the light
  `detektJsMainSourceSet`/`detektJsTestSourceSet` for the JS-only sources. The plain
  `detekt` task (all of `src`, no type resolution) is **not** in `check`: the typed
  tasks already cover common + JVM, and running both analyzed everything twice. Use
  `./gradlew detekt` for a one-off whole-tree run; it still works and is not removed.
  `config/detekt/detekt.yml` raises `LargeClass.allowedLines` to **500** to keep a
  class within this project's budget. Detekt has no per-file line rule — `LargeClass`
  counts a class, so keep files small by responsibility rather than chasing a line
  count. `MagicNumber` is deliberately disabled: tile offsets, degrees, game data and
  test expectations read better in place; only opaque algorithm limits get a named
  constant. `ReturnCount` ignores guard clauses, a loop may use up to three
  `break`/`continue` statements, and destructuring may bind four names. Import the
  project's own packages by name, never with a wildcard, so every cross-package
  edge is visible in the file's header and review can compare it with the table
  in `code.md`. `de.bjrke.test.*`,
  `kotlin.test.*`, `kotlinx.serialization.json.*` and third-party wildcards stay
  allowed.
  There are **no committed Detekt baselines**, so every finding fails `check`
  directly. Fix a finding by extracting a named responsibility; only suppress a
  proven false positive, with a written reason.
- `TooManyFunctions` allows 11 functions per file/class (Detekt default
  `allowedFunctionsPerFile`/`allowedFunctionsPerClass`). Extracting a long
  method's helpers into one file can replace its finding with a file-level one:
  split by responsibility into separate files or classes instead of collecting
  a dozen writers in one file.
- Package boundaries are the other half of that rule: a package is a domain with a
  stated surface. A cross-package reference is an
  explicit import of the names actually used. The allowed edges are the table in
  `code.md` ("Package dependencies"); they are enforced by the compiler
  (`internal` marks a symbol used outside its package) and by review, not by a test
  — `ForbiddenImport` cannot express them (one flat list, see `code.md` §Traps). A new
  package or a new edge means adding or changing a row on purpose — never widening
  one to silence a failure. A guard that has to prove itself by injecting the
  violation it rejects belongs at the Gradle/build level, not in an app suite.
  A package that outgrows 10 files is cut along its responsibilities (15 is the
  ceiling); review keeps that budget, no task does. There is no line budget; class
  length is capped by `LargeClass` (500).
- Model a compass side as `Direction`; add a dedicated enum only when it narrows
  the domain (`LineSide` keeps a lane to two sides and makes its `when`
  exhaustive) or carries semantics `Direction` cannot express (`PortFlow`,
  `BeltEnd`). A 1:1 rename adds a translation at every caller.
- Split by responsibility, not by line count. The layout shows the target shape:
  `LayoutBoard` owns the mutable grid/placement kernel, `LayoutRouter` the A*,
  `UndergroundStretcher` the run growth, and `BlueprintLayout` the ordered
  pipeline. Components take the board (or plain data) instead of sharing a second
  copy of the state.
- **An `internal` top-level declaration collides with a `private` one of the same
  name in the package.** Graduating a file-private constant to `internal` to share
  it broke `:compileKotlinJvm` with `REDECLARATION … EPSILON` against
  `Optimizer.kt`'s `private const val EPSILON` — a file-private declaration is not
  exempt from the clash. Rename (`FEED_EPSILON`) or keep it private and pass the
  value. Verified splitting `DirectFeed.kt` into `DirectFeedModel.kt`.

## Detekt traps

- **A declaration-level `@Suppress` may not silence a light-mode finding.**
  `MatchingDeclarationName` is reported on the file and the external-interface
  `UnusedParameter` on the interface/property; `@Suppress` on the class or property
  was ignored, while `@file:Suppress(...)` before the package declaration worked for
  all four React `XProps` files and `BlueprintDialogProps.text`. Re-run the report
  after adding a suppression instead of assuming it took.
- **`LongParameterList` ignores data-class constructors.** Detekt's default
  `ignoreDataClasses: true` means a data class may carry many fields without a
  finding. Use that for a genuine value object (`FactorySpec`, `Selection`,
  `RowLanes`) where the fields belong together; do not convert an
  arbitrary argument tuple into a data class just to silence the rule.
- **A file with one top-level declaration must be named after it.**
  `MatchingDeclarationName` flagged `LayoutTunnels.kt` once `Tunnels` was its only
  type; renaming the file (not suppressing the rule) is the fix. Files with several
  top-level declarations are exempt.
- **An exhaustive `when` over every entity reaches Detekt's complexity limit.**
  The ascii glyph switch over every `LayoutEntity` kind reported
  `CyclomaticComplexMethod`; group first (`ChestLikeEntity`, `Plumbing`) and switch
  over the group in its own function — the dispatch stays exhaustive.

## Spelling and naming

- Code, KDoc and commit messages use **American English**: `neighbor`, `color`,
  `center`, `behavior`, `serialize`. Keep an identifier as the game spells it even
  when it differs: the blueprint key is `neighbours`, so `Blueprint.neighbours`,
  `PoleEntity.neighbours` and `neighboursJson()` stay British. British spellings in
  untouched code (`labelled` in `Optimizer`/`Surface`) are not churned just to
  conform; new identifiers and new prose are American (user instruction,
  2026-10-24).

## Behavior-preserving refactors

- Before splitting a layout stage, pin its output with a characterization test.
  `BlueprintLayoutSnapshotTest` hashes the canonical entity/wire list (each
  `BlueprintEntity.toString()` plus the wires, FNV-1a) for the base, chest, fluid
  and band plans. Prove the split is neutral by running the same test in a clean
  worktree at the pre-change commit (`git worktree add /tmp/opencode/<name> HEAD`),
  copying the test in and comparing: identical hashes mean unchanged geometry and
  entity order. Remove the worktree when done.
- Keep the ordered pipeline explicit. Stages are classes that take the board (or
  plain data) and the shared state they own in their constructor; there is no
  stage interface and no DI container, because the `build()` order is
  load-bearing. A stage owns its state and exposes it to later stages
  (`RowPlacer.rows`, `RowPlacer.placedRows`). State that several stages must mutate goes
  in one small holder passed to each (`ImportExtension`), not a lambda or a
  copied value; a scalar several stages update (`poleWestBound`) is a property of
  that holder, not a `var` on `RowLayout`.

## Git

- Commit rules (headline length, body wrapping, no `!<MR>` suffix, no
  novel-length recap, no repetition of `knowledge/` or documentation) are in
  `AGENTS.md` §Git workflow.
- `/squash` is a plugin command, not a prompt: it resets to `origin/main`, stages
  the branch's net changes with a squash merge (restoring the former HEAD on
  failure), writes the commit message from the staged diff with a transient
  `session.generate` call in a child session (no history, so the parent
  conversation is not part of its context), and posts the Former HEAD and the
  new commit's `git show --stat` as a user message that carries `resume: false`.
  `session.prompt` wakes the session unless told otherwise, so without that flag
  the squash would trigger an agent turn. The message is visible because the TUI
  renders user text, unlike a synthetic message, which it shows as a one-line
  notice. The scripts resolve against the plugin's location, but every git call
  runs in the session's directory, so a session moved to a worktree squashes its
  own checkout. The result is one commit containing only the branch's changes.
- **`spotlessApply` after staging leaves the reformat unstaged.** It reorders the
  project imports, so run it before `git add`, or amend after it.

## Knowledge promotion

Promote a finding into `knowledge/` only when it is reusable, stable and verified:

- reusable across changes, likely to prevent repeated investigation, a stable
  architectural/behavioral fact, a verified convention, or a consequential
  decision.
- Try the code first: the reason for a value, order or shape belongs in the KDoc of
  the constant, function or class it governs, where the next editor reads it.
  `decisions.md` is only for a constraint that no single owner file can carry.
- Put the finding in the file that owns the area: a map fact in `code.md`, layout
  behavior in `layout.md` or `lanes-fluids.md`, planning in `planning.md`, a build
  fact in `build.md`, a test fact in `testing.md`, a way of working here. A trap
  goes into that file's `## Traps` section with the evidence that proves it.
- Mark uncertain items as hypotheses until verified. Cite the evidence (file path,
  test name, command) where practical.
- Prefer updating the existing authoritative file over adding a new one; check for
  duplication and contradictions first; remove guidance that is demonstrably
  outdated.
- Keep `knowledge/index.md` a short navigation aid — it should help decide what to
  load, not require loading everything.
