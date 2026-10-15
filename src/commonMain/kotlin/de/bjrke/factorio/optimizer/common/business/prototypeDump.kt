package de.bjrke.factorio.optimizer.common.business

/** The `data.raw` dump (JSON) for the base game or for base plus the Space Age DLC mods. */
expect fun loadPrototypeDump(spaceAge: Boolean): String
