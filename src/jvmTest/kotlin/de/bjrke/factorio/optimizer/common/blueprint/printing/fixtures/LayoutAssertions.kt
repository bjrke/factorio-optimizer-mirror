package de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures

import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.ConveyorEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.LayoutEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.TransportEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.UndergroundEntity
import de.bjrke.factorio.optimizer.common.blueprint.printing.model.BlueprintEntity
import de.bjrke.factorio.optimizer.common.core.model.Area
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.Direction
import de.bjrke.factorio.optimizer.common.core.model.Io
import de.bjrke.factorio.optimizer.common.core.model.Material
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
    range: Int,
) = assertRunsStretched(entities.map { it.runTile(materials) }, range)

/** Asserts on the placed entities of a layout dump, which carry their own material. */
fun assertUndergroundRunsAreStretched(entities: List<LayoutEntity>, range: Int) =
    assertRunsStretched(entities.map { it.runTile() }, range)

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
private fun assertRunsStretched(tiles: List<RunTile>, range: Int) {
    val byTile = tiles.associateBy { it.area.topLeft }
    val protected = tiles.inserterTiles()
    val inputs = tiles.filter { it.type == Io.INPUT && it.name.endsWith("underground-belt") }
    val problems =
        inputs
            .filterNot { tiles.isImportConnector(it) }
            .flatMap { runProblems(it, byTile, protected, range) }
    assertEquals(emptyList(), problems.take(5))
}

/** The tiles an inserter takes from or drops onto; a run may not swallow them. */
private fun List<RunTile>.inserterTiles() = buildSet {
    for (inserter in this@inserterTiles.filter { it.name.endsWith("inserter") }) {
        val direction = inserter.direction ?: continue
        val reach = if (inserter.name == "long-handed-inserter") 2 else 1
        for (distance in 1..reach) {
            add(inserter.area.topLeft + direction.step * distance)
            add(inserter.area.topLeft - direction.step * distance)
        }
    }
}

private fun runProblems(
    input: RunTile,
    byTile: Map<Coordinate, RunTile>,
    protected: Set<Coordinate>,
    range: Int,
): List<String> {
    val direction = input.direction
    val limit = range
    val output = if (direction == null) null else partner(input, byTile, direction.step, limit)
    return if (direction == null || output == null || output.first >= limit) emptyList()
    else
        listOfNotNull(
            backProblem(input, byTile, direction, protected),
            forwardProblem(output.second, byTile, direction, protected),
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
    protected: Set<Coordinate>,
): String? {
    val back = input.area.topLeft - direction.step
    if (back in protected) return null
    val beyond = byTile[back - direction.step]
    // the new end must still join a surface belt, not the next run's underground end, and the
    // belt past the swallowed tile must keep feeding the run
    val grows =
        byTile[back]?.plainBelt(direction, input.material) == true &&
            beyond?.plainBelt(direction, input.material) == true
    return if (grows) "input ${input.area.topLeft} can still grow back" else null
}

private fun forwardProblem(
    output: RunTile,
    byTile: Map<Coordinate, RunTile>,
    direction: Direction,
    protected: Set<Coordinate>,
): String? {
    val forward = output.area.topLeft + direction.step
    if (forward in protected) return null
    val beyond = byTile[forward + direction.step]
    val grows =
        byTile[forward]?.plainBelt(direction, output.material) == true &&
            beyond?.plainBelt(direction, output.material) == true
    return if (grows) "output ${output.area.topLeft} can still grow forward" else null
}

private fun RunTile.plainBelt(direction: Direction, material: Material?) =
    name.endsWith("transport-belt") && this.direction == direction && this.material == material

private fun List<RunTile>.isImportConnector(entity: RunTile): Boolean {
    val direction = entity.direction ?: return false
    val type = entity.type ?: return false
    val step = direction.step
    val sign = if (type == Io.INPUT) 1 else -1
    return listOf(2, 4).any { distance ->
        val partner =
            firstOrNull { it.area.topLeft == entity.area.topLeft + step * distance * sign }
                ?: return@any false
        if (
            !partner.name.endsWith("underground-belt") ||
                partner.type == type ||
                partner.direction != direction
        ) {
            return@any false
        }
        val middle = firstOrNull {
            it.area.topLeft == entity.area.topLeft + step * (distance / 2) * sign
        }
        middle != null && middle.name.isConnectorFurniture()
    }
}

private fun String.isConnectorFurniture() =
    this == "constant-combinator" || endsWith("chest") || endsWith("inserter") || endsWith("loader")
