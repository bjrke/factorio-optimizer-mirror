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

Each block below is self-contained: copy one as the task prompt. Each is one
branch with its own re-profile. The board block lands first because the route
block uses its int-based queries.

Baseline: the 2026-11-26 `./gradlew jvmTest -Pbenchmark -Pprofile` run. The
`jvmTest` task took 253 s with JFR attached and 213 s without; the recording
spans 214 s and holds 18,172 main-thread CPU samples. It allocates ~287 GB
(2,420 young GCs, 2,706 pauses, 4.59 s total pause, P99 8.66 ms), so the cost
is still boxing and cache misses, not pauses.
By the first project frame: delivered-feed routing 9,086 samples (50%),
compaction 5,399 (30%), band search 1,688 (9%), validation 1,394 (8%),
everything else 605 (3%). **11,286 samples (62%) sit inside
`HashMap`/`equals`/`hashCode`/boxing internals** — `getNode` 4,642 (26%),
`Intrinsics.areEqual` 1,756 (10%), `Coordinate.equals` 1,424 (8%), `putVal`
1,302 (7%), `resize` 1,220 (7%), `MinHeap`'s comparator 830 (5%) — and only 38%
in the search logic. The hash share is 6,200 of 9,086 routing samples (68%) and
4,033 of 5,399 compaction samples (75%), while band search is down to 132 of
1,688 (8%): the recipe-index keying and the interned materials did their job.

Re-measure every block the same way:

```sh
./gradlew jvmTest -Pbenchmark                          # wall clock, compare against 213 s
./gradlew jvmTest -Pbenchmark -Pprofile                # fresh recording, 1 fork, deletes the dir; 253 s with JFR
F=$(ls -t build/profiles/jvmTest/*.jfr | head -1)
env -u JAVA_TOOL_OPTIONS jfr view --width 160 hot-methods "$F"
env -u JAVA_TOOL_OPTIONS jfr print --events jdk.ExecutionSample --stack-depth 40 --json "$F" > /tmp/opencode/samples.json
S=.opencode/skills/jfr-profiles/scripts/jfr-top.sh ; $S self -n 25 ; $S alloc -n 20
```

`jfr print` defaults to five stack frames; at that depth every sample looks like
it sits in the test method and the stage split is invisible. Track the
`HashMap`/`equals` sample count (11,286) next to the stage totals.

### Make the layout board a dense grid

`LayoutBoard.grid` (`blueprint/layout/LayoutBoard.kt:74`) is
`mutableMapOf<Coordinate, Cell>()` and every query hashes a `Coordinate`
(`entityAt` `:101`, `isFree` `:103`, `isReserved` `:105`, `reservedMaterialAt`
`:121`). `Tunnels` (`blueprint/layout/LayoutBoard.kt:77`) still keys its marks
by `Coordinate`: `Tunnels$Key.equals` is 59 self samples and `Tunnels.has`
allocates 1,396 MB.

Replace it with flat storage over the bounds the board already tracks
(`minX`/`endX`/`minY`/`endY`, updated in `place()` at `:132-139` and reset in
`replaceAll` at `:150-158`): index `(x - originX) + (y - originY) * width`, and
grow the origin to the west and north so `IMPORT_FEED` and negative tiles
survive. Prefer parallel arrays over one `Cell` object per tile (entity index
`IntArray` with `-1`, `BooleanArray reserved`, a reserved-material array and an
edge-fluid array). Keep the public queries and add int overloads so the hot
paths stop passing `Coordinate` at all, and fold `Tunnels` into the same dense
storage.

`RowReservations.sections` (`LayoutBoard.kt:169`) is a `Map<Int, Section>` read
as `sections[coordinate.y]` at `:175-179`, for at most a few dozen rows. Store
an offset `Array<Section?>` over the reserved row range, cleared on `clear()`.

Add tests: a board unit case that places entities across negative coordinates
and asserts `entityAt`/`isReserved`/`removeAt` after `replaceAll`; a
`RowReservations` case with negative and sparse rows; the full layout suite
(every golden URL and blueprint) unchanged.

### Key the route search by packed node ids

