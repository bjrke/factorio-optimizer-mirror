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

### Compare the receiving lane, not the material set, where belts meet perpendicular

`SeamGrid.cornersIntoPerpendicularLane`
(`src/commonMain/kotlin/de/bjrke/factorio/optimizer/common/blueprint/placing/compaction/SeamGrid.kt:114`)
compares `ahead.load.sameMaterialsAs(belt.load)` even though `ahead` faces perpendicular;
`LayoutValidationTransport`
(`src/commonMain/kotlin/de/bjrke/factorio/optimizer/common/blueprint/layout/validation/LayoutValidationTransport.kt:85`
and `:126`) does the same in `checkAhead`'s turn branch and `checkSideLoad`'s early return, and
`SplitDeliveryRouter`
(`src/commonMain/kotlin/de/bjrke/factorio/optimizer/common/blueprint/placing/routing/SplitDeliveryRouter.kt:128`)
compares single-material labels. Same-direction comparisons are safe because both belts share one
lane frame. Perpendicular ones are not: a mixed belt (`A` left, `B` right) turning into a mixed belt
(`B` left, `A` right) has equal material sets, passes, and both materials then land on the receiving
belt's near lane.

Correct rule: the source must be single-material `M` and
`ahead.load.laneAt(ahead.direction, directionBetween(aheadTile, fromTile)) == M`; a mixed source
must not side-load at all. The same applies to the mixed splitter's second output.

Add tests: a seam-compaction unit case where a mixed corner would mis-splice only under the set
comparison; a validation case where a mixed belt turns into a mixed belt with swapped lanes and
must report a foreign side-load.

### Let an inserter carry more than one material

`InserterEntity.material` is singular; `MachineInserterPlacer`
(`src/commonMain/kotlin/de/bjrke/factorio/optimizer/common/blueprint/planning/row/MachineInserterPlacer.kt:100`)
labels the inserter with the biggest share of a mixed belt or a multi-solid-result recipe, and
`LayoutValidationInserter`
(`src/commonMain/kotlin/de/bjrke/factorio/optimizer/common/blueprint/layout/validation/LayoutValidationInserter.kt:45`
and `:61`) checks only that label. A second ingredient/result is invisible to validation, and a
missing one is never reported. A blueprint encodes no inserter material
(`src/commonMain/kotlin/de/bjrke/factorio/optimizer/common/blueprint/printing/json/BlueprintEntityJson.kt`),
so this is model-only: give the inserter a material set, check the source/drop carries every recipe
material it moves, keep one label for messages.

Add tests: a mixed input belt where the recipe needs both lanes' materials but the inserter is
labelled with one; a multi-solid-result recipe whose output inserter must move both results; a
missing second material must report instead of passing.

### Decide whether a full belt may claim a lane its source never fills

`LaneLoad` lets a half source feed a fuller single-material target (`feedsInto`/`takesStraightFrom`),
so the target's other lane is marked with a material no feeder supplies; that is the deliberate
"half output belt may feed a full trunk" rule. A lane-coverage validation that requires every
claimed lane to have a feeder (straight belt, side feeder, inserter drop or loader) reports 73 of
the reported layouts, starting with produced-delivery paths and tunnels that begin on a half product
output: the full label is a capacity reservation, not content.

Either propagate the producer's half load through `DeliveryPreparation`/`DeliveryRouter` and the
consumer's row input belts (loads are not emitted, so the blueprints stay the same, but compaction,
tunnels and every lane check change), or keep the capacity reservation and state that a full belt
does not promise both lanes are fed. Decide before adding lane-coverage validation.

Add tests: a half producer feeding a consumer path whose plan needs both lanes must either be
rejected or get a half consumer input; a half source into a mixed target must keep the existing
lane-for-lane requirement.
