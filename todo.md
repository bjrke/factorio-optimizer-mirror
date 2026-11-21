# Todo

Delete an item's whole section as part of the change that implements it; this
file lists open work only.

- https://bjrke.gitlab.io/factorio-optimizer/#/2.4/bioflux/CFSbgmr/1/1/10/- doesn't contain yumako-mash because it has
  by products

## Feature findings — prompt-ready

Each block below is self-contained: copy one as the task prompt.

### Connect an electromagnetic-plant row by mirroring alternate machines

Fix the electrolyte layout `http://localhost:8080/#/24/electrolyte/FSXmor/1/1`, where a row of
four touching electromagnetic plants is only partly connected.

`RowPlacer.placeRow` advances every machine by `blueprintMachineFootprint` width, so the plants
touch. `prototypeFluidPorts` (`BlueprintMachine.kt`) is written for the single
`blueprintMachineDirection` orientation: with `Direction.EAST` the heavy oil and holmium solution
sit on the north/south edges, and both electrolyte outputs on the west/east side edges. A side port
of an interior machine is covered by the neighbor's footprint, so `FluidFeeder.connectTargetOutput`
(`FluidFeeder.kt:154`) finds the tile neither free nor reserved, returns silently, and only the
westmost plant gets an electrolyte infinity pipe. The other three machines drain nowhere and the
layout reports no failure.

Mirror every second plant in the row so the fluid ports of neighboring machines meet and connect,
with the fluid inputs moving to the other edge as needed. The port lookup must become per-machine
orientation-aware instead of assuming one direction for the whole row, and `connectTargetOutput`
must record a failure instead of returning silently when a machine's output cannot be drained. The
fix must not be electrolyte-specific: the same machines build processing units, where sulfuric acid
is one of the fluid inputs next to the solid ones, so a processing-unit row must connect the same
way.

Add tests: the 24/min electrolyte URL with every electromagnetic plant's electrolyte output
reaching a pipe (count per machine), `failures` empty and `validateLayout` clean; a processing-unit
URL whose electromagnetic plants take sulfuric acid, proving the fix is not recipe-specific; a unit
test on the port/direction assignment where alternating machines expose their side ports in the
same seam and a machine whose drain cannot be placed reports a failure.

### Let direct-feed bands pair differently sized machines

Allow a direct-feed band to pair machines with different footprints.

`BandSearch.arrange` bails out with `footPrintsHaveDifferentSize`, and `RowPlacer.placeRow`
advances every machine by one shared `size`, so a 3×3 assembler cannot feed a 5×5 foundry
machine-to-machine and gets a belt row instead. The row geometry (machine width, band top,
machine-edge inserters, the near belt reach) must be derived per machine: advance `left` by each
machine's own width, size the row by the tallest machine, and use a long inserter for a belt that a
wider/taller machine cannot reach with a fast inserter.

The worked example is a splitter plan at 1.8/s: with the foundry enabled,
`http://localhost:8080/#/1.8/splitter/CFSXbmr/1/1/10/copper-plate,iron-plate`
puts iron gear wheels in a 3×3 assembler and transport belts/splitters in the 5×5 foundry, so the
gear-wheel → transport-belt link should be one interleaved band; without it,
`http://localhost:8080/#/1.8/splitter/CSXbmr/1/1/10/copper-plate,iron-plate`
keeps everything 3×3 and must keep behaving exactly as today. Keep the existing band bounds
(`GROUP_MAX_MACHINES`, `MAX_ROW_BELTS`, `MAX_COPIES`) and the rate model unchanged; only the
footprint assumption goes.

Add tests: a band-search unit case where a gear-wheel (3×3) link into a transport-belt (5×5)
production arranges into one band and a mixed-footprint band still fits the belt budget; the
splitter URL with the foundry where gear-wheel and transport-belt machines share one `PlannedRow`
with a link, no belt carries the gear wheel, `failures` is empty and `validateLayout` is clean; the
same URL without the foundry as an unchanged regression case; an inserter case where only a long
inserter reaches the near belt, with the lane load still correct.

### Lay out a target row whose inputs need more belts than fit

Fix the base-game `exoskeleton-equipment` layout
`http://localhost:8080/#/1.0/exoskeleton-equipment/-/1/1/10`, which plans 13 recipes and reports
`row needs 5 solid belt rows, only 4 fit: exoskeleton-equipment` followed by `exoskeleton-equipment
has no output belt on its row`, so the blueprint has no product belt for its target.

