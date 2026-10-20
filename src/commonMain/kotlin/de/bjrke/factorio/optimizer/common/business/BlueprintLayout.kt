package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.*

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
fun buildBlueprint(
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

/** Inputs whose belt could not be routed; empty for a valid blueprint. Used by tests. */
internal fun blueprintFeedFailures(
    plan: BlueprintPlan,
    logistics: LogisticsCalculator,
    beltEnd: BeltEnd = BeltEnd.CONSTANT_COMBINATOR,
) =
    RowLayout(plan, logistics, beltEnd).run {
        build()
        failures
    }

/**
 * The material each transport entity carries, keyed by tile. Used by tests to check nothing merges.
 */
internal fun blueprintTransportMaterials(
    plan: BlueprintPlan,
    logistics: LogisticsCalculator,
    beltEnd: BeltEnd = BeltEnd.CONSTANT_COMBINATOR,
) =
    RowLayout(plan, logistics, beltEnd).run {
        build()
        transportMaterials()
    }

/** The fluid each pipe or pipe-to-ground tile carries. Used by tests to check no fluids merge. */
internal fun blueprintPipeFluids(
    plan: BlueprintPlan,
    logistics: LogisticsCalculator,
    beltEnd: BeltEnd = BeltEnd.CONSTANT_COMBINATOR,
) =
    RowLayout(plan, logistics, beltEnd).run {
        build()
        pipeFluids()
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
    private val logistics: LogisticsCalculator,
    private val beltEnd: BeltEnd,
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

    private val fluidFeeder =
        FluidFeeder(
            board,
            plan,
            logistics,
            planner,
            rowPlacer,
            router,
            beltEnd,
            imports,
            fluidPorts,
            failures,
        )

    private val importTrimmer = ImportTrimmer(board, connector, beltEnd, imports)

    private val compactionPass = CompactionPass(board, beltEnd, imports, fluidPorts)

    private val deliveryRouter =
        DeliveryRouter(
            board,
            plan,
            logistics,
            planner,
            rowPlacer,
            router,
            connector,
            imports,
            beltEnd,
            failures,
        )

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

    /** The material each transport entity carries, keyed by the tile. Used by tests. */
    fun transportMaterials() =
        placed.filterIsInstance<TransportEntity>().associate {
            it.area.topLeft to it.material
        }

    /** The fluid each pipe or pipe-to-ground tile carries, keyed by the tile. Used by tests. */
    fun pipeFluids() =
        placed.filterIsInstance<Plumbing>().associate {
            it.area.topLeft to it.material
        }

    /** The circuit wires a chest marker needs, relative to the final entity order. */
    fun blueprintWires(): List<BlueprintWire> = chestWires(placed)

    private fun stretchUndergroundRuns() = UndergroundStretcher(board, fluidPorts).stretchAll()
}
