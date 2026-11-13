# Todo

- http://localhost:8080/#/1.8/splitter/CFSXbmr/1/1/10/iron-plate and
  http://localhost:8080/#/1.8/splitter/CFSXbmr/1/1/10/copper-ore,iron-plate have different poles 
  because the amount of inserters changes. The reason is probably a different autoscale.

## Feature findings — prompt-ready

Each block below is self-contained: copy one as the task prompt.

### Connect an electromagnetic-plant row by mirroring alternate machines

Fix the electrolyte layout `http://localhost:8080/#/24/electrolyte/FSXmor/1/1`, where a row of
four touching electromagnetic plants is only partly connected.

`RowPlacer.placeRow` advances every machine by `blueprintMachineFootprint` width, so the plants
touch. `prototypeFluidPorts` (`BlueprintMachine.kt`) is written for the single
`blueprintMachineDirection` orientation: with `Direction.EAST` the heavy oil and holmium solution
sit on the north/south edges, and both electrolyte outputs on the west/east side edges. A side port
of an interior machine is covered by the neighbour's footprint, so `FluidFeeder.connectTargetOutput`
(`FluidFeeder.kt:154`) finds the tile neither free nor reserved, returns silently, and only the
westmost plant gets an electrolyte infinity pipe. The other three machines drain nowhere and the
layout reports no failure.

Mirror every second plant in the row so the fluid ports of neighbouring machines meet and connect,
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

### Group the selection controls and keep each group on one line

Group the web UI's selection controls and stop line breaks inside a group; wrapping may only happen
between groups.

The selectors build flat sequences of `selectableIcon` labels (`FactorySelector`, `PlanetSelector`,
`LogisticsSelector`), and the container wraps them anywhere. Render each logical group in its own
container: a radio group from `FactoryConfig.groups`, the `singles` checkboxes, the surface, the
belt, the inserter and the belt end. Each group is a nowrap flex row; the outer container is the
only place that wraps. Keep the native radio `groupName` semantics so keyboard and screen-reader
behaviour does not regress.

Add a jsTest component test that renders the selectors and asserts every group is one nowrap flex
row and that neighbouring groups are separate siblings. Do not assert pixel widths: the headless
Chrome font metrics differ. Keep the existing selector tests green.

### Plan rocket parts and launches with the Space Age rocket silo

Make a rocket-silo plan possible in Space Age, so the optimizer can feed a silo as fast as possible
and launch one rocket after another.

`Factory.ROCKET_SILO` is `baseOnly` (`Factory.kt:75`), so Space Age offers no rocket-building
machine at all; `rocket-part` recipes exist in both dumps, but a `rocket-part` target has nowhere to
build. The Space Age dump has a `rocket-silo` with `fixed_recipe = rocket-part`,
`crafting_categories = [rocket-building]`, crafting speed 1, `rocket_parts_required` and a 9×9
footprint (`blueprintMachineFootprint` already knows the size), and `rocket-part` is processing-unit
+ low-density-structure + rocket-fuel.

Add the Space Age silo as a factory next to the base-game one: 9×9, rocket-building category, the
fixed `rocket-part` recipe (no recipe choice), no fluid ports, solid inputs fed by inserters, and no
product belt — the parts stay in the silo and a launch consumes `rocket_parts_required` of them.
The plan must report how many launches per minute it can sustain rather than pretending the parts
are exported; fix the base-game path only where it shares code.

Add tests: `FactoryConfig(spaceAge = true)` offers the silo while the base game still has it; an
optimizer test for `rocket-part` in Space Age where the silo is the producing factory, the three
ingredients are planned or imported and there is no free source; a blueprint/layout test where the
9×9 silo sits in a row with its three input lanes, no output belt or infinity chest for the parts,
`failures` empty and `validateLayout` clean; a launch-rate assertion (parts /
`rocket_parts_required`) that covers the base-game `rocket_parts_required` fallback of 100.

### Model biochamber nutrients as fuel and break the cycle

Count the nutrients a biochamber burns, without creating a nutrients ↔ bioflux cycle.