The target recipe takes processing unit, electric engine unit and steel plate, while `MAX_ROW_BELTS`
in `BlueprintPlan.kt` caps a row at four belts. Whatever the fifth belt is — a side input, a mixed
belt split back apart, or the product itself — the target's own output must never be the one that
gets dropped. Either pair inputs that share a lane, direct-feed one of them, or give the product
belt priority and report the missing input instead.

Add tests: the URL with `failures` empty, `validateLayout` clean and the target's product belt
present with `exoskeleton-equipment` on it; a row-planner unit case where a target with more inputs
than `MAX_ROW_BELTS` still reserves its product belt and pairs two inputs on one mixed belt; a case
where the inputs genuinely cannot fit, which must report the missing input instead of a target
without an output belt.

### Route the express-splitter plan at normalize 1.0

Fix the Space Age `express-splitter` layout
`http://localhost:8080/#/1.0/express-splitter/CFSXbmr/1/1/10`, which plans splitter, fast-splitter
and transport-belt rows in the foundry next to electromagnetic plants making copper cable,
electronic circuit and advanced circuit, and reports

```
copper-plate runs into electronic-circuit at Coordinate(x=37, y=99)
foreign side-load into electronic-circuit at Coordinate(x=37, y=99)
```

The copper plate delivery to the electromagnetic plants collides with the electronic-circuit row at
one tile. `DeliveryRouter`/`LayoutRouter` should route the crossing belt under the occupied tile or
reserve its approach instead of side-loading into the neighbor; a side-load that cannot be avoided
must be a failure, not a silent merge. `express-splitter` at 0.5 and 2.0 lays out cleanly, so use
the 1.0 URL as the case.

Add tests: the URL at 1.0 with `failures` empty and `validateLayout` clean, no belt carrying
copper plate that also loads onto an electronic-circuit row (assert over `Layout.beltMaterials`/
`beltLoads` next to the colliding tile); the same URL at 0.5 and 2.0 unchanged. Re-add
`express-splitter` to `nauvisMaterials` (and `exoskeleton-equipment` to `baseGameMaterials` for the
previous item) so `BlueprintBenchmarkTest` covers them again.

## Performance findings — prompt-ready

Each block below is self-contained: copy one as the task prompt.

They come from `./gradlew jvmTest -Pprofile`; the baseline recording is
`build/profiles/jvmTest/jvmTest-73260.jfr`. Over the 22 s suite it holds 1,710 CPU samples, 18.6 GB
of sampled allocation (~850 MB/s) and 102 young GCs (677 ms total, P99 17 ms). By inclusive samples
the stages split into compaction 526 (31%), routing 394 (23%), band planning 184 (11%), validation
145 (8.5%). 711 of the 1,710 samples (42%) sit inside `java.util.HashMap` internals —
`getNode` 439, `putVal` 117, `resize` 107, `put` 48 — and the hottest self-time entries are
`Material.equals` 56, `Coordinate.equals` 41, `BeltRouteRules.poursInto` 40, `RouteNode.equals` 39,
`BandCycleSearch.window` 37, `CorridorShift.accept` 32.

Re-measure every block the same way:

```sh
./gradlew jvmTest -Pprofile
S=.opencode/skills/jfr-profiles/scripts/jfr-top.sh
$S self -n 25 ; $S alloc -n 20
env -u JAVA_TOOL_OPTIONS jfr view gc-pauses build/profiles/jvmTest/*.jfr
```

### Move the seam grid and its DP onto flat arrays

The compaction stage costs 31% of the suite and most of it is hash-map traffic.
`SeamGrid` (`SeamGrid.kt:184-185`) stores `runs: HashMap<Coordinate, IntRange>` and
`cost: HashMap<Coordinate, Int>` with one entry per removable tile, and `SeamSearch`
(`SeamGrid.kt:227-229`) keeps `bottleneck`/`cost`/`previous` in three more
`HashMap<Coordinate, _>`. `Step` (`SeamGrid.kt:232`) is allocated per candidate. In the profile
that is `SeamGrid.<init>` 78 inclusive samples, `SeamSearch.extend` 111, `bestPredecessor` 101,
316 + 311 map-allocation samples, 285 MB of `IntRange`, 1.4 GB of `Coordinate` from `tileAt`
(`SeamGrid.kt:24`) and 309 MB of `SeamSearch$Step`.

