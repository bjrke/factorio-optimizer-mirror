package de.bjrke.factorio.optimizer.common.blueprint.layout.validation

import de.bjrke.factorio.optimizer.common.blueprint.layout.LayoutIndex
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.ConveyorEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.LayoutEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.LoaderEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.SplitterEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.UndergroundBeltEntity
import de.bjrke.factorio.optimizer.common.core.model.Area
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.Io
import de.bjrke.factorio.optimizer.common.core.model.Material

/**
 * The belt-flow checks: a splitter has to be fed from its back, and no belt may run in a circle
 * back into itself. A layout can keep every material apart, pair every tunnel and feed every
 * inserter while still leaving a circuit splitter dangling in front of a machine and looping a
 * produced lane into its own trunk; both starve the machines they were meant to feed.
 */
internal fun validateBeltFlow(
    index: LayoutIndex,
    entities: List<LayoutEntity>,
    errors: MutableList<String>,
) {
    val conveyors = entities.filterIsInstance<ConveyorEntity>()
    for (splitter in conveyors.filterIsInstance<SplitterEntity>()) {
        if (!splitter.isFed(index)) {
            errors += "splitter at ${splitter.area.topLeft} has no input"
        }
    }
    for (loop in beltLoops(conveyors, index)) {
        errors += "${loop.material.name} belt loops back at ${loop.tile}"
    }
}

/**
 * Whether a belt or loader behind [this] splitter empties onto it. A splitter takes items on the
 * two tiles behind its face only, so a belt that only runs alongside it cannot feed it.
 */
private fun SplitterEntity.isFed(index: LayoutIndex): Boolean = area.any { tile ->
    val behind = tile - direction.step
    feeds(index.at(behind), behind, area, index)
}

/** Whether [sender] lays its items onto a tile of [target]. */
private fun feeds(
    sender: LayoutEntity?,
    senderTile: Coordinate,
    target: Area,
    index: LayoutIndex,
): Boolean =
    when (sender) {
        is ConveyorEntity -> sender.outlets(index).any { it in target }
        is LoaderEntity -> sender.io == Io.OUTPUT && senderTile + sender.direction.step in target
        else -> false
    }

/** The tiles this conveyor hands its items to; a buried entrance hands them to its run's exit. */
private fun ConveyorEntity.outlets(index: LayoutIndex): List<Coordinate> {
    val step = direction.step
    return when (this) {
        is UndergroundBeltEntity ->
            if (io == Io.INPUT) {
                val output = findUndergroundOutput(index, this) ?: return emptyList()
                listOf(output.area.topLeft + step)
            } else {
                listOf(area.topLeft + step)
            }
        else -> area.map { it + step }
    }
}

/** One closing belt circle: the material it carries and the first tile it covers. */
private class BeltLoop(val material: Material, val tile: Coordinate)

/**
 * Every belt circle, reported once by its north-west tile. A circle only exists where a route hands
 * items back to a belt it already fed: a produced lane whose dead end points into its own trunk.
 *
 * The check trims the graph twice, once forwards and once backwards, and keeps what both trims
 * leave: a tile on a circle never becomes sourceless in either direction, while a belt merely
 * downstream of a circle loses its reverse source and is not reported.
 */
private fun beltLoops(conveyors: List<ConveyorEntity>, index: LayoutIndex): List<BeltLoop> {
    if (conveyors.isEmpty()) return emptyList()
    val nodeAt = HashMap<Coordinate, Int>()
    conveyors.forEachIndexed { node, conveyor -> conveyor.area.forEach { nodeAt[it] = node } }
    val edges = conveyors.map { conveyor ->
        conveyor
            .outlets(index)
            .mapNotNull { outlet ->
                val node = nodeAt[outlet] ?: return@mapNotNull null
                val target = conveyors[node]
                // a splitter only takes items on its back face, so a belt that runs into its
                // side is not part of a flow circle
                if (target is SplitterEntity && target.direction != conveyor.direction) {
                    return@mapNotNull null
                }
                node
            }
            .distinct()
    }
    val circles = trimAcyclic(edges) intersect trimAcyclic(reversed(edges))
    if (circles.isEmpty()) return emptyList()
    return circleGroups(circles, edges)
        .map { group ->
            val tiles = group.flatMap { conveyors[it].area }
            val tile = tiles.minWith(compareBy({ it.x }, { it.y }))
            BeltLoop(conveyors[group.first { tile in conveyors[it].area }].material, tile)
        }
        .sortedWith(compareBy({ it.tile.y }, { it.tile.x }))
}

/**
 * The nodes a forward or backward sweep cannot remove: with no sourceless node left to peel, a
 * circle and everything its edges can reach stays behind. Running both directions narrows that to
 * the circles themselves.
 */
private fun trimAcyclic(edges: List<List<Int>>): Set<Int> {
    val remaining = IntArray(edges.size)
    for (successors in edges) for (successor in successors) remaining[successor]++
    val queue = ArrayDeque<Int>()
    for (node in edges.indices) if (remaining[node] == 0) queue.add(node)
    val trimmed = BooleanArray(edges.size)
    while (queue.isNotEmpty()) {
        val node = queue.removeFirst()
        if (trimmed[node]) continue
        trimmed[node] = true
        for (successor in edges[node]) {
            if (--remaining[successor] == 0) queue.add(successor)
        }
    }
    return edges.indices.filterTo(mutableSetOf()) { !trimmed[it] }
}

private fun reversed(edges: List<List<Int>>): List<List<Int>> {
    val reversed = List(edges.size) { mutableListOf<Int>() }
    edges.forEachIndexed { from, successors -> successors.forEach { reversed[it] += from } }
    return reversed
}

/** Splits the circle nodes into the separate belts that touch each other. */
private fun circleGroups(circles: Set<Int>, edges: List<List<Int>>): List<List<Int>> {
    val adjacent = HashMap<Int, MutableSet<Int>>()
    for (from in circles) {
        for (to in edges[from]) {
            if (to !in circles) continue
            adjacent.getOrPut(from) { mutableSetOf() } += to
            adjacent.getOrPut(to) { mutableSetOf() } += from
        }
    }
    val seen = mutableSetOf<Int>()
    val groups = mutableListOf<List<Int>>()
    for (start in circles) {
        if (!seen.add(start)) continue
        val group = mutableListOf<Int>()
        val queue = ArrayDeque<Int>()
        queue.add(start)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            group += node
            for (next in adjacent[node].orEmpty()) {
                if (seen.add(next)) queue.add(next)
            }
        }
        groups += group
    }
    return groups
}
