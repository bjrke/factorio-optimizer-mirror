# Pitfalls

Verified traps, non-obvious behavior and diagnostic dead ends. An entry belongs
here only with evidence, and should say how to recognise it.

- **`gradlew` tests run with the project root as the working directory.** Tests
  that shell out can resolve repository files relative to `System.getProperty("user.dir")`.
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
- **A row with more than two solid inputs breaks the import lanes, and that
  reads as "compaction does not run".** `RowShape` gives a row a near, a far and a
  below lane; `sinkOf` feeds every further input from below (`NORTH`) at
  `row.left + (index - 3)`. The copies share one y, so those sinks line up with the
  lane columns the row's own product runs through. When the row sits in a
  direct-feed band, or a producer trunk already runs westward on those columns, the
  import has to enter a tile that already holds a foreign belt. `BeltRouteRules`
  allows it at the goal (`goal ||` skips `poursIntoSide`), so the layout is
  invalid before compaction starts, and `CompactionPass` rejects every corridor
  because `validateLayout` never comes back empty. Evidence (2026-10-23): the
  reported `utility-science-pack` URL puts low-density-structure/processing-unit/
  steel-plate on `(40..42, 127)` over the westbound electronic-circuit belt of the
  flying-robot-frame row; the layout stays 93x131 in every configuration (1 or 2
  lanes, normalize 1.0 or 4.5), and `validateLayout` is empty right after
  `fluidFeeder.feed()` but not after `routeDeliveries()`. The flying-robot-frame
  recipe has four solid inputs plus two more on the target row, which in a
  single-material layout only a **mixed belt** can feed; that is out of scope and
  not a router bug. Recognise it by `foreign side-load into …` at a few adjacent
  lane tiles after routing with zero feed failures, not by a compaction log line.

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
  of these; see `decisions.md` (2026-10-21). If an edge checker is ever
  automated, read the compiled constant pool rather than the sources: the compiler
  has already resolved every name there.
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
- **detekt's `ForbiddenImport` is a flat, global list — it cannot express per-package
  edges.** Verified on 2.0.0-alpha.6: `allowedImports` is one global list, and
  `forbiddenImports` is merged from every entry, so `pkg.**` forbids `pkg` for
  *every* file, not only for the section it was written under. A package can
  therefore be listed only when no other package imports it. `pkg.**` matches the
  members of `pkg` and of every nested package (so `blueprint.**` covers
  `blueprint.inspection.**`); a bare `pkg` matches nothing; `pkg.*` is a regex
  whose dots are wildcards. Do not read a section comment as scoping — it is
  documentation only. Recognise a wrong setup by an allowed edge being reported
  (e.g. `band -> model` after `data` forbids `model`). `ForbiddenImport` stays
  empty on purpose; the intended edges are the table in `architecture.md` and are
  guarded by the compiler (`internal`) and review (`decisions.md`, 2026-10-23).
- **A `sed`/`python` import rewrite is only a hint; the compiler is the arbiter.**
  Moving a file and rewriting imports textually re-pointed `adjacent` (a
  `model` extension) at a same-named private helper in `band`, and mapped
  `ChestLikeEntity`/`Plumbing` to the wrong package. Run `./gradlew
  compileKotlinJvm compileTestKotlinJvm` after every move and fix only what it
  names; a bulk `git checkout -- src` also reverts the *staged* moves' content, so
  the package line of every moved file must be checked again afterwards.
- **Do not rebuild an import block from a name-based usage scan.** On 2026-10-22 a
  helper that inserted `import <newPackage>.<Symbol>` for every symbol found in the
  body silently corrupted ~80 files: it truncated `…common.prototype.Recipe` to
  `…common.prototype` (a package, so `Packages cannot be imported`), inserted into
  a block it then re-sorted without `import `, and added imports of *members* and
  *parameter names* (`production`, `testRecipe`) that are only importable as
  declarations. Recovering the blocks needed `git show HEAD:<path>` plus matching
  each file's original import symbols against its body — never a name-to-package
  guess. The reliable recipe is: `git mv`, `sed` the `package` line, then add the
  import the *compiler* asks for, one symbol at a time.
- **A moved `expect`/`actual` pair needs its source-set directories created too.**
  Moving `common/production/loadRecipe.kt` also means `jsMain`/`jvmMain`
  `common/production/loadRecipe.kt`; the JS one is also the `@JsModule` target the
  webpack config resolves, so renaming the file without updating the `@JsModule`
  path breaks only the browser bundle, not `compileKotlinJvm`.
- **A package that is both a directory and a symbol prefix needs care in scanners.**
  `common.prototype` and `common.prototype.selection` are separate packages, so a
  resolver that stops at the longest known *prefix* attributes `…prototype.selection.X`
  to `prototype`. Resolve from the full dotted path (or the declared package), never
  from a prefix match — the package dependency table in `architecture.md` treats the
  nested package as its own unit.
- **An architecture guard must read the build output, not the source tree.** The
  removed `PackageEdgesTest` first walked the source dirs and parsed `*.class` names
  inside the `.kt` files, so it found zero class files and passed vacuously; the
  injected `service -> blueprint` probe only failed after pointing it at
  `build/classes/kotlin/jvm/main/...`. If a Gradle-level check is added, prove it by
  injecting the violation it should reject.
- **`commonTest` cannot walk the source tree.** A test that reads files runs only
  on JVM: put it in `jvmTest` and locate the root by walking up from
  `System.getProperty("user.dir")` (same as `IconCoverageTest`). Prefer a Gradle
  task over such a test for anything that guards the build itself.
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