`primaryRange`/`secondaryRange` already describe an exact rectangle, so replace the four maps with
arrays indexed by `(primary - primaryRange.first) * cols + (secondary - secondaryRange.first)`:
`IntArray` run starts and ends (`-1` for "not removable"), `IntArray` bottleneck (`-1` for
"unreachable"), `IntArray` cost and `IntArray` predecessor. Delete `Step` and let
`bestPredecessor` compare three ints locally. `pathTo` then walks the predecessor array and maps
indices back to coordinates, and `CompactedSeam` keeps its current shape.

The tie-break order documented on `bestPredecessor`/`isBetter` (width, then cost, then
northernmost/leftmost) must stay exactly as it is, and `tileAt`/`primaryOf`/`secondaryOf` stay for
`corridorOf`, `CorridorShift` and `nextCompaction`. Expected effect: the compaction stage's hash
lookups and its 600-plus allocation samples disappear.

Add tests only where the shape is not already covered: the existing compaction and golden layout
suites must pass unchanged; add a unit case asserting the seam's tie-break on a hand-built grid
where two predecessors share width and cost, and one where the widest run is reachable only
through the `-1` sentinel edge rows.

### Make the layout board and tile index dense grids

`LayoutBoard.grid` (`LayoutBoard.kt:73`) is `mutableMapOf<Coordinate, Cell>()` and every query
hashes a `Coordinate`: `isReserved` 65 inclusive samples, `entityAt` 31, `reservedMaterialAt` 19,
`isFree` 12. `LayoutIndex.tiles` (`LayoutIndex.kt:12`) is the same shape and is rebuilt from
scratch per compaction candidate (`LayoutCompaction.kt:67`) and per validation
(`CompactionPass.kt:56`), costing 53 inclusive samples and 88 map-allocation samples just to build
a lookup table. `HashMap.getNode` callers are `RouteSearch.relax` 85, `SeamSearch.bestPredecessor`
80, `LayoutBoard.isReserved` 51, `LayoutIndex.at` 41, `SeamSearch.extend` 40, `RouteSearch.find`
36, `LayoutBoard.entityAt` 27.

Replace both with flat storage over the bounds the board already tracks (`minX`/`maxX`/`minY`/
`maxY`, updated in `place()` at `LayoutBoard.kt:132-135` and reset in `replaceAll`): index
`(x - originX) + (y - originY) * width`. For `LayoutIndex` that is two passes — bounds, then
fill — so `at()` becomes an index computation. Prefer parallel arrays over one `Cell` object per
tile (entity index `IntArray` with `-1`, `BooleanArray` reserved, a reserved material array and an
edge fluid array). `Tunnels` (`LayoutBoard.kt:76`) needs the same treatment to stop per-tile map
churn, and `RowReservations.sections` (`LayoutBoard.kt:165`) is a `Map<Int, Section>` that boxes
148 MB of `Integer` in `isReserved` alone (`:171`) plus 49 MB in `materialAt`, for at most a few
dozen rows — give it an array too.

Keep the public queries (`entityAt`, `isFree`, `isReserved`, `reservedMaterialAt`) and add int
overloads so the hot paths stop passing `Coordinate` at all. The west bound `IMPORT_FEED` and
the ability to grow the origin in either direction must survive.

Add tests: a board unit case that places entities across negative coordinates and asserts
`entityAt`/`isReserved`/`removeAt` after `replaceAll`; the full layout suite (every golden URL and
blueprint) unchanged.

### Stop the band search comparing machines by value and rebuilding lists

`BandSearch.window` (`BandSearch.kt:147-150`) builds `List(length) { cycle[...] }` and counts with
`machines.count { it == recipe }` for every length and offset, and `FeedWindow`
(`BandSearch.kt:337`) compares `machine == link.consumer.production` in `init`, `seedSupply` and
`consume`. Each comparison descends `FactoryProduction` → `Material` → `String.equals`, which is
where the 56 `Material.equals` and 27 `String.equals` samples come from; the lists alone cost
137 MB of `Object[]` in `ArrayList.<init>` plus 121 MB in `ArrayList.grow`.

First make the six `FeedWindow` comparisons identity comparisons (`===`): the machines and the
link endpoints are the same instances. Then keep the cycle as recipe indices — `search` already
holds `cycle: IntArray` — and count with an `IntArray` histogram, materializing a
`FactoryProduction` only for the winning plan. The band bounds (`GROUP_MAX_MACHINES`,
`MAX_ROW_BELTS`, `MAX_COPIES`) and the rate model stay untouched.

