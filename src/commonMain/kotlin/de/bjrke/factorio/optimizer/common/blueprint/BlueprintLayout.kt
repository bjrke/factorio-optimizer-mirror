package de.bjrke.factorio.optimizer.common.blueprint

import de.bjrke.factorio.optimizer.common.beltend.BeltEndConnector
import de.bjrke.factorio.optimizer.common.blueprint.inspection.LayoutDump
import de.bjrke.factorio.optimizer.common.blueprint.output.chestWires
import de.bjrke.factorio.optimizer.common.blueprint.output.toBlueprintEntity
import de.bjrke.factorio.optimizer.common.compaction.CompactionPass
import de.bjrke.factorio.optimizer.common.compaction.UndergroundStretcher
import de.bjrke.factorio.optimizer.common.layout.BeltEnd
import de.bjrke.factorio.optimizer.common.layout.LayoutBoard
import de.bjrke.factorio.optimizer.common.layout.LayoutNames
import de.bjrke.factorio.optimizer.common.layout.entity.LayoutEntity
import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.plan.BlueprintPlan
import de.bjrke.factorio.optimizer.common.plan.ImportExtension
import de.bjrke.factorio.optimizer.common.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.planning.LayoutStage
import de.bjrke.factorio.optimizer.common.planning.RowPlacer
import de.bjrke.factorio.optimizer.common.planning.RowPlanner
import de.bjrke.factorio.optimizer.common.plumbing.FluidFeeder
import de.bjrke.factorio.optimizer.common.prototype.InserterTier
import de.bjrke.factorio.optimizer.common.route.LayoutRouter
import de.bjrke.factorio.optimizer.common.routing.DeliveryRouter
import de.bjrke.factorio.optimizer.common.routing.ImportTrimmer
import de.bjrke.factorio.optimizer.common.special.PowerPlacer

/**
 * Lays out [plan] as rows of connected factories. Every recipe is one row of touching machines, fed
 * by belt lanes just above (a short inserter for the near lane, a long inserter for the far one)
 * and drained by an output belt just below. Rows flow top to bottom; imports enter from the left,
 * and a material built inside the plan runs on one trunk from its producer's output belt, with a
 * splitter on that belt for every further recipe that takes it. Fluids use pipe clusters next to
 * the machines instead of belts.
 *
 * The [beltEnd] chooses how a solid belt ends: the constant-combinator label (the default), an
 * infinity chest, or a chest the belt's inserters feed from or to. A fluid belt always keeps the
 * constant-combinator/pipe label, since a chest cannot carry a fluid.
 */
fun fromPlaced(
    plan: BlueprintPlan,
    logistics: LogisticsCalculator,
    beltEnd: BeltEnd = BeltEnd.CONSTANT_COMBINATOR,
) =
    RowLayout(plan, logistics, beltEnd).run {
        val entities = build()
        Blueprint(
            label = plan.label,
            icon = plan.target,
            entities = entities,
            wires = blueprintWires(),
        )
    }

/** The placed entities and the feed routes that failed, for the CLI's text layout. */
internal fun blueprintLayoutDump(
    plan: BlueprintPlan,
    logistics: LogisticsCalculator,
    beltEnd: BeltEnd = BeltEnd.CONSTANT_COMBINATOR,
) =
    RowLayout(plan, logistics, beltEnd).run {
        build()
        LayoutDump(placedEntities(), failures.toList())
    }

private class RowLayout(
    private val plan: BlueprintPlan,
    // logistics and beltEnd only feed the stage constructors below, so they stay plain
    // constructor parameters instead of fields.
    logistics: LogisticsCalculator,
    beltEnd: BeltEnd,
) {
    private val board =
        LayoutBoard(
            LayoutNames(
                belt = logistics.belt.identifier,
                inserter = logistics.inserter.identifier,
                longInserter = InserterTier.LONG_HANDED_INSERTER.identifier,
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

    private val router = LayoutRouter(board)

    private val connector = BeltEndConnector(board, beltEnd)

    private val placed
        get() = board.placed

    /**
     * The machine-port tiles a fluid must reach. An underground run never swallows one, so growing
     * a run cannot pull a pipe off the machine it feeds.
     */
    private val fluidPorts = mutableSetOf<Coordinate>()

    /** Inputs whose belt could not be routed; a non-empty list means the blueprint is broken. */
    val failures = mutableListOf<String>()

    /** The import markers and west bounds the trim, compaction and power stages share. */
    private val imports = ImportExtension()

    private val planner = RowPlanner(plan, logistics)

    private val rowPlacer = RowPlacer(board, plan, logistics, planner)

    private val stage = LayoutStage(board, plan, logistics, planner, rowPlacer, router)

    private val fluidFeeder = FluidFeeder(stage, beltEnd, imports, fluidPorts, failures)

    private val importTrimmer = ImportTrimmer(board, connector, beltEnd, imports)

    private val compactionPass = CompactionPass(board, beltEnd, imports, fluidPorts)

    private val deliveryRouter = DeliveryRouter(stage, connector, imports, beltEnd, failures)

    fun build(): List<BlueprintEntity> {
        if (plan.recipes.isNotEmpty()) {
            rowPlacer.placeAll()
            deliveryRouter.placeTargetOutput()
            // prepare every source before routing anything, so a long early route cannot occupy a
            // marker or producer outlet that a later delivery needs
            val deliveries = deliveryRouter.prepareDeliveries()
            // pipes go first: their underground runs are cheap and belts can tunnel under them
            fluidFeeder.feed()
            deliveryRouter.routeDeliveries(deliveries)
            fluidFeeder.placeTargetOutput()
            // the markers may move now that the routes are fixed; then the layout is compacted
            // before electricity, so poles and wires are placed on the final geometry
            importTrimmer.trim()
            stretchUndergroundRuns()
            compactionPass.compact()
            PowerPlacer(board, imports.poleWestBound).place()
        }
        return placed.map { it.toBlueprintEntity() }
    }

    /** The entities placed so far, in placement order. Used by [blueprintLayoutDump]. */
    fun placedEntities(): List<LayoutEntity> = placed.toList()

    /** The circuit wires a chest marker needs, relative to the final entity order. */
    fun blueprintWires(): List<BlueprintWire> = chestWires(placed)

    private fun stretchUndergroundRuns() = UndergroundStretcher(board, fluidPorts).stretchAll()
}
