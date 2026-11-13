package de.bjrke.factorio.optimizer.common.blueprint

import de.bjrke.factorio.optimizer.common.blueprint.layout.BeltEnd
import de.bjrke.factorio.optimizer.common.blueprint.layout.LayoutBoard
import de.bjrke.factorio.optimizer.common.blueprint.layout.LayoutNames
import de.bjrke.factorio.optimizer.common.blueprint.layout.PowerPole
import de.bjrke.factorio.optimizer.common.blueprint.layout.validation.validateLayout
import de.bjrke.factorio.optimizer.common.blueprint.placing.compaction.CompactionPass
import de.bjrke.factorio.optimizer.common.blueprint.placing.compaction.UndergroundStretcher
import de.bjrke.factorio.optimizer.common.blueprint.placing.plumbing.FluidFeeder
import de.bjrke.factorio.optimizer.common.blueprint.placing.route.LayoutRouter
import de.bjrke.factorio.optimizer.common.blueprint.placing.routing.DeliveryRouter
import de.bjrke.factorio.optimizer.common.blueprint.placing.routing.ImportTrimmer
import de.bjrke.factorio.optimizer.common.blueprint.placing.special.BeltEndConnector
import de.bjrke.factorio.optimizer.common.blueprint.placing.special.PowerPlacer
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.BlueprintPlan
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.ImportExtension
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.blueprint.planning.row.LayoutStage
import de.bjrke.factorio.optimizer.common.blueprint.planning.row.RowPlacer
import de.bjrke.factorio.optimizer.common.blueprint.planning.row.RowPlanner
import de.bjrke.factorio.optimizer.common.blueprint.printing.inspection.LayoutDump
import de.bjrke.factorio.optimizer.common.blueprint.printing.model.Blueprint
import de.bjrke.factorio.optimizer.common.blueprint.printing.output.chestWires
import de.bjrke.factorio.optimizer.common.blueprint.printing.output.toBlueprintEntity
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.game.prototype.InserterTier

internal class RowLayout(
    val plan: BlueprintPlan,
    // logistics and beltEnd only feed the stage constructors below, so they stay plain
    // constructor parameters instead of fields.
    logistics: LogisticsCalculator,
    beltEnd: BeltEnd = BeltEnd.CONSTANT_COMBINATOR,
    private val powerPole: PowerPole = PowerPole.MEDIUM_ELECTRIC_POLE,
) {
    private val board =
        LayoutBoard(
            LayoutNames(
                belt = logistics.belt.identifier,
                inserter = logistics.inserter.identifier,
                longInserter = InserterTier.LONG_HANDED_INSERTER.identifier,
                pole = powerPole.identifier,
            ),
            undergroundRange =
                when {
                    logistics.belt.identifier.contains("turbo") -> 11
                    logistics.belt.identifier.contains("express") -> 9
                    logistics.belt.identifier.contains("fast") -> 7
                    else -> 5
                },
            logistics::isFluid,
            logistics::fluidTemperature,
        )

    /**
     * What the layout could not do: an input whose belt did not route, or a route that only exists
     * outside the compact search box. A non-empty list means the layout needs attention.
     */
    private val failures = mutableListOf<String>()

    private val router = LayoutRouter(board, failures)

    private val connector = BeltEndConnector(board, beltEnd)

    /**
     * The machine-port tiles a fluid must reach. An underground run never swallows one, so growing
     * a run cannot pull a pipe off the machine it feeds.
     */
    private val fluidPorts = mutableSetOf<Coordinate>()

    /** The import markers and west bounds the trim, compaction and power stages share. */
    private val imports = ImportExtension()

    private val planner = RowPlanner(plan, logistics)

    private val rowPlacer = RowPlacer(board, plan, logistics, planner, failures)

    private val stage = LayoutStage(board, plan, logistics, planner, rowPlacer, router)

    private val fluidFeeder = FluidFeeder(stage, beltEnd, imports, fluidPorts, failures)

    private val importTrimmer = ImportTrimmer(board, connector, beltEnd, imports)

    private val compactionPass = CompactionPass(board, beltEnd, imports, fluidPorts)

    private val deliveryRouter = DeliveryRouter(stage, connector, imports, beltEnd, failures)

    private var initialized = false

    private fun build() {
        if (!initialized && plan.rows.isNotEmpty()) {
            initialized = true
            rowPlacer.placeAll()
            deliveryRouter.reserveTargetOutput()
            // prepare every source before routing anything, so a long early route cannot occupy a
            // marker or producer outlet that a later delivery needs
            val deliveries = deliveryRouter.prepareDeliveries()
            // the fluid lanes and markers go first: their geometry is fixed and a lane can read
            // the belts it passes under. The solid imports then take their direct path before the
            // routed fluid fallback and the produced trunks.
            fluidFeeder.placeImports()
            deliveryRouter.routeImports(deliveries.imports)
            fluidFeeder.routeFluids()
            deliveryRouter.routeProduced(deliveries.produced)
            fluidFeeder.connectLanes()
            fluidFeeder.placeTargetOutput()
            // the markers may move now that the routes are fixed; then the layout is compacted
            // before electricity, so poles and wires are placed on the final geometry
            importTrimmer.trim()
            stretchUndergroundRuns()
            compactionPass.compact()
            // the product connector goes last so compaction cannot drag it back over the routes
            deliveryRouter.placeTargetOutput()
            PowerPlacer(board, imports.poleWestBound, powerPole).place()
            // compaction accepts a corridor only when the whole layout validates, so an earlier
            // error silently turns the pass off; surface it instead of shipping a broken layout
            failures += validateLayout(board.placed.toList())
        }
    }

    private fun stretchUndergroundRuns() = UndergroundStretcher(board, fluidPorts).stretchAll()

    /** The placed entities and the feed routes that failed, for the CLI's text layout. */
    internal fun layoutDump(): LayoutDump {
        build()
        return LayoutDump(board.placed.toList(), failures.toList())
    }

    /**
     * Lays out [plan] as rows of connected factories. A plain recipe is one row of touching
     * machines, while a direct-feed band leaves a column at every linked machine pair, fed by belt
     * lanes just above (a short inserter for the near lane, a long inserter for the far one) and
     * drained by an output belt just below. Rows flow top to bottom; imports enter from the left,
     * and a material built inside the plan runs on one trunk from its producer's output belt, with
     * a splitter on that belt for every further recipe that takes it. A fluid row gets one pipe
     * lane on a row beyond its belts, with an underground shaft down to every machine port; a
     * machine whose port the lane cannot reach keeps the routed pipe instead.
     *
     * The [beltEnd] chooses how a solid belt ends: the constant-combinator label (the default), an
     * infinity chest, or a chest the belt's inserters feed from or to. A fluid belt always keeps
     * the constant-combinator/pipe label, since a chest cannot carry a fluid.
     */
    internal fun blueprint(): Blueprint {
        build()
        return Blueprint(
            label = plan.label,
            icon = plan.target,
            entities = board.placed.map { it.toBlueprintEntity() },
            wires = chestWires(board.placed),
        )
    }
}
