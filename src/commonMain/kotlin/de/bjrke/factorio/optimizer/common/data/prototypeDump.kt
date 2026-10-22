package de.bjrke.factorio.optimizer.common.data

/** The `data.raw` dump (JSON) for the base game or for base plus the Space Age DLC mods. */
internal expect fun loadPrototypeDump(spaceAge: Boolean): String
