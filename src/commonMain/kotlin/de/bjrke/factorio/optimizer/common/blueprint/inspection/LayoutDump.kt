package de.bjrke.factorio.optimizer.common.blueprint.inspection

import de.bjrke.factorio.optimizer.common.layout.entity.LayoutEntity

/** A built layout plus the feed routes that could not be placed, for the CLI's text rendering. */
internal data class LayoutDump(val entities: List<LayoutEntity>, val failures: List<String>)