Delivered-feed routing is 50% of the run (9,086 samples) and 68% of it is hash
traffic. `RouteSearch.relax` is 2,773 inclusive samples (15%) and
`expandSurface` 6,006 (33%); `gScore`
(`blueprint/placing/route/RouteSearch.kt:84`) and `previous` (`:85`) are keyed
by the three-component data class `RouteNode` (`:41`), and
`HashMap.getNode` is 4,642 self samples (26%). `RouteNode.equals` itself is
only 13 self samples now, but a fresh `RouteNode` and `Coordinate` are
allocated for every expansion attempt and the `Int` score is boxed. The
allocation is `TileGeometryKt.getNeighbors` 14,138 MB plus the four helpers
(south 13,254, north 12,466, east 12,320, west 10,595 MB), `Coordinate.plus`
15,756 MB and `Integer.valueOf` 12,337 MB. `MAX_ROUTE_EXPANSIONS` is 1,000,000
per route (`:34`), so a route that never fits burns the full budget in hash
lookups.

Encode a node as one `Int`/`Long` instead: `x` is bounded by
`ROUTE_WEST_BOUND..eastBound`, `y` by `northBound..southBound`, the heading is
one of four ordinals and `straight` is a small int, so the region size is known
once per search. Use a small int-keyed open-addressing map owned by the route
package (commonMain has no primitive map) for `gScore`, and store the
predecessor as a packed key too; keep `RouteNode` only for `reconstructRoute`.
`RouteSearch.kt:134` also computes `surface.neighbors.any(board::isReserved)`,
which allocates an `ArrayList` plus four `Coordinate`s per expansion
(`TileGeometry.kt:53`); replace it with the four ordered checks against the
int-based board query from the dense-grid item. The documented neighbor order
(north, east, south, west) and the `MAX_ROUTE_EXPANSIONS` cap stay as they are.
Let `MinHeap` compare through the element's own `compareTo` when no comparator
is passed (`MinHeap.kt:87`) — the comparator lambda is 830 self samples (5%),
and `siftDown`/`siftUp` add 304 + 106; drop the second write of the element in
`add` (`MinHeap.kt:24-25`).

Add tests: a routing unit case where two nodes differ only in `straight` and
must not share a `gScore` entry, one where they differ only in heading, one
where two nodes from different regions must not collide when the packing is
wrong, and the existing routing and golden layout suites unchanged.

### Reuse the band-search feed windows

Band search is down to 9% of the run (1,688 samples, only 132 hash samples)
because the recipe-index keying and interned materials landed, but its
allocation is still the largest single site: `FeedWindow.<init>`
(`blueprint/planning/band/BandSearch.kt:308`) is 31,945 MB (11% of the sweep)
for `demand`, `supply`,
`remaining`, `used` and `feeds` (`:310-329`), `InnerBandSearch.window` adds
2,987 MB and `FeedWindow.consume` 1,889 MB. `RecipeIndex.box-impl` adds 5,058 MB
from the generic `Map<RecipeIndex, _>` reads (`:55`, `:111`, `:321`).

Reuse one scratch `FeedWindow` per `BandCycleSearch`, resetting the arrays and
allocating only the `BandFeed` list a plan keeps, or memoize windows by the
cycle pattern; keep the recipe-index pairing, the rate model and the plan it
settles on byte-identical.
Index `needs` and the machine counts by `RecipeIndex.value` in a `DoubleArray`
and `IntArray` where the boxing remains. Keep the documented `GROUP_MAX_MACHINES`,
`MAX_ROW_BELTS` and `MAX_COPIES` bounds.

Add tests: a case where the same cycle pattern is asked for twice and the
memoized/reused window returns the same plan; a mixed input belt where two
equal-but-distinct `FactoryProduction` values appear in `group.needs`/`links`
and must still pair by recipe slot; the existing band, plan and golden layout
suites unchanged.

### Let an inserter carry more than one material

`InserterEntity.material` (`LayoutTransportEntities.kt:71`) is singular;
`MachineInserterPlacer`
(`src/commonMain/kotlin/de/bjrke/factorio/optimizer/common/blueprint/planning/row/MachineInserterPlacer.kt:100`)
labels the inserter with the biggest share of a mixed belt or a multi-solid-result
recipe, and `LayoutValidationInserter`
(`src/commonMain/kotlin/de/bjrke/factorio/optimizer/common/blueprint/layout/validation/LayoutValidationInserter.kt:45`
and `:61`) checks only that label. A second ingredient/result is invisible to
validation, and a missing one is never reported. A blueprint encodes no inserter
material
(`src/commonMain/kotlin/de/bjrke/factorio/optimizer/common/blueprint/printing/json/BlueprintEntityJson.kt`),
so this is model-only: give the inserter a material set, check the source/drop
carries every recipe material it moves, keep one label for messages.

Add tests: a mixed input belt where the recipe needs both lanes' materials but
the inserter is labelled with one; a multi-solid-result recipe whose output
inserter must move both results; a missing second material must report instead
of passing.
