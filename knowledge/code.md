# Code map

Where the code lives and how the packages depend on each other. Load this file
when you need the module map, the package edges, or are moving a file.

## Project

Factorio-Optimizer is a Kotlin Multiplatform app (Gradle, Kotlin) that computes
layout-optimizing Factorio blueprints. It compiles to a React web app (JS) and a
CLI (JVM) from one shared core.

## Source layout

- `src/commonMain` — shared logic, one package per domain. The dependency direction
  is one-way, from the vocabulary up to the artifact. The exact edges each package
  may use are listed under "Package dependencies":
  - `core/model` — the grid vocabulary and nothing else: `Tile.kt` (`Coordinate`,
    `Area`, `Dimension`, `Direction`, `Io`), `TileGeometry.kt` (distance and
    compass helpers), `Material.kt`, `IconLayer.kt`. A leaf: it imports no other
    project package.
  - `core/util` — `MinHeap`, `connectedComponents`, `ArrayDeque.drain`, and
    `MathUtils` (`countVectors`, `greatestCommonDivisor`).
  - `game/prototype` — the Factorio rulebook: `Recipe.kt` (`ParsedRecipe`,
    `MachineStats`, `Recipe`, `Subgroup`), `Factory.kt`, `Category.kt`,
    `Logistics.kt` (`BeltStats`/`InserterStats`/`PoleStats`, `Logistics` and
    `LogisticsConfig`), `Surface.kt` (`Surface`, `SurfaceProperty/Condition`, the
    surface rules as `Surface.accepts*` extensions; the enum values are the
    curated fallbacks the dump overrides), `SurfaceRecipes.kt`
    (`ParsedRecipe.forSurface`),
    `RecipeUsage.kt` (the cached transitive usage counts, the least-used-first
    order and the intermediate-limit constants) and `RecipePolicy.kt` (the
    optimizer's explicit recipe exclusions, `unplannedRecipes`, and the one
    burning fuel per surface, `burnerFuel`).
  - `game/selection` — the URL/CLI codecs: `Selection.kt`
    (`Selection`, `SelectionFlag`, `selectionString`, `spaceAgeIn`,
    `fullBeltIn`), `BeltEndSelection.kt` (`beltEndFromSelection/-Identifier`),
    `SurfaceSelection.kt` (`surfaceFromUrl/-Planet`) and `ExcludedSelection.kt`
    (`excludedMaterialsFromUrl/-ToUrl`, the material exclusions). Only the entry
    points (UI, CLI) and the tests use it; the layout never sees a URL.
  - `game/production` — reads the prototype dump: `recipeParser.kt` orchestrates
    it, `RecipeLoader` (the cached per-mods parse and the cached
    `forSurface(spaceAge, surface)` view), `prototypeDump.kt` (the
    `loadPrototypeDump` `expect` declaration), `RecipePrototype.kt`,
    `JsonFields.kt`, `resourceRecipes.kt`, `customRecipes.kt`
    (`ALL_SCIENCE_PACKS` from `lab.inputs`, plus the base game's rocket-silo
    launch), `spoilageRecipes.kt`, `utils.kt` (normalization) and
    `prototypeStats.kt` (`machineStats`/`logisticsStats`: machine energy use,
    belt speed and underground reach, inserter and pole stats); `recipeParser.kt`
    also reads the `fuelValues` of burnable items and the per-surface
    `surfaceProperties`.
  - `optimize` — the optimizer: `Optimizer.kt` and `MachineCatalog.kt`
    (machine choice and rates) and `LinearProgram.kt`. It takes a `ParsedRecipe`
    in and returns an `OptimizationResult` (from `model`); it never loads the
    dump or builds a blueprint. `MachineCatalog.fuelled` adds the surface's
    burning fuel to a burner furnace's recipe, so the fuel is an ordinary solid
    the plan pays for and the blueprint feeds. Its output models now live in
    `model`, so the blueprint planner can consume an optimizer result without
    depending on the optimizer itself. `Optimizer` takes a `maxIntermediates`
    limit: it keeps the least-used produced materials (from `ParsedRecipe.usage`)
    and turns the rest into imports before solving the LP, so the result cache is
    per limit. It also takes `excludedMaterials`: every recipe producing one is
    dropped before reachability and the LP, so the material is imported and the
    plan neither calculates nor lays out its production. The target is never
    excluded.
  - `model` — the shared result models: `OptimizationResult`, `FactoryProduction`
    (per-machine production) and `factoryProductionComparator`. They sit above
    the rulebook (`game/prototype`) and are consumed by both the optimizer and
    the blueprint planner, so `optimize` and the `planning` packages need not
    couple to each other — both reach the *models* instead. A leaf of its own:
    it imports only `core/model` and `game/prototype`.
  - `blueprint` — everything that picks, places and prints a blueprint, split
    into the `layout`, `planning`, `placing` and `printing` stages below. The
    optimizer (`optimize`) stays out of it.
    - `blueprint/layout` — the grid kernel: `LayoutBoard` (tiles, reservations,
      primitives), `LayoutPlacement` (single-tile entity-kind helpers),
      `LayoutTunnels` (the surface/end/tunnel placement and the tunnel-link
      plan), `LayoutUnderground`, `Tunnels`, `LayoutIndex` (the read-only tile
      index), `BeltEnd` (how a belt is terminated), `PowerPole` (which pole a
      selection uses; its supply radius and wire reach come from the dump's
      `electric-pole` prototype through `Logistics.poles`). `entity` holds the entity
      hierarchy (`LayoutEntity`, `LayoutTransportEntities`,
      `LayoutEntityStations`, `LayoutSettings`); `validation` holds the invariant
      checks (`LayoutValidation*.kt`). This tree must never mention the
      optimizer.
    - `blueprint/planning` — planning the blueprint. It holds the plan reduction
      (`blueprintPlan`, `plannedSources`, `BlueprintSelection`) at the stage root
      and clusters the planner by
      concern: `plan` — the shared vocabulary (`BlueprintPlan` and its result,
      the `Planned*` types, the machine footprint and fluid ports, the budget
      (`MAX_COPIES`), `ImportExtension` (the import
      state the marker, trim, compaction and power stages share) and
      `LogisticsCalculator` (the layout's belt/inserter rates)); `row` — the row
      planner (`RowPlanner`, `RowTopology`, `RowPlacer`, `RowBeltPlacement`, `Row`
      and the inserter/interface placers) with the shared `LayoutStage`; `band` — the
      direct-feed band (`DirectFeed`, `DirectFeedModel`, `BandSearch`,
      `FeedCandidates`, `FeedLink`, `RecipeFilter`). `RecipeFilter` plans smelting;
      the optimizer's intermediate limit decides which smelting products are built.
    - `blueprint/placing` — placing the entities and their connections:`route`
      (the weighted A* shared by belts and pipes — `LayoutRouter`,
      `RouteSearch`, `RouteProfile`), `routing` (implicit delivery — `Delivery*`,
      `Import*`, `FluidFeedLinker`, `SplitDeliveryRouter` and `BeltRouteRules`),
      `plumbing` (the fluid feed — `FluidFeeder`, `FluidLaneFeed`), `beltend`
      (`BeltEndConnector.kt` — the solid belt's end marker: label, infinity
      chest, chest), `special` (the path, power and material-search passes) and
      `compaction` (`LayoutCompaction`, `CompactionPass`, `SeamGrid`,
      `UndergroundStretcher`).
    - `blueprint/printing` — emitting the artifact: `model` (the wire DTO:
      `Blueprint`, `BlueprintEntity`, `BlueprintWire` — imports only
      `core/model` and `blueprint/layout/entity`, so the encoders and the
      assembly can share it without a cycle), `json` (`Blueprint.toJson`,
      `BlueprintEntityJson.kt` — the wire JSON, a leaf nothing else may import),
      `output` (`BlueprintString.kt`, `FactorioUrl.kt`, `LayoutSerialization.kt`,
      `ChestWires.kt`) and `inspection` (the ASCII layout and dump —
      `LayoutAscii*.kt`, `LayoutDump.kt`). The ordered `build()` pipeline lives in
      `RowLayout.kt` at the `blueprint` root, not in this stage (see
      `layout.md`).
  - plus the dumped `prototypes/` resources.
- `src/jsMain` — React web UI (kotlin-wrappers: `react`, the TanStack router
  `tanstack.react.router`, `emotion-react`); entry `js/index.kt`;
  `common/game/production/loadRecipe.kt` pulls the dumps via `@JsModule` and the
  webpack config in `webpack.config.d/`.
- `src/jvmMain` — CLI entry `jvm/CliOptimizer.kt`, argument parsing
  `jvm/CliArgsParser.kt`; `jvm/common/game/production/loadRecipe.kt`
  loads recipes from a classpath resource. An explicit `--name=value` accepts only
  an exact prototype identifier or exactly one code character (Nauvis is `n` for
  the CLI), so a typo fails instead of matching a character inside it; only the
  URL `selection` segments keep scanning characters.
- `src/commonTest` — shared kotlin-test suites, run by both `jvmTest` and
  `jsBrowserTest`; must stay platform-neutral. Every suite sits in the package it
  tests (`commonTest/…/common/blueprint/printing/…`, `.../game/production/…`), and shared
  helpers live in a `fixtures` package (`common/fixtures`: `testLogistics`,
  `calculator`, the material and named-recipe fixtures (`RecipeFixtures.kt`), the
  optimizer fixtures and the test-only `BlueprintPlan.recipes` view;
  `blueprint/printing/fixtures`: plans, layout assertions and the minimal fluid-row
  helpers).
- `src/jvmTest` / `src/jsTest` — platform-specific tests; JVM suites that need
  the build output or the source tree (`IconCoverageTest`) and the CLI
  integration tests live there.

`commonMain` compiles to both targets, so platform differences belong in the
target-specific `loadRecipe.kt`/`index.kt` files (the `loadRecipe.kt` ones are
`expect`/`actual` pairs with the common declaration, both in
`common.production`).

Package sizes (files, verified 2026-11-13). **Files** are the budget: past 10 is a
signal to split, 15 the ceiling. Nothing automates it — the file-count test was
removed, so review keeps it. There is no line
budget; `LargeClass` caps a class at 500 lines. Recheck a count with
`find src/commonMain/…/<package> -maxdepth 1 -name '*.kt' | wc -l`.

| package | files |
| --- | --- |
| core.model | 4 |
| core.util | 4 |
| game.prototype | 8 |
| game.production | 10 |
| game.selection | 4 |
| model | 1 |
| optimize | 3 |
| blueprint | 1 |
| blueprint.layout | 10 |
| blueprint.layout.entity | 5 |
| blueprint.layout.validation | 5 |
| blueprint.planning | 2 |
| blueprint.planning.plan | 6 |
| blueprint.planning.row | 11 |
| blueprint.planning.band | 6 |
| blueprint.placing.route | 3 |
| blueprint.placing.routing | 7 |
| blueprint.placing.plumbing | 2 |
| blueprint.placing.special | 4 |
| blueprint.placing.compaction | 4 |
| blueprint.printing.model | 1 |
| blueprint.printing.json | 2 |
| blueprint.printing.inspection | 3 |
| blueprint.printing.output | 4 |

`game.production` is at 10 files — the warn line itself, so split it before adding a
file; `blueprint.layout` (10) and `blueprint.planning.row` (11) are next.

## Package dependencies

Each package imports the others by name (no project wildcards; the
`WildcardImport` rule in `config/detekt/detekt.yml` allows only `de.bjrke.test.*`
from this repository), and a symbol no other package needs is `internal`: the
compiler then rejects a cross-package use. That is the whole enforcement: **no test
checks these edges** — a guard that needs to prove itself by injecting the
violation belongs at the build level, not in the app suites. The table below is
the intent; keep a change to it deliberate and reviewable, never widen a row to
make an existing edge legal.

The arrows point one way from the vocabulary up to the artifact (leaf
dependencies at the bottom, the assembly root at the top):

```
core.model < game.prototype < game.production
core.model, core.util, game.prototype < model < optimize < blueprint
blueprint.layout < blueprint.placing.route, blueprint.planning.plan
blueprint.layout, blueprint.planning.plan < blueprint.planning.row,
  blueprint.placing.compaction
blueprint.planning.row < blueprint.placing.plumbing, blueprint.placing.routing
blueprint.layout.entity < blueprint.printing.model <
  blueprint.printing.{json, output, inspection}
(pulling all of the above together) < blueprint
```

`blueprint.printing.model` is the DTO leaf the encoders share; `blueprint` is the
assembly root and imports the placing stages, `blueprint.planning.plan` and
`blueprint.planning.row`, and `blueprint.printing.{model,output,inspection}`. Only
the entry points (UI, CLI) and the tests import `blueprint` itself, for `RowLayout`
(which lives at the `blueprint` root). The optimizer's *results* are reached
through the shared `model` package — planning and `optimize` never import each
other.

| package | may use | may not reach |
| --- | --- | --- |
| `core.model` | nothing | every project package |
| `core.util` | nothing | every project package |
| `game.prototype` | `core.model` | `game.production`, `model`, `optimize`, `blueprint` |
| `game.production` | `core.model`, `game.prototype` | `model`, `optimize`, `blueprint` |
| `game.selection` | `blueprint.layout`, `core.model`, `game.prototype` | `optimize`, `model`, the dump loader |
| `model` | `core.model`, `game.prototype` | everything above (the shared result models) |
| `optimize` | `core.model`, `game.prototype`, `model` | `game.production` (the dump loader), `game.selection`, `blueprint` |
| `blueprint` | `blueprint.layout(+entity,validation)`, the placing stages, `blueprint.planning.{plan,row}`, `blueprint.printing.{model,output,inspection}`, `core.model`, `game.prototype` | `blueprint.json`, `optimize` (the optimizer stays out) |
| `blueprint.layout` | `blueprint.layout.entity`, `core.model`, `game.prototype` | `optimize`, `blueprint`, the stages above |
| `blueprint.layout.entity` | `core.model`, `game.prototype` | everything else |
| `blueprint.layout.validation` | `blueprint.layout`, `blueprint.layout.entity`, `core.model` | `blueprint` and above |
| `blueprint.planning` | `blueprint.planning.{band,plan}`, `core.model`, `game.production`, `game.prototype`, `model` | `layout` (it builds a plan, not a grid), the placing/printing stages |
| `blueprint.planning.plan` | `core.model`, `game.prototype`, `model` | `layout`, `blueprint` |
| `blueprint.planning.row` | `blueprint.layout`, `blueprint.layout.entity`, `blueprint.placing.route`, `blueprint.planning`, `blueprint.planning.plan`, `core.model`, `game.prototype`, `model` | `routing`, `blueprint` |
| `blueprint.planning.band` | `blueprint.planning.plan`, `core.model`, `core.util`, `game.prototype`, `model` | the layout |
| `blueprint.placing.route` | `blueprint.layout`, `core.model`, `core.util` | `routing`, `blueprint` |
| `blueprint.placing.routing` | `blueprint.layout`, `blueprint.layout.entity`, `blueprint.layout.validation`, `blueprint.placing.route`, `blueprint.placing.special`, `blueprint.planning.{plan,row}`, `core.model` | `compaction`, `blueprint` |
| `blueprint.placing.plumbing` | `blueprint.layout`, `blueprint.layout.entity`, `blueprint.placing.route`, `blueprint.placing.special`, `blueprint.planning.{plan,row}`, `core.model` | `routing`, `blueprint` |
| `blueprint.placing.special` | `blueprint.layout`, `blueprint.layout.entity`, `core.model`, `core.util` | `routing`, `blueprint` |
| `blueprint.placing.compaction` | `blueprint.layout`, `blueprint.layout.entity`, `blueprint.layout.validation`, `blueprint.planning.plan`, `core.model` | `routing`, `blueprint` |
| `blueprint.printing.model` | `blueprint.layout.entity`, `core.model` | everything above (the DTO leaf) |
| `blueprint.printing.json` | `blueprint.layout.entity`, `blueprint.printing.model`, `core.model` | — and **nothing may import it**: it is a leaf writer |
| `blueprint.printing.inspection` | `blueprint.layout.entity`, `core.model` | `blueprint` and above (it only reads a finished layout) |
| `blueprint.printing.output` | `blueprint.layout.entity`, `blueprint.printing.model`, `core.model`, `game.production` | `blueprint`, `blueprint.json` |

A nested package is its own unit: it may use its parent, and the parent may not use
the child unless its row says so (`layout` cannot see `layout.entity` merely
because the names are related — `layout.entity` is listed explicitly). A package
that both is a directory and a symbol prefix (`prototype` and
`prototype.selection`) is resolved from its full dotted name, so an edge to
`…prototype.selection.X` belongs to `prototype.selection`, not to `prototype`.

When the compiler rejects a symbol, the edge is missing from the table: adding a
package or a dependency means adding a row deliberately. §Traps below has the
recipe for moving a file (the compiler's error list is the only reliable source of
edges).

## Traps

- **detekt's `ForbiddenImport` is a flat, global list — it cannot express
  per-package edges.** On 2.0.0-alpha.6 `allowedImports` is one global list, and
  `forbiddenImports` is merged from every entry, so `pkg.**` forbids `pkg` for
  *every* file, not only for the section it was written under. A package can
  therefore be listed only when no other package imports it. `pkg.**` matches the
  members of `pkg` and of every nested package (so `blueprint.**` covers
  `blueprint.inspection.**`); a bare `pkg` matches nothing; `pkg.*` is a regex
  whose dots are wildcards. `DependencyRules` no longer ships in any 2.0.0-alpha.6
  rules jar; a custom rule needing real resolution must implement
  `dev.detekt.api.RequiresAnalysisApi`. `ForbiddenImport` stays empty on purpose;
  the edges are the table above.
- **Package edges cannot be derived from Kotlin imports or a text scan.** Before
  `4f6c3df` most files imported `common.model.*`, so an import-based checker saw
  almost no real edges; even with explicit imports, an import proves a type
  reference, not which package is allowed to reach which. Text scanning is also
  fragile: the tree has multi-line constructor headers, one-line and multi-line
  KDoc, KDoc references (`[Factory]`), and the same name declared in several
  packages (`material`, `recipe`, `name`). A hand-written scanner hit all of these.
  If an edge checker is ever automated, read the compiled constant pool rather than
  the sources, or resolve from the compiler's unresolved-reference list.
- **An architecture guard must read the build output, not the source tree.** The
  removed `PackageEdgesTest` first walked the source dirs and parsed `*.class` names
  inside the `.kt` files, so it found zero class files and passed vacuously; the
  injected `service -> blueprint` probe only failed after pointing it at
  `build/classes/kotlin/jvm/main/...`. If a Gradle-level check is added, prove it by
  injecting the violation it should reject.
- **Classycle, Konture and a bespoke scanner were all evaluated and rejected.**
  Classycle analyzes Java class files, its Gradle plugins are unmaintained
  (2015–2018), and it expects Java naming, so Kotlin edges get silently dropped.
  Konture 1.0.2 (`io.github.baole.konture`) reads the KMP sources exactly but
  mislabels local, private and library types (a reference to `private class
  CorridorShift` and even `Collection<FactoryProduction>` was labeled
  `common.model.CorridorShift` / `common.model.Collection`) and cannot express
  allowed symbols or standard-library packages; applying it in
  `settings.gradle.kts` also breaks the root script (`Unresolved reference 'de'`),
  while the root `build.gradle.kts` `plugins {}` block works. Recognize a wrong
  report by a violation naming a symbol under a package that does not declare it.
- **A `sed`/`python` import rewrite is only a hint; the compiler is the arbiter.**
  Moving a file and rewriting imports textually re-pointed `adjacent` (a `model`
  extension) at a same-named private helper in `band`, and mapped
  `ChestLikeEntity`/`Plumbing` to the wrong package. Run
  `./gradlew compileKotlinJvm compileTestKotlinJvm` after every move and fix only
  what it names; a bulk `git checkout -- src` also reverts the *staged* moves'
  content, so the package line of every moved file must be checked again
  afterwards.
- **Do not rebuild an import block from a name-based usage scan.** A helper that
  inserted `import <newPackage>.<Symbol>` for every symbol found in the body
  silently corrupted ~80 files: it truncated `…common.prototype.Recipe` to
  `…common.prototype` (a package, so `Packages cannot be imported`), inserted into
  a block it then re-sorted without `import `, and added imports of *members* and
  *parameter names* (`production`, `testRecipe`) that are only importable as
  declarations. Recover with `git show HEAD:<path>` plus matching each file's
  original import symbols against its body — never a name-to-package guess. The
  reliable recipe is: `git mv`, `sed` the `package` line, then add the import the
  *compiler* asks for, one symbol at a time.
- **A moved symbol needs an explicit import at every use site.** Kotlin resolves
  names from the file's own package only: after a file moves, each file that used
  one of its symbols must `import` it by name, even though it compiled before.
  `expect`/`actual` pairs must move together (same package, all source sets).
- **A moved `expect`/`actual` pair needs its source-set directories created too.**
  Moving `common/game/production/loadRecipe.kt` also means
  `jsMain`/`jvmMain` `common/game/production/loadRecipe.kt`; the JS one is also the
  `@JsModule` target the webpack config resolves, so renaming the file without
  updating the `@JsModule` path breaks only the browser bundle, not
  `compileKotlinJvm`.
- **A package that is both a directory and a symbol prefix needs care in
  scanners.** `common.prototype` and `common.prototype.selection` are separate
  packages, so a resolver that stops at the longest known *prefix* attributes
  `…prototype.selection.X` to `prototype`. Resolve from the full dotted path (or
  the declared package), never from a prefix match — the package dependency table
  above treats the nested package as its own unit.
