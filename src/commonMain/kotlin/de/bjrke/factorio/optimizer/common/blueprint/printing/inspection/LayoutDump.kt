package de.bjrke.factorio.optimizer.common.blueprint.printing.inspection

import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.LayoutEntity

/** A built layout plus the feed routes that could not be placed, for the CLI's text rendering. */
internal data class LayoutDump(val entities: List<LayoutEntity>, val failures: List<String>)
