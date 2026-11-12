package de.bjrke.factorio.optimizer.common.blueprint.planning.row

import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.BlueprintPlan
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedRow
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.blueprintMachineFootprint
import de.bjrke.factorio.optimizer.common.core.model.Io
import de.bjrke.factorio.optimizer.common.core.model.LineSide
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.model.FactoryProduction

/** One row to place, north to south, with the side its own producers sit on. */
internal data class RowUnit(val index: Int, val row: PlannedRow, val side: LineSide?)

/**
 * The plan-level decisions a layout needs before it places anything: the row order, the side every
 * belt should face, and the planned outlet columns. Also the pure sizing helpers
 * (`inserterPattern`) the row placer uses, so no geometry leaks in.
 */
internal class RowPlanner(
    plan: BlueprintPlan,
    private val logistics: LogisticsCalculator,
) {

    private val topology = RowTopology(plan, logistics)

    /** The rows to place, north to south, in the target-centred topology order. */
    val units: List<RowUnit> = topology.units

    fun isTopologyAware(unit: RowUnit) = topology.isTopologyAware(unit.index)

    fun preferredInputSide(unit: RowUnit, material: Material) =
        topology.preferredInputSide(unit.index, material)

    fun preferredOutputSide(unit: RowUnit, material: Material) =
        topology.preferredOutputSide(unit.index, material)

    /** The planned east outlet of [material]'s producer, before any row is placed. */
    fun outletX(material: Material) = topology.outletX(material)

    fun footprint(production: FactoryProduction) = blueprintMachineFootprint(production.factory)

    /** How many inserters one machine needs to pull [items] out of its lane. */
    fun insertersFor(production: FactoryProduction, io: Io, items: Double) =
        logistics.beltInserterCount(
            when (io) {
                Io.INPUT -> production.inputPerMachine(items)
                Io.OUTPUT -> production.outputPerMachine(items)
            },
        )

    /**
     * A list of inserters per machine edge: one entry per inserter, `true` for a long one (the far
     * lane). [width] is how many columns the edge leaves free for inserters. The two lanes are
     * interleaved; when they do not fit, the counts are scaled down fairly.
     */
    fun <T> inserterPattern(amounts: Map<T, Number>, width: Int) = sequence {
        if (width <= 0) return@sequence
        val sum =
            amounts.values.sumOf {
                val v = it.toDouble()
                if (v > 0) v else 0.0
            }
        if (sum <= 0) return@sequence
        val ratio = (width / sum).coerceAtMost(1.0)

        val queue =
            amounts.entries.mapNotNullTo(ArrayDeque()) { (k, v) ->
                val v = v.toDouble()
                if (v > 0) k to v * ratio else null
            }
        queue.sortByDescending { (_, v) -> v }

        while (true) {
            val (k, v) = queue.removeFirstOrNull() ?: return@sequence
            when {
                v > 1 -> {
                    yield(k)
                    queue += k to v - 1
                }
                v > 0 -> {
                    yield(k)
                }
            }
        }
    }
        .take(width)
}