Add tests: a unit case where two equal-but-distinct `FactoryProduction` values appear in
`group.needs`/`links` and the band must still pair them (guards against an identity compare being
wrong), and the existing band and golden layout suites unchanged.

### Key the route search by packed node ids

`RouteSearch.relax` is 187 inclusive samples (11%). `gScore` and `previous`
(`RouteSearch.kt:81-82`) are keyed by the three-component data class `RouteNode`
(`RouteSearch.kt:41`), so each lookup runs `Objects.hash` on a fresh `Object[]`, boxes the `Int`
score (55 MB of `Integer` from `relax` alone) and compares three fields. The node itself is
allocated per expansion, and `MinHeap` orders the frontier through `naturalOrder()`'s comparator
lambda (`MinHeap.kt:87`), 38 self samples.

Encode a node as one `Int`/`Long` instead: `x` is bounded by `ROUTE_WEST_BOUND..eastBound`, `y` by
`northBound..southBound`, the heading is one of four ordinals and `straight` is a small int, so
the region size is known once per search. Use a small int-keyed open-addressing map owned by the
route package (commonMain has no primitive map) for `gScore`, and store the predecessor as a
packed key too; keep `RouteNode` only for `reconstructRoute`.

`RouteSearch.kt:131` also computes `surface.neighbors.any(board::isReserved)`, which allocates an
`ArrayList` plus four `Coordinate`s per expansion (`TileGeometry.kt:53`); replace it with the four
ordered checks against the int-based board query from the dense-grid item. The documented neighbor
order (north, east, south, west) and the `MAX_ROUTE_EXPANSIONS` cap stay as they are. Let `MinHeap`
compare through the element's own `compareTo` when no comparator is passed (`MinHeap.kt:87`), so
the frontier no longer hops through `naturalOrder()`'s lambda.

Add tests: a routing unit case where two nodes differ only in `straight` and must not share a
`gScore` entry, one where they differ only in heading, and the existing routing and golden layout
suites unchanged.

### Stop building a `Set` on every `LaneLoad.materials` read

`LaneLoad.materials` (`LaneLoad.kt:25`) is a computed getter, `get() = setOfNotNull(left, right)`,
and it is read by hot comparisons: `SeamGrid.kt:102`, `:119` and `:140` compare
`other.materials == belt.materials` or `belt.materials == setOf(behind.material)`,
`BeltRouteRules.poursInto` tests `material !in belt.materials`, and
`LayoutValidationUnderground.kt:167` compares `materials == setOf(material)`. That is 246 MB
attributed to the getter and most of the 557 MB of `LinkedHashSet`, plus a hash lookup and a
`Material.equals` per comparison.

Compute the set once per instance (a `val`), and add
`fun carries(material: Material): Boolean` and `fun sameAs(other: LaneLoad): Boolean` so the
comparison sites never build a set at all — replace `materials == setOf(m)` with `carries(m)` and
`a.materials == b.materials` with `a.sameAs(b)`. Keep `materials` for the callers that genuinely
want a collection. Check every `materials` call site, including `BeltEntity`.

Add tests: a unit case for `carries`/`sameAs` on a full, a half and a mixed lane load, and the
existing compaction, routing and validation suites unchanged.

### Replace the boxed collections in the flow validation

`validateLayout` runs in the production path, once per candidate width (`LayoutCompaction.kt:93`)
and once per seam (`CompactionPass.kt:56`), and the validation stage is 145 inclusive samples
(8.5%). `trimAcyclic` (`LayoutValidationFlow.kt:119-133`) is 24 self samples because `Int` nodes
are boxed into `ArrayDeque` and `filterTo(mutableSetOf())`; `beltLoops` accounts for 84
map-allocation samples and `validateOverlaps` 48.

Rewrite the flow pass with `IntArray`-backed structures: an int queue (or the existing
`IntArray` + index cursor), `IntArray` adjacency instead of `List<List<Int>>` plus
`HashMap<Int, MutableSet<Int>>` in `circleGroups`, and `BooleanArray` visited sets. Do this on top
of the dense `LayoutIndex` item, or on its own if the boxing is still worth it after that lands.

Add tests: the existing validation unit cases cover the rules; add one that asserts
`trimAcyclic`'s result on a graph with a self-loop and one on a two-cycle, and keep the golden
layout suite unchanged.
