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
- **`gradlew` tests run with the project root as the working directory.** Tests
  that shell out can resolve repository files relative to `System.getProperty("user.dir")`.
- **Local plugins need a service restart, and are verifiable.** `.opencode/plugins/*.js`
  is loaded by the OpenCode server; a direct-file edit does not always hot-reload, so
  run `opencode service restart` (the current TUI connection drops). Confirm the plugin
  is active with
  `opencode api get '/api/plugin?location%5Bdirectory%5D=<abs-project-dir>'` (the
  default location is `~`, so the project directory must be passed). Verified on
  v2.0.32: a plain `{ id, setup }` default export from a `.js` file loads as an active
  `local` plugin, so no `@opencode/plugin` dependency is required. `node --check`
  catches syntax errors; behavior needs the runtime.
- **OpenCode compacts automatically; the plugin only steers the summary.** A
  `compaction` hook adds one preservation instruction (verified state, decisions,
  next action, `knowledge/*` paths). `/handoff` produces the same information
  deliberately. There is no agent-initiated compaction, no token budget and no
  handoff file.
- **A band's fluid port sits in a gap column the fluid feed cannot reach.** A
  direct-feed band interleaves its machines, so a member that takes a fluid has its
  port between two machines instead of on the row's outside edge; `feedFluids` then
  routes around it and the pipe network splits ("sulfuric-acid is split over 3
  networks" in `realFluidNetworksAreConnected` for processing-unit and
  utility-science-pack). Recognise it as a band/fluid interaction, not a routing
  regression. `DirectFeed.kt` therefore skips any recipe that takes a fluid, as
  producer *and* as consumer, until the fluid feed is reworked (see
  `decisions.md`, 2026-10-18).
- **A band stands in one row of up to 18 machines, so a wide band forces belt
  crossings.** `GROUP_MAX_MACHINES = 18` (`DirectFeed.kt`) is what a band may hold;
  rows above a band then have to reach past it, and a belt that crosses another belt
  can only tunnel. Evidence: the reported `advanced-circuit` URL went from "no
  underground belts" to two copper-cable tunnels once its cable row joined a band
  (`advancedCircuitUrlOnlyTunnelsForItsCableFeed` locks the new state in). Line
  breaking a band too wide is a deferred follow-up, not a bug in the router.
