# Testing

Where tests live, which fixtures to use and how the suites are structured. Load
this file before writing or changing tests; the test conventions are binding
whenever they apply.

## Test suites and fixtures

- Group test suites by scenario and keep shared fixtures in their own file. Split
  an over-long suite by scenario; when the resulting suites use **disjoint**
  fixtures, leave each suite's `private` helpers and values in place rather than
  forcing a shared file (`OptimizerTest` → `OptimizerTest` + `OptimizerSurfaceTest`).
  When they share helpers, export them instead: top-level `internal` values and
  functions in the fixtures package of the package they belong to
  (`common/fixtures/LogisticsFixtures.kt` holds `testLogistics`, `BeltCase` and
  `calculator`; `common/fixtures/MaterialFixtures.kt` holds the shared materials;
  `jvmTest/…/blueprint/printing/fixtures/` holds the recipes, plans and layout assertions),
  or an abstract base
  class for helpers that capture per-test callbacks
  (`OptimizationResultTestBase`). A base class of a public test class must itself be
  public, or Kotlin reports "'public' subclass exposes its 'internal' supertype".
  Prove a split moved no test by diffing the sorted `fun <name>()` list before and
  after (`grep -oP 'fun \K[a-zA-Z]+(?=\()' | sort`); keep the suite's original name
  on one part.
  A fixtures file in the **same package** must live in a subpackage (e.g.
  `jvmTest/…/common/blueprint/printing/fixtures/`): an `internal` top-level fixture with the same
  name as another suite's `private` top-level value is a Kotlin `REDECLARATION`, and
  Kotlin does not allow star-importing an `object`. Import each fixture by name; the
  project's packages no longer use wildcards.
- Prefer data that can be tested alone: `LayoutCompaction` already takes a
  `List<LayoutEntity>` and `LayoutRouter` only reads the board, so neither needs a
  full `BlueprintPlan` to test.
