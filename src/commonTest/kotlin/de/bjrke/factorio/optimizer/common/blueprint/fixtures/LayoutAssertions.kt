package de.bjrke.factorio.optimizer.common.blueprint.fixtures

import de.bjrke.factorio.optimizer.common.blueprint.model.BlueprintEntity
import de.bjrke.factorio.optimizer.common.layout.entity.ConveyorEntity
import de.bjrke.factorio.optimizer.common.layout.entity.LayoutEntity
import de.bjrke.factorio.optimizer.common.layout.entity.TransportEntity
import de.bjrke.factorio.optimizer.common.layout.entity.UndergroundEntity
import de.bjrke.factorio.optimizer.common.model.Area
import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Direction
import de.bjrke.factorio.optimizer.common.model.Io
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.area
import de.bjrke.factorio.optimizer.common.model.manhattanDist
import kotlin.test.assertEquals

/**
 * A tile the underground-run assertion understands, whichever source it came from: the blueprint
 * DTO (whose belts carry no material, so the caller supplies it) or the placed layout entities.
 */
private data class RunTile(
    val name: String,
    val area: Area,
    val direction: Direction?,
    val type: Io?,
    val material: Material?,
)

/** Asserts on the blueprint DTO; [materials] supplies the material carried on each belt tile. */
fun assertUndergroundRunsAreStretched(
    entities: List<BlueprintEntity>,
    materials: Map<Coordinate, Material>,
) = assertRunsStretched(entities.map { it.runTile(materials) })

/** Asserts on the placed entities of a layout dump, which carry their own material. */
fun assertUndergroundRunsAreStretched(entities: List<LayoutEntity>) =
    assertRunsStretched(entities.map { it.runTile() })

private fun BlueprintEntity.runTile(materials: Map<Coordinate, Material>) =
    RunTile(name, area, direction, type, materials[area.topLeft])

private fun LayoutEntity.runTile() =
    RunTile(
        name = name,
        area = area,
        direction = (this as? ConveyorEntity)?.direction ?: (this as? UndergroundEntity)?.direction,
        type = (this as? UndergroundEntity)?.io,
        material = (this as? TransportEntity)?.material,
    )

/**
 * A run shorter than the belt tier's reach must not still have a belt of its own material to
 * swallow on either end: the stretcher grows it as far as the straight run allows.
 */
private fun assertRunsStretched(tiles: List<RunTile>) {
    val byTile = tiles.associateBy { it.area.topLeft }
    val inputs = tiles.filter { it.type == Io.INPUT && it.name.endsWith("underground-belt") }
    val problems =
        inputs.filterNot { tiles.isImportConnector(it) }.flatMap { runProblems(it, byTile) }
    assertEquals(emptyList(), problems.take(5))
}

private fun runProblems(input: RunTile, byTile: Map<Coordinate, RunTile>): List<String> {
    val direction = input.direction
    val limit = undergroundRange(input.name)
    val output = if (direction == null) null else partner(input, byTile, direction.step, limit)
    return if (direction == null || output == null || output.first >= limit) emptyList()
    else
        listOfNotNull(
            backProblem(input, byTile, direction),
            forwardProblem(output.second, byTile, direction),
        )
}

/**
 * The partner of [input] ahead and how far it sits; a same-column run of another material is
 * skipped.
 */
private fun partner(
    input: RunTile,
    byTile: Map<Coordinate, RunTile>,
    step: Coordinate,
    limit: Int,
): Pair<Int, RunTile>? {
    for (distance in 2..limit) {
        val candidate = byTile[input.area.topLeft + step * distance] ?: continue
        // the partner carries the same material: another run may tunnel in the same column
        val sameRun =
            candidate.type == Io.OUTPUT &&
                candidate.name == input.name &&
                candidate.material == input.material
        if (sameRun) return distance to candidate
    }
    return null
}

private fun backProblem(
    input: RunTile,
    byTile: Map<Coordinate, RunTile>,
    direction: Direction,
): String? {
    val back = input.area.topLeft - direction.step
    val beyond = byTile[back - direction.step]
    // the new end must still join a surface belt, not the next run's underground end
    val grows =
        byTile[back]?.plainBelt(direction, input.material) == true &&
            beyond != null &&
            !beyond.name.endsWith("underground-belt")
    return if (grows) "input ${input.area.topLeft} can still grow back" else null
}

private fun forwardProblem(
    output: RunTile,
    byTile: Map<Coordinate, RunTile>,
    direction: Direction,
): String? {
    val forward = output.area.topLeft + direction.step
    val beyond = byTile[forward + direction.step]
    val grows =
        byTile[forward]?.plainBelt(direction, output.material) == true &&
            beyond != null &&
            !beyond.name.endsWith("underground-belt")
    return if (grows) "output ${output.area.topLeft} can still grow forward" else null
}

private fun RunTile.plainBelt(direction: Direction, material: Material?) =
    name.endsWith("transport-belt") && this.direction == direction && this.material == material

private fun List<RunTile>.isImportConnector(entity: RunTile) = any {
    (it.name == "constant-combinator" ||
        it.name.endsWith("chest") ||
        it.name.endsWith("inserter")) && it.area.topLeft.manhattanDist(entity.area.topLeft) == 1
}

private fun undergroundRange(name: String) =
    when {
        name.contains("turbo") -> 11
        name.contains("express") -> 9
        name.contains("fast") -> 7
        else -> 5
    }