- **An underground run's far end must be matched by material, not by position.**
  Two runs can tunnel in the same column, so a test that looks for "the next
  underground belt in this direction" finds a *foreign* one and reports a phantom
  violation. `realBlueprintsStretchUndergroundRuns` did exactly that for
  `advanced-circuit` ("input (38,22) can still grow back") after the band reshaped
  the layout; the layout's own `undergroundOutputOf` compares the material, and the
  test now does too. Match material (or `blueprintTransportMaterials`' map) whenever
  you search along a run.
- **A blueprint does not rebuild circuit wires — store them in `wires`.** The
  top-level `blueprint.wires` array lists `[entity, connector, entity, connector]`
  quadruples; the game draws nothing from proximity. A chest-connector inserter
  needs it: its `circuit_condition` (e.g. `electronic-circuit < 200`) reads the
  chest's contents, which only works with a wire from the inserter to the chest
  (connector `1` on both ends). `BlueprintLayout.chestWires()` derives them from the
  final placed entities and `Blueprint.toJson()` emits the array; pole copper wires
  are the same idea, stored per pole in `neighbours`. Verified by generating and
  decoding a `buffer-chest` layout: `wires: [[29,1,31,1],[31,1,30,1]]`.
- **`poleWestBound` keeps poles out of the import extension.** The pole pass
  (`bestPoleSpot`, `freeNear`) places a pole only at `x >= poleWestBound`, which
  `importSource`/`trimImportPrefixes`/`fluidMarker` raise to the import connector's
  east edge. A chest marker's inserters sit *inside* that zone, so the bound must be
  skipped when `beltEnd.isChest` — in all four places, including the compacted
  import shift — or `assertPolesCoverAndConnect` fails for the connector inserters.
- **A chest needs ground; a space platform allows only the label.**
  `BeltEnd.allowedOn(Surface.SPACE)` is false for every chest. The UI disables those
  radio options and both entry points (`BeltEnd.fromSelection`, the CLI) clamp a
  chest code to `constant-combinator`; do not special-case the label again
  elsewhere.
- **Package boundaries cannot be derived from Kotlin imports.** `common.business`
  imports `common.model.*` in most files, and the JSON/packaging code imports only
  Kotlin and kotlinx. An import- or grep-based checker therefore sees almost no
  `business → model` edges and cannot tell a wildcard import from a real use. Text
  scanning is also what a checker must survive: the project has multi-line
  constructor headers (`data class Request(` with parameters on following lines,
  at brace depth 0), one-line and multi-line KDoc, KDoc references (`[Factory]`),
  and the same name declared in several packages (`material`, `recipe`, `name` in
  `common.business.fixtures` and as parameter names). A hand-written scanner hit all
  of these; see `decisions.md` (2026-10-21).
- **Classycle is not usable for this project.** Classycle analyses Java class files
  and its Gradle plugins are unmaintained (Portal versions 2015–2018); it expects
  Java outer-class naming and `extends`, which Kotlin bytecode and metadata do not
  match, so edges get silently dropped. A Kotlin-native tool or a real parser is
  needed; detekt's `DependencyRules`/`ForbiddenImport` see imports only and cannot
  resolve Kotlin top-level declarations.
- **A `buildSrc` test suite never runs from the root build.** The root build drives
  buildSrc only through `:classes`/`:jar`, so `buildSrc/src/test` is dead unless
  invoked. `tasks.named("jar") { dependsOn(tasks.test) }` fails with a circular
  dependency (`test → testClasses → compileTestKotlin → jar`), and a root
  `GradleBuild` task fails with "build name 'buildSrc' … reserved name". buildSrc
  carries no tests today; wire a CI line plus `./gradlew -p buildSrc test` locally if
  one is added. See `conventions.md`.
- **Detekt's type-resolved KMP tasks report `expect`/`actual` compiler errors.** Both
  `detektMainJvm` and `detektTestJvm` print "There were 3 compiler errors found during
  analysis. This affects accuracy of reporting." (`debug = true` shows
  `loadPrototypeDump: expect and corresponding actual are declared in the same
  module`). detekt analyses `commonMain` and `jvmMain` as one fragment, which is a
  known detekt limitation (issue #9602), not a project error: type-resolution is
  degraded for `loadPrototypeDump` and the build still passes. Do not chase it in the
  source.
- **A missing Gradle `baseline` file is not an error.** Detekt's extension baseline
  defaults to `detekt-baseline.xml`, and the typed tasks prefer
  `detekt-baseline-main.xml`/`detekt-baseline-test.xml` when they exist. With no
  baseline at all (the current state) every finding is reported, so the build fails
  on any of them; that is intended. If a baseline is ever reintroduced, generate all
  three with `detektBaseline`, `detektBaselineMainJvm` and `detektBaselineTestJvm`, or
  a typed task silently falls back to the plain one.
- **An exhaustive `when` over every entity reaches Detekt's complexity limit.** The
  ascii glyph switch over all 14 `LayoutEntity` kinds reports
  `CyclomaticComplexMethod` (15 > 14). Group first (`ChestLikeEntity`, `Plumbing`)
  and switch over the group in its own function; the dispatch stays exhaustive.
- **Phase one of `LinearProgram` is numerically sensitive.** The `d < -EPS` test on
  the most negative right-hand side is not interchangeable with `d >= 0.0`: the
  zero test pivots in tiny-negative rows, and the optimizer then reports
  "no producer for …"/empty plans while every small `LinearProgramTest` case still
  passes. Keep the `-EPS` form when touching `makeFeasible`, and check the optimizer
  suites (`OptimizerMachineTest`, the blueprint integration suites), not just the LP
  unit test.
- **Detekt's reports exclude baselined findings.** `detekt`, `detektMainJvm` and
  `detektTestJvm` write only what the baseline does not cover, so a green report is
  not an inventory of the debt. To see the current findings while cleaning up, point
  the task's `baseline` at an empty file with an init script and run it, or compare the
  files written by `detektBaseline`/`detektBaselineMainJvm`/`detektBaselineTestJvm`.
- **A declaration-level `@Suppress` may not silence a light-mode finding.**
  `MatchingDeclarationName` is reported on the file and the external-interface
  `UnusedParameter` on the interface/property; `@Suppress` on the class or property was
  ignored, while `@file:Suppress(...)` before the package declaration worked for all
  four React `XProps` files and `BlueprintDialogProps.text`. Check the baseline again
  after adding a suppression instead of assuming it took.
- **An `internal` top-level declaration collides with a `private` one of the same
  name in the package.** Graduating a file-private constant to `internal` to share it
  broke `:compileKotlinJvm` with `REDECLARATION … EPSILON` against `Optimizer.kt`'s
  `private const val EPSILON` — a file-private declaration is not exempt from the
  clash. Rename (`FEED_EPSILON`) or keep it private and pass the value. Verified
  splitting `DirectFeed.kt` into `DirectFeedModel.kt`.
- **A cold Gradle daemon can fail build-script compilation with `Unresolved
  reference 'de'`.** After the daemon was stopped, `./gradlew` reported the
  `de.bjrke.factorio.optimizer.buildlogic.*` imports in `build.gradle.kts` as
  unresolved even though `buildSrc/build/libs/buildSrc.jar` contained the classes.
  It is a stale script classpath, not a code error: a straight retry (or clearing
  `.gradle/<version>` plus `buildSrc/build`) rebuilds `buildSrc` and succeeds — do
  not start editing `build.gradle.kts` over it.
- **Never delete `buildSrc/build/kotlin` while a Kotlin daemon from an earlier run
  is still alive.** A Kotlin compile daemon outlives the Gradle daemon that started
  it (seen: one from 20:02 still writing at 21:13, hours after `--stop`). Removing
  the cache directory underneath it makes the *next* builds fail with
  `Could not close incremental caches … NoSuchFileException` or `Can't write logical
  size … caches-jvm/inputs` / `Failed to clean up output files`, i.e. directories
  vanishing mid-write. Cure, in order: `./gradlew --stop`,
  `pkill -f '[K]otlinCompileDaemon'`, `rm -rf buildSrc/build`, then run again (the
  first run after that may still hit the stale-classpath entry above). Verified by
  the 21:1x buildSrc failures on `bjrke/direct-insertion-groups` when a source-dir
  `touch` produced no rebuild, ruling a host `--continuous` build out.
- **`LongParameterList` ignores data-class constructors.** Detekt's default
  `ignoreDataClasses: true` means a data class may carry many fields without a
  finding. Use that for a genuine value object (`FactorySpec`, `Selection`,
  `RowBox`/`RowLanes`) where the fields belong together; do not convert an arbitrary
  argument tuple into a data class just to silence the rule.
- **A file with one top-level declaration must be named after it.**
  `MatchingDeclarationName` flagged `LayoutTunnels.kt` once `Tunnels` was its only
  type; renaming the file (not suppressing the rule) is the fix. Files with several
  top-level declarations are exempt.
- **Compaction does not summarize the newest reply.** OpenCode's checkpoint
  summary covers the transcript *before* the retained recent tail; the tail is
  kept verbatim. At 2026-10-19 18:57 the manual compaction of session
  `ses_eab214006ffexOwJWcjfeWwQX3` produced a summary that still said "No code
  changes made" with the pre-implementation `Next Move`, while its `recent` field
  began at the `ok` message (seq 194) and ended with the just-written handoff
  reply. A `/handoff` reply is therefore *in the tail, not in the summary*, so
  running `/handoff` and `/compact` alone cannot update it. The repo plugin
  (`.opencode/plugins/compaction.js`) now lifts the newest assistant reply's
  `## Handoff state` block into `event.result.summary`; run `/handoff` immediately
  before `/compact` and put the durable state under that exact heading.
- **V2 ignores `compaction.tail_turns`.** `opencode.json` still had the V1 key;
  the server logged `path=$.compaction.tail_turns kind=unsupported action="omitted
  unsupported legacy setting"` and used the default retention instead. Configure
  `compaction.keep.tokens` (default 15000), not `tail_turns`; `opencode.json` is
  gitignored, so this is a local fix and needs `opencode service restart`.
- **detekt's `ForbiddenImport` is a flat, global list — it cannot express per-package
  edges.** Verified on 2.0.0-alpha.6: `allowedImports` is one global list, and
  `forbiddenImports` is merged from every entry, so `pkg.**` forbids `pkg` for
  *every* file, not only for the section it was written under. A package can
  therefore be listed only when no other package imports it. `pkg.**` matches the
  members of `pkg` and of every nested package (so `blueprint.**` covers
  `blueprint.inspection.**`); a bare `pkg` matches nothing; `pkg.*` is a regex
  whose dots are wildcards. Do not read a section comment as scoping — it is
  documentation only. Recognise a wrong setup by an allowed edge being reported
  (e.g. `band -> model` after `data` forbids `model`). The edges here stay
  compiler- and review-enforced; see `decisions.md` (2026-10-22).
- **A `sed`/`python` import rewrite is only a hint; the compiler is the arbiter.**
  Moving a file and rewriting imports textually re-pointed `adjacent` (a
  `model` extension) at a same-named private helper in `band`, and mapped
  `ChestLikeEntity`/`Plumbing` to the wrong package. Run `./gradlew
  compileKotlinJvm compileTestKotlinJvm` after every move and fix only what it
  names; a bulk `git checkout -- src` also reverts the *staged* moves' content, so
  the package line of every moved file must be checked again afterwards.
- **`commonTest` cannot walk the source tree.** A test that reads files runs only
  on JVM: put it in `jvmTest` and locate the root by walking up from
  `System.getProperty("user.dir")` (same as `IconCoverageTest`).
- **Konture on this repo: source capture is exact, dependency labels are not.**
  Evaluated on 2026-10-21 with `io.github.baole.konture` 1.0.2 in a throwaway
  `:konture-test` module. What works: `generateArchitectureLayout` reads KMP source
  sets correctly (96 files = 95 `src/{common,js,jvm}Main` Kotlin files + the generated
  `BuildInfo.kt`), `common.model` passes `onlyDependOnPackages` of its own package, and
  `common.business` fails with 60-63 files that really do reach `common.model`. What
  does not: the reported dependency labels are unreliable. A reference to a
  `private class CorridorShift` and even `Collection<FactoryProduction>` in
  `common.business` are labelled `common.model.CorridorShift` /
  `common.model.Collection`; same-package references such as `common.business.LayoutBoard`
  are still reported as unauthorized even when that package is in the allowance, so 6
  of 60 blocks stay false; and the allowance matcher treats `kotlin..` as one literal
  package, not a pattern. A rule that also needs the names it may use (a surface list)
  cannot be expressed. Recognition: violations name a symbol under a package that
  does not declare it.
- **A moved symbol needs an explicit import at every use site.** Kotlin resolves names
  from the file's own package only: after a file moves, each file that used one of its
  symbols must `import` it by name, even though it compiled before. `expect`/`actual`
  pairs must move together (same package, all source sets).
- **Do not infer a symbol's package from its name with a text scan.** The cut was first
  attempted that way and produced wrong edges: parameters and members look like
  declarations, KDoc prose matched the declaration regexes, and names repeat across
  packages (`recipe`, `plan`, `materials`, `EAST`). Resolve edges from the compiler's
  unresolved-reference list instead; that list is exact and small.
- **`spotlessApply` after staging leaves the reformat unstaged.** It reorders the
  project imports, so run it before `git add`, or amend after it.
- **Host-share phantoms recur and block every build.** On the virtiofs checkout an
  interrupted Gradle run leaves entries that `stat` rejects while `ls` lists them;
  renaming their *immediate* parent fails too, so detach at the shallowest ancestor
  that accepts the rename. `tmp/fix-phantom.py` does this (defaults to
  `buildSrc/build`, remove the tree afterwards). Recognise it by
  `Failed to clean up output files` or
  `Cannot access output property 'destinationDirectory'`.
- **A settings-level `plugins { id("io.github.baole.konture") }` breaks the root
  script.** Applying the Konture plugin in `settings.gradle.kts` stops the root
  `build.gradle.kts` from seeing `buildSrc` (`Unresolved reference 'de'` for every
  `de.bjrke.factorio.optimizer.buildlogic` import). Applying it in the root
  `build.gradle.kts` `plugins {}` block works and is enough for `generateArchitectureLayout`.
- **`buildSrc/build` can hold undeletable entries on the host share.** The project
  lives on a virtiofs mount; after an interrupted build,
  `buildSrc/build/classes/kotlin/main/de/bjrke` and
  `.../META-INF/buildSrc.kotlin_module` became phantom entries: `stat` reports
  "No such file", while `ls` still lists them as `d?????????`. They make every later
  build fail with
  `Failed to clean up output files for task ':buildSrc:compileKotlin'`, and
  `rm -rf buildSrc/build` cannot remove them. Fix: `mv` the phantom *parents*
  (`de` to `de_`, `META-INF` to `META-INF_`), then `rm -rf buildSrc/build`.
- **A stale Gradle daemon can fake a build failure.** After the Konture experiment the
  untouched tree still failed with `Script compilation errors ... Unresolved reference
  'de'`, while `./gradlew :buildSrc:compileKotlin --rerun-tasks` compiled cleanly. A
  `--rerun-tasks` on the buildSrc task cleared it. Before debugging a script error,
  rerun the underlying task; do not edit the build script.
- **detekt's `ForbiddenImport` cannot see wildcard imports; `DependencyRules` is gone.**
  Verified on detekt 2.0.0-alpha.6: `style>ForbiddenImport>forbiddenImports` accepts
  `pkg.**` patterns and is enforced (`e:` plus non-zero exit), but an
  `import de.bjrke.factorio.optimizer.common.business.*` produced **no** finding while
  explicit imports of the same package did. This codebase imports whole packages on
  purpose (`WildcardImport.excludeImports`), so an import-based detekt rule would see
  almost none of the `business -> model` edges. `DependencyRules` no longer ships in
  any 2.0.0-alpha.6 rules jar (they are `dev.detekt.rules.*`); a custom rule that
  needs real resolution must implement `dev.detekt.api.RequiresAnalysisApi`, which
  `CastNullableToNonNullableType` does.
