package de.bjrke.factorio.optimizer.common.business

/**
 * Renders a [dump] as one character per tile so a layout can be eyeballed in a terminal: machines
 * are digits, belts show the code of the material they carry, and every other entity has its own
 * glyph. A legend decodes the codes and the failures are listed below the grid.
 *
 * The grid comes from [asciiBoard] and the explanations from [asciiLegend]; this function only
 * arranges them in the reported order.
 */
internal fun layoutAscii(dump: LayoutDump): String {
    if (dump.entities.isEmpty()) return "layout: empty\n"
    val board = asciiBoard(dump.entities)
    return buildString {
        append(board.header)
        append(board.grid)
        append("\nlegend\n")
        append(asciiLegend(dump.entities))
        if (dump.failures.isNotEmpty()) {
            append("\nfailures (${dump.failures.size})\n")
            dump.failures.forEach { append("  $it\n") }
        }
    }
}