- Test-only code lives in the test source set, not in `commonMain`. A stage's test
  accessors go in a `fixtures/` file built on a production entry point: the layout
  suites read entities and failures back through `RowLayout.layoutDump()` (the
  CLI's `--layout` also uses it) or through the `Layout` fixture that wraps both
  read-backs. No production type widens its visibility beyond `RowLayout`'s
  `internal` or keeps a wrapper only tests call. Do not reintroduce top-level
  `fromPlaced`/`blueprintLayoutDump` wrappers, and do not rebuild a layout after it
  has built (`RowLayout`'s `build()` is idempotent); a new layout fact is a property
  on the `Layout` fixture, not a second dump function.

## Testing

- A behavior change needs tests in the matching source set
  (`commonMain`/`jsMain`/`jvmMain` → `commonTest`/`jsTest`/`jvmTest`);
  `AGENTS.md` states the rule. Use `kotlin.test`.
- Every test that builds the blueprint `Layout` fixture (`Layout(...)` or
  `layoutFromUrl(...)`) asserts `assertEquals(emptyList(), layout.failures)` in
  that test. A layout can still satisfy a geometry assertion while leaving a feed
  unrouted; the failures list is the cheap check. Only a test that pins failure
  reporting (e.g. `aRowThatCannotFitReportsTheMissingMaterials`) asserts failures
  are non-empty.
- Use the shared material and recipe fixtures before defining a local recipe:
  `common/fixtures/MaterialFixtures.kt` and `common/fixtures/RecipeFixtures.kt`
  hold real game recipes (`ironPlateSmeltingRecipe`, `copperCableRecipe`,
  `electricEngineUnitRecipe`, …) with the `data.raw` amounts and times. A synthetic
  recipe is fine for a contrived rate, a cycle no vanilla recipe has, or a minimal
  fluid-row geometry; say why in a comment. `blueprint/printing/fixtures/FluidRowFixtures.kt`
  keeps `water-boil` as that geometry double: a real fluid-to-solid recipe adds
  inputs and rates that move the exact lane tiles the characterization suites pin.
- Floating-point `assertEquals` uses the shared `TOLERANCE` (`1e-9`) from
  `de.bjrke.test`, not a per-assert literal. Keep a coarser local delta only when
  the expectation is deliberately rounded or measured: display/UI pixels, a value
  already rounded to three decimals, or the URL round-trip's 12-digit precision.
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
  - existence → `assertNotNull(collection.firstOrNull { predicate }, message)`.
  - `none` → `assertEquals(emptyList(), collection.filter { predicate }, message)`.
  - `all` → `assertEquals(emptyList(), collection.filterNot { predicate }, message)`.
    The equality form prints the elements that broke the rule, while `assertTrue`
    printed only `false`. `assertNotNull` prints no collection, so keep the
    collection in the message where it identifies the case (`inserters.toString()`,
    `names.toString()`).
- `assertTrue`/`assertFalse` are only for genuine predicates (comparisons, flags,
  scalar domain rules such as `components <= 1`); a search over a collection
  (`any`/`none`/`all`) is not one. Use `assertIs`, `assertNotNull`, `assertNull`,
  `assertEquals` when they express the check.
- `assertAll` is only for independent assertions about one subject. Repeated
  assertions over cases MUST use `assertTable`/`assertTableOf` with the case as the
  element (`String`, `Pair`, `Triple`, local data class). `assertTable` labels each
  failure with `case: <value>`, so no manual counting is needed; do not collect case
  lambdas in `assertAll`.
- Do not append the actual value's `.toString()` to a message; equality assertions
  already print it. Keep only messages that add context.
- **Build each real layout once and assert every invariant on it.** A layout costs
  about 200 ms while its invariants are cheap, so `realBlueprintsAreValid` in
  `BlueprintLayoutIntegrationTest` builds one layout per target and runs the overlap,
  inserter, tunnel, splitter, merge, stretch and belt-source checks on it. Add an
  invariant to that table instead of a new table over the same targets. A memoized
  `Layout` fixture is not the answer: a shared cache is not safe once suites run in
  parallel.
- **A belt fed by several materials is only a merge when its own lanes cannot hold
  them.** `assertNoMergingBelts` takes the `LaneLoad` of every belt tile
  (`layout.beltLoads`) next to `layout.beltMaterials`: a planned mixed input belt
  joins two single-material lanes on purpose, while a merge into an occupied lane
  stays a failure. Passing materials alone flagged the mixed rows of
  `utility-science-pack`.
- `commonTest` runs on JVM **and** JS: keep it platform-neutral; target specifics
  go in `src/jvmTest` / `src/jsTest`.
- The Gradle-run rule is in `AGENTS.md` §Testing. `./gradlew check` runs
  `spotlessCheck`, Detekt, `jvmTest` and `jsBrowserTest` (headless Chrome), so
  iterate with `./gradlew jvmTest` and finish with `./gradlew spotlessApply check`
  — one invocation, one Gradle process.
- **A suite that is too heavy for the JS browser belongs in `jvmTest`.** `commonTest`
  runs on both JVM and JS, so a suite there costs a Chrome execution in
  `jsBrowserTest` on every pipeline. The heavy layout and blueprint suites
  (`BlueprintLayout*`, `BlueprintFluid*`, `BlueprintImportTest`,
  `BlueprintBeltEndTest`, `BlueprintLayoutSnapshotTest`, `blueprint/planning/band/`, the ascii, plan and
  encoder suites, and the `planning` row/lane suites `FluidLaneTest`,
  `RowPlacerTest`, `RowPlannerTest` and `blueprint/placing/routing/DeliveryPreparationTest`) therefore
  live in `src/jvmTest`. The light kernel and unit suites still run in `commonTest`
  (`LayoutCompactionTest`, `LayoutPipesTest`, `LayoutEntityTest`,
  `MaterialSearchTest`, plus the model, prototype, production, optimizer and service
  suites) and appear in `jsBrowserTest` too. Move a suite to `jvmTest` rather than
  filtering it out in CI — the Kotlin/JS runner cannot express a multi-suite exclude,
  and a filter hides the same cost from local runs too.
  `common/fixtures` stays in `commonTest` because the `jsTest`
  `OptimizationResult*` suites read it. `blueprint/printing/fixtures` moved to `jvmTest` with
  the layout suites that are its only readers; a helper needed by both source sets
  belongs in `common/fixtures`. Moved `jvmTest` suites may still import
  `common/fixtures` (`testLogistics`, `production`, the `MaterialFixtures`
  materials): the JVM test compilation sees the `commonTest` source set as a friend
  module, so both `internal` and public declarations keep compiling — verified by
  moving the suites and rerunning `compileTestKotlinJvm`.
- Build logic (`buildSrc/**`) has **no** test suite; the root `check` drives buildSrc
  only up to `jar`, so a buildSrc test would not run from `check` — wire it in
  separately (`./gradlew -p buildSrc test`) if one is ever added. Such tests are plain
  `kotlin.test` — the `de.bjrke.test` helpers are app-only. `tasks.test {
  useJUnitPlatform() }` makes `kotlin("test")` resolve to `kotlin-test-junit5`,
  which already brings `junit-jupiter-engine`; never declare the engine explicitly.
- **Never test agent tooling from the app suites.** `AGENTS.md` §Testing defines
  what the build covers; `./gradlew check` covers the app only.

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

## Traps

- **`gradlew` tests run with the project root as the working directory.** Tests
  that shell out can resolve repository files relative to
  `System.getProperty("user.dir")`.
- **`commonTest` cannot walk the source tree.** A test that reads files runs only
  on JVM: put it in `jvmTest` and locate the root by walking up from
  `System.getProperty("user.dir")` (same as `IconCoverageTest`). Prefer a Gradle
  task over such a test for anything that guards the build itself.
- **A `buildSrc` test suite never runs from the root build.** The root build drives
  buildSrc only through `:classes`/`:jar`, so `buildSrc/src/test` is dead unless
  invoked. `tasks.named("jar") { dependsOn(tasks.test) }` fails with a circular
  dependency (`test → testClasses → compileTestKotlin → jar`), and a root
  `GradleBuild` task fails with "build name 'buildSrc' … reserved name". See
  §Testing above.
- **A one-line collection assertion becomes a multi-line block and can push a
  test method over `LongMethod` (60).** Converting `assertTrue(x.any { ... })` to
  `assertNotNull(x.firstOrNull { ... })`, or `assertTrue(x.none { ... })` to
  `assertEquals(emptyList(), x.filter { ... })`, adds lines. The assertion cleanup
  grew `aSharedInputSplitsOnItsBeltNotAtTheProducer` from 59 to 65 source lines,
  and `detektTestJvm` failed with `[LongMethod]` although the method passed on
  `main`. Recognize it by a test file touched only by assertion rewrites suddenly
  failing `check`. Build repeated rows up front (`val row1 = ...; rows =
  listOf(row1, row2)`) or move plan construction into a fixture before adding the
  assertion blocks — do not suppress the rule.
