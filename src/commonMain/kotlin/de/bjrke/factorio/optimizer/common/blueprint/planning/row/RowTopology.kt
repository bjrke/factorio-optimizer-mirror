package de.bjrke.factorio.optimizer.common.blueprint.planning.row

import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.BlueprintPlan
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedRow
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.blueprintMachineFootprint
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.rowInputs
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.rowOutputs
import de.bjrke.factorio.optimizer.common.core.model.LineSide
import de.bjrke.factorio.optimizer.common.core.model.Material

/** A target branch in this size range is small enough to distribute across both sides. */
private const val MIN_DISTRIBUTED_BRANCH_ROWS = 3
private const val MAX_DISTRIBUTED_BRANCH_ROWS = 4

/**
 * The row shape for a target-centred layout: every row's producers sit on one side of it, and the
 * target sits between its own branches.
 *
 * A producer is placed on the side of its consumer that matches the producer's position in
 * `BlueprintPlan.rows`: rows the plan lists before the target go north, rows after it go south. The
 * producer's own producers continue outward on the same side, so a branch stays contiguous. The
 * result is the north-to-south [units] order plus the preferred side of every belt: an input faces
 * its producer and a product faces the consumer that placed the row.
 *
 * The target's direct producers are split in plan order when it has exactly two branches. Three or
 * more branches are distributed across both sides, largest first and alternating, only when the
 * largest branch is [MIN_DISTRIBUTED_BRANCH_ROWS] to [MAX_DISTRIBUTED_BRANCH_ROWS] rows deep; every
 * other target stays compact and one-sided, largest branch nearest. A larger branch set is never
 * distributed: it strands the producers the branches share, disables compaction and doubles the
 * layout.
 *
 * Nodes are the row **positions** in `BlueprintPlan.rows`, never row values: two structurally equal
 * rows are two occurrences and must not collapse into one graph node.
 */