A biochamber runs on nutrients instead of electricity, but `FactorySpec.burner` and `burnerFuel`
(`RecipePolicy.kt`) model fuel only for furnaces, so a biochamber's nutrient demand is missing from
the plan and the listing. The dump carries `fuel_value = 2MJ` for nutrients and a biochamber
`energy_usage` of 500kW, i.e. 0.25 nutrients per second per running biochamber. Make the fuel a
factory property (nutrients for the biochamber on every surface, separate from the surface fuel
`burnerFuel` returns for furnaces), and show the demand in the listing and the plan.

The fuel edge creates a cycle: nutrients are made in a biochamber, and bioflux — the nutrients
input — is itself made in a biochamber that would burn nutrients. Break it with a pseudo material
for the fuel term: a distinct material key that renders with the nutrients icon and name
(`iconLayers`), used for the burner energy instead of `nutrients`, and supplied either as an import
or from the real nutrients row — pick one and document it in the owning KDoc. The pseudo material
must never reach the blueprint as an item that cannot be moved.

Biochambers also need no poles: `MachineEntity.isPowered` is hard-coded true
(`LayoutEntityStations.kt:16`) and `PowerPlacer` covers every powered entity, so a biochamber row
gets poles for the machine body. Let burner machines be unpowered; their inserters still need
poles, so the pole count drops rather than the row going dark.

Add tests: a biochamber production consumes 0.25 nutrients/s and the pseudo material appears in the
plan's materials with the nutrients icon; a `nutrients` target on Gleba plans without a
nutrients ↔ bioflux cycle, and a bioflux target likewise; a blueprint test where biochamber rows
place no pole for the machine body (or strictly fewer poles), the inserters stay covered and
`validateLayout`/power tests pass; a `PowerPlacer`/`isPowered` unit test for a burner machine.

## Review findings — prompt-ready

Each block below is self-contained: copy one as the task prompt. Line numbers refer to
`main` @ `2cf115a`.

### Gate the synthetic Space Age recipes

Gate `harvestedFruit()` and `asteroidCollectionRecipes()` in `resourceRecipes.kt:14-20` on the Space
Age dump, the way `harvestedWood` already checks for `tree-seed`. The base-game dump has no yumako,
jellynut or asteroid chunks, but the material list advertises all six. Add a test that
`RecipeLoader.forActiveMods(false).materials` contains none of them.

### Fix the fluid lane / far merge collision

Fix the collision between a far mixed belt's U-turn and the fluid lane's inline source block.

`FAR_TOP` sits at `factoryTop-3`, so its turn row is `factoryTop-5`, which equals `laneRow(NORTH)`
(`RowFluidLane.kt:87-99`). An east-fed merge puts its approach tile at column 8
(`EAST_MERGE_OFFSET`, `RowPlacer.kt:47`), and `sourceEnd` requires `head.x-3 .. head.x-1`
(`FluidLanePlacer.kt:98-103`), which always includes column 8 for the first machine. The
`canPlacePipes` check fails and `FluidLaneFeed.place` silently `continue`s
(`FluidLaneFeed.kt:67`): an input falls back to a routed pipe, but a non-target fluid output loses
its only drain and `FluidFeeder` routes inputs only (`FluidFeeder.kt:67-114`, `141-151`).

Move the source block clear of the turn/approach tiles (or reserve them as part of the lane), record
a failure when a drain lane cannot be placed, and add a test for a row with two belts on one side,
the far one an east-fed mixed belt, plus a fluid lane on that side.

### Reject unknown CLI option values

Reject unknown `--belt/--inserter/--planet/--belt-end` values instead of decoding them as another
choice.

`--belt=yellow` becomes `EXPRESS_TRANSPORT_BELT` (`e`), `--belt=turbo` becomes `TRANSPORT_BELT`
(`t`), `--inserter=yellow` becomes `BULK_INSERTER` (`y`), `--planet=mars` becomes `AQUILO` (`a`) and
`--belt-end=Debug` becomes `STEEL_CHEST` (`D`) through the character-scanning fallbacks
(`CliArgsParser.kt:117-131`, `Logistics.kt:87,118`, `SurfaceSelection.kt:11-12`,
`BeltEndSelection.kt:15-18`). Keep the character scan for URL `selection` segments only; a
`--name=value` must be an exact identifier or a single code character, otherwise exit non-zero with
a message. Add table-driven tests for every option.

