package de.bjrke.factorio.optimizer.common.planning

import de.bjrke.factorio.optimizer.common.model.Coordinate
import kotlin.math.abs

/** The shortest a vertical shaft can be: every run has at least one tile between its ends. */
private const val SHAFT_MIN_REACH = 2

/** Which side of a row a lane serves: the feed above its machines, or the drain below them. */
internal enum class LineSide {
    NORTH,
    SOUTH,
}

/**
 * One fluid lane of a row **beyond its belts**, as a shape decided without the board so it can be
 * tested alone. [ports] are the machine port tiles, left to right; every port is reached by a
 * **vertical** shaft that runs from just inside the [laneRow] down to the port tile.
 *
 * The lane row is a chain of **plain junction pipes**, one above every port, joined by a
 * **horizontal pipe-to-ground run** across each gap. The run fills the gap between two junctions:
 * with the two tiles of a touching-machine gap its two ends stand next to each other (`pUUp`), and
 * a wider gap keeps the tiles between them buried. A run's ends and the tiles it buries are what
 * the player can walk over, which is why even a two-tile gap gets a run instead of a surface pipe.
 *
 * A pipe-to-ground end connects on the side its mouth faces, and the two ends of a run face away
 * from each other: the end beside a junction faces that junction, and a shaft's top end faces the
 * lane above it while its bottom end faces the machine below it. All run ends are stored with the
 * run's travel direction (east for a north lane, west for a south lane, south for every shaft); the
 * blueprint writer flips the entrance so each mouth faces the surface pipe it serves.
 */
internal class FluidLane
private constructor(
    /** The lane row: the pipe line's own row, two rows beyond the outermost belt. */
    val laneRow: Int,
    /** The machine port tiles the lane feeds (or drains), left to right. */
    val ports: List<Coordinate>,
    /** The plain pipes of the lane row: one junction per port, west to east. */
    val surface: List<Coordinate>,
    /** The horizontal runs between neighbouring junctions, input end first. */
    val runs: List<Pair<Coordinate, Coordinate>>,
    /** The shafts, north end first; both run south, so [shafts] pairs (top, bottom). */
    val shafts: List<Pair<Coordinate, Coordinate>>,
) {
    /** The plain pipe in the marker's column: where the lane receives (or gives) its fluid. */
    val head
        get() = surface.first()

    init {
        require(ports.isNotEmpty()) { "a fluid lane needs at least one port" }
        check(surface.isNotEmpty() && surface.all { it.y == laneRow }) {
            "the lane of $ports leaves row $laneRow"
        }
        check(head.x == ports.first().x) { "the head of $ports is not above its first port" }
        // every tile from the first to the last junction belongs to the lane exactly once: as a
        // junction, as a run end, or as a tile a run passes under
        val covered =
            (surface +
                    runs.flatMap { (from, to) ->
                        (minOf(from.x, to.x)..maxOf(from.x, to.x)).map { x ->
                            Coordinate(x, laneRow)
                        }
                    })
                .map { it.x }
        check(covered.sorted() == (ports.first().x..ports.last().x).toList()) {
            "the lane of $ports does not cover its span once: $covered"
        }
        check(runs.all { (from, to) -> from.y == laneRow && to.y == laneRow }) {
            "a run of $ports leaves row $laneRow"
        }
        check(runs.all { (from, to) -> from.x != to.x }) { "a run of $ports has no length" }
        val shaftEnds = shafts.flatMap { (top, bottom) -> listOf(top, bottom) }
        check(shaftEnds.distinct().size == shaftEnds.size) {
            "a shaft of $ports doubles up: $shafts"
        }
        check(
            shafts.all { (top, bottom) ->
                minOf(abs(top.y - laneRow), abs(bottom.y - laneRow)) == 1
            },
        ) {
            "a shaft of $ports does not start one row beside its lane: $shafts"
        }
        check(
            shafts.all { (top, bottom) ->
                top.x == bottom.x && abs(bottom.y - top.y) >= SHAFT_MIN_REACH
            },
        ) {
            "a shaft of $ports is not a vertical run: $shafts"
        }
        val reached = shafts.flatMap { (top, bottom) -> listOf(top, bottom) }
        check(ports.all { port -> port in reached }) { "a port of $ports is not reached" }
    }

    companion object {
        /**
         * The lane at [laneRow] for [ports] (sorted left to right) on [side]. Every port gets a
         * junction pipe and a vertical shaft in its own column; the gap to the next port becomes a
         * run when it holds two tiles or more, and stays a plain pipe when it holds one.
         */
        fun of(laneRow: Int, ports: List<Coordinate>, side: LineSide): FluidLane {
            require(ports.zipWithNext().all { (from, to) -> from.x < to.x }) {
                "the ports of the lane must run left to right"
            }
            val surface = mutableListOf<Coordinate>()
            val runs = mutableListOf<Pair<Coordinate, Coordinate>>()
            for ((index, port) in ports.withIndex()) {
                surface += Coordinate(port.x, laneRow)
                val next = ports.getOrNull(index + 1) ?: continue
                val gap = (port.x + 1 until next.x).map { Coordinate(it, laneRow) }
                when (gap.size) {
                    0 -> Unit
                    // one tile is too short for a run: it stays a plain pipe
                    1 -> surface += gap.single()
                    // the ends fill the gap; the tiles between them are passed under
                    else -> {
                        val west = gap.first()
                        val east = gap.last()
                        runs +=
                            when (side) {
                                LineSide.NORTH -> west to east
                                LineSide.SOUTH -> east to west
                            }
                    }
                }
            }
            val shafts = ports.map { port ->
                // the end beside the lane faces it; the other one lands on the port
                when (side) {
                    LineSide.NORTH -> Coordinate(port.x, laneRow + 1) to port
                    LineSide.SOUTH -> port to Coordinate(port.x, laneRow - 1)
                }
            }
            return FluidLane(
                laneRow,
                ports,
                surface.sortedBy { it.x },
                runs.sortedBy { (from, _) -> from.x },
                shafts,
            )
        }
    }
}