internal class RowTopology(
    plan: BlueprintPlan,
    logistics: LogisticsCalculator,
) {

    /** One plan row and the belt facts the graph needs, indexed by its plan position. */
    private class RowInfo(
        val row: PlannedRow,
        val inputs: Map<Material, Double>,
        val outputs: Map<Material, Double>,
        val beltMaterials: Set<Material>,
        val right: Int,
    )

    private val infos: List<RowInfo> =
        plan.rows.map { row ->
            val linked = row.links.mapTo(mutableSetOf()) { it.material }
            val size = blueprintMachineFootprint(row.machines.first().factory)
            RowInfo(
                row = row,
                inputs = rowInputs(row.machines, logistics).minus(row.fullyInternalInputs),
                outputs = rowOutputs(row.machines, logistics).minus(linked),
                beltMaterials = row.beltMaterials,
                right =
                    LANE_MARGIN +
                        row.machines.size * size.width +
                        row.links.map { it.gap }.toSet().size - 1,
            )
        }

    private val targetIndex: Int? =
        infos.indexOfFirst { plan.target in it.beltMaterials }.takeIf { it >= 0 }
            ?: infos
                .indexOfFirst { info ->
                    info.row.productions.any { it.recipe.name == plan.target.name }
                }
                .takeIf { it >= 0 }
            ?: infos.lastIndex.takeIf { it >= 0 }

    /** The first row that exports a material, matching `BlueprintPlan.producerOf`. */
    private val producerIndex: Map<Material, Int> = buildMap {
        infos.forEachIndexed { index, info ->
            for (material in info.beltMaterials) {
                if (material !in this) put(material, index)
            }
        }
    }

    private val parent = MutableList<Int?>(infos.size) { null }
    private val children = List(infos.size) { mutableListOf<Int>() }
    private val branchSide = MutableList<LineSide?>(infos.size) { null }
    private val layoutIndex = MutableList(infos.size) { -1 }
    private var targetProductSide: LineSide = LineSide.SOUTH

    /** The rows to place, north to south, with the side their own producers sit on. */
    val units: List<RowUnit> = build()

    private fun build(): List<RowUnit> {
        val root =
            targetIndex
                ?: return infos.mapIndexed { index, info ->
                    RowUnit(index, info.row, null)
                }
        val discovered = BooleanArray(infos.size)
        val queue = ArrayDeque<Int>()
        discovered[root] = true
        queue += root

        while (queue.isNotEmpty()) {
            val consumer = queue.removeFirst()
            val candidates =
                producersOf(consumer)
                    .filter { it != consumer && !discovered[it] }
                    .sortedWith(
                        compareByDescending<Int> { producer ->
                                val supplied = infos[producer].beltMaterials
                                infos[consumer].inputs.entries.sumOf { (material, rate) ->
                                    if (material in supplied) rate else 0.0
                                }
                            }
                            .thenBy { it }
                            .thenBy {
                                infos[it]
                                    .row
                                    .productions
                                    .map { p -> p.recipe.name }
                                    .sorted()
                                    .joinToString("+")
                            },
                    )
            for (producer in candidates) {
                parent[producer] = consumer
                children[consumer] += producer
                discovered[producer] = true
                queue += producer
            }
        }

        assignSides(root)

        val ordered = mutableListOf<Pair<Int, LineSide?>>()
        val emitted = BooleanArray(infos.size)
        emit(root, ordered, emitted)
        infos.indices
            .filter { !emitted[it] }
            .sortedBy {
                infos[it].row.productions.map { p -> p.recipe.name }.sorted().joinToString("+")
            }
            .forEach { index ->
                branchSide[index] = null
                ordered += index to null
            }
        ordered.forEachIndexed { position, (index, _) -> layoutIndex[index] = position }
        return ordered.map { (index, side) -> RowUnit(index, infos[index].row, side) }
    }

    /**
     * Distributes the target's producer branches across both sides when the largest branch is small
     * enough to fit the free side; otherwise keeps the compact one-sided order. A two-branch target
     * keeps the plan-order split. Every deeper branch stays contiguous on its side.
     */
    private fun assignSides(root: Int) {
        chooseTargetSides(root)
        val queue = ArrayDeque<Int>()
        children[root].forEach { queue += it }
        while (queue.isNotEmpty()) {
            val row = queue.removeFirst()
            val side = branchSide[row] ?: LineSide.SOUTH
            for (child in children[row]) {
                branchSide[child] = side
                queue += child
            }
        }
    }

    /** Chooses the target's branch sides and product side, and orders a one-sided branch set. */
    private fun chooseTargetSides(root: Int) {
        val targetChildren = children[root]
        val planSides =
            targetChildren.mapTo(mutableSetOf()) { child ->
                if (child < root) LineSide.NORTH else LineSide.SOUTH
            }
        val largest = targetChildren.maxOfOrNull { subtreeSize(it) } ?: 0
        val distribute =
            targetChildren.size >= 3 &&
                largest in MIN_DISTRIBUTED_BRANCH_ROWS..MAX_DISTRIBUTED_BRANCH_ROWS
        when {
            distribute -> {
                val ordered = targetChildren.sortedByDescending { subtreeSize(it) }
                targetChildren.clear()
                targetChildren += ordered
                ordered.forEachIndexed { index, child ->
                    branchSide[child] = if (index % 2 == 0) LineSide.NORTH else LineSide.SOUTH
                }
                targetProductSide = LineSide.SOUTH
            }
            planSides.size == 2 -> {
                targetChildren.forEach { child ->
                    branchSide[child] = if (child < root) LineSide.NORTH else LineSide.SOUTH
                }
                targetProductSide = LineSide.SOUTH
            }
            else -> {
                val side = planSides.singleOrNull() ?: LineSide.SOUTH
                val ordered = targetChildren.sortedByDescending { subtreeSize(it) }
                targetChildren.clear()
                targetChildren += ordered
                ordered.forEach { branchSide[it] = side }
                targetProductSide = if (side == LineSide.NORTH) LineSide.SOUTH else LineSide.NORTH
            }
        }
    }

    private fun emit(
        row: Int,
        out: MutableList<Pair<Int, LineSide?>>,
        emitted: BooleanArray,
    ) {
        if (emitted[row]) return
        emitted[row] = true
        val north = children[row].filter { branchSide[it] == LineSide.NORTH }
        val south = children[row].filter { branchSide[it] == LineSide.SOUTH }
        north.asReversed().forEach { emit(it, out, emitted) }
        out += row to branchSide[row]
        south.forEach { emit(it, out, emitted) }
    }

    /** The rows that put one of [row]'s external inputs on a belt. */
    private fun producersOf(row: Int): List<Int> =
        infos[row].inputs.keys.mapNotNull { producerIndex[it] }.distinct()

    /** How many rows the branch rooted at [row] contains, for nearest-first branch ordering. */
    private fun subtreeSize(row: Int): Int = 1 + children[row].sumOf { subtreeSize(it) }

    /** Whether the row at [index] has a producer or consumer row, so its belts have a side. */
    fun isTopologyAware(index: Int): Boolean =
        parent[index] != null ||
            infos[index].inputs.keys.any { producerIndex[it] != null } ||
            infos[index].outputs.keys.any { consumersOf(it, index).isNotEmpty() }

    /** The side [material]'s producer sits on, or the row's own producer side for an import. */
    fun preferredInputSide(index: Int, material: Material): LineSide? {
        val producer = producerIndex[material]
        if (producer == null || producer == index) return branchSide[index]
        val rowPosition = layoutIndex[index]
        val producerPosition = layoutIndex[producer]
        if (rowPosition < 0 || producerPosition < 0) return branchSide[index]
        return if (producerPosition < rowPosition) LineSide.NORTH else LineSide.SOUTH
    }

    /** The side [material]'s consumer sits on; a target product defaults south. */
    fun preferredOutputSide(index: Int, material: Material): LineSide? {
        val consumers = consumersOf(material, index)
        val rowPosition = layoutIndex[index]
        if (rowPosition < 0) return null
        val placingSide =
            parent[index]
                ?.takeIf { it in consumers }
                ?.let { placing -> layoutIndex[placing].takeIf { it >= 0 } }
                ?.let { position -> if (position < rowPosition) LineSide.NORTH else LineSide.SOUTH }
        val positioned = consumers.mapNotNull { consumer ->
            layoutIndex[consumer].takeIf { it >= 0 }?.let { consumer to it }
        }
        val north =
            positioned
                .filter { (_, position) -> position < rowPosition }
                .sumOf { (consumer, _) -> infos[consumer].inputs[material] ?: 0.0 }
        val south =
            positioned
                .filterNot { (_, position) -> position < rowPosition }
                .sumOf { (consumer, _) -> infos[consumer].inputs[material] ?: 0.0 }
        return placingSide
            ?: when {
                consumers.isEmpty() -> if (index == targetIndex) targetProductSide else null
                south >= north -> LineSide.SOUTH
                else -> LineSide.NORTH
            }
    }

    /** The rows that take [material] from [producer] over a belt. */
    private fun consumersOf(material: Material, producer: Int): List<Int> =
        infos.indices.filter { it != producer && material in infos[it].inputs }

    /** The tile east of the row that puts [material] on a belt, or Far East when none does. */
    fun outletX(material: Material): Int {
        val producer = producerIndex[material] ?: return Int.MAX_VALUE
        return infos[producer].right + 1
    }
}