### Resolve belt stacking with a non-stacking inserter

Fix the capacity model when `beltStack > 1` but the selected inserter cannot stack.

`beltStackMax` is 4 in Space Age for every inserter and the UI offers it
(`LogisticsSelector.kt:85-92`), while `beltThroughput(stack)` (`LogisticsCalculator.kt:105-111`) and
`oneLaneCapacity()` assume stacked input lanes for the plan and the listing. `placingThroughput()`
correctly ignores the stack for a fast/bulk inserter, so inputs and outputs of the same plan
disagree. Either clamp `beltStack` to 1 unless the selected inserter stacks (or a direct-output
machine does), or document the "pre-stacked input belt" assumption and stop offering >1. Pin both
sides in a test; `OptimizationResultLogisticsTest` currently pins only the output side.

### Replace history on text-field edits

Use `replace = true` in `changeUrl`'s `navigate` call (`index.kt:136-140`) for edits that come from
text fields: typing an amount currently pushes one hash entry per keystroke, so Back must be pressed
once per character. Keep PUSH for discrete icon clicks if that is wanted. Add a component test that
asserts the navigate option or the resulting history length.

### Reset the material picker highlight

Reset `highlighted` when Escape or blur closes the dropdown (`MaterialSelector.kt:57-80`). The
first ArrowDown after reopening otherwise highlights index 1 and Enter selects the wrong material.
Add a test that reopens after Escape and asserts index 0 is highlighted.

### Print prototype identifiers in the CLI listing

Print `production.factory.identifier` in the CLI listing (`CliOptimizer.kt:233`) instead of the enum
constant, so the listing shows `electric-mining-drill` rather than `ELECTRIC_MINING_DRILL` and does
not print `NO_FACTORY` for import rows. Add a CLI integration assertion.

### Make `--planet` override a pasted URL

Make `--planet=n` (and any surface choice) override the URL. `n` is not a surface code
(`NAUVIS.code == null`), so `surfaceFromPlanet("n")` returns null and the URL wins
(`CliArgsParser.kt:90-92`). Add a Nauvis code or accept the identifier `nauvis`, and add a test that
parses `--planet=nauvis` together with an SA URL and expects Nauvis. Extend
`CliOptimizerTest.planetSelectsTheSurfaceByIdentifierOrCode`, which currently passes only because it
never supplies a URL.

### Align the absent-selection default

Make an absent `selection` segment mean the same in both entry points: the CLI reads
`spaceAgeIn(null) == false` (`CliArgsParser.kt:79-84`) while the web UI reads `?: true`
(`index.kt:71`). Decide on Space Age on or off and apply it in both, and test the truncated URL
`#/4.5/advanced-circuit`.

### Type a fluid target icon

Write `type: "fluid"` for a fluid target's blueprint icon signal (`BlueprintJson.kt:30-35`), as
`BlueprintEntityJson.kt:145` already does for fluid marker filters. Add a JSON test for a fluid
target such as `petroleum-gas`.

### Keep compaction state in sync

Relocate `fluidPorts` after a compaction corridor shifts the layout (`CompactionPass.kt:58`,
`RowLayout.kt:109-116`) so `UndergroundStretcher`'s port protection still names the real port tiles.
Also fix the dead/wrong-axis `poleWestBound` update at `CompactionPass.kt:68-77`: it adds a y shift
to an x bound for horizontal corridors, currently unreachable because the horizontal call passes no
`pinned` tiles (`:42-47`); either pass the pins there and update both axes correctly, or remove the
branch.

### Fix the stale file count in the knowledge base

`knowledge/code.md:176` says `blueprint.layout` has 9 files; it has 10 (the table above says 10).
Correct the count.
