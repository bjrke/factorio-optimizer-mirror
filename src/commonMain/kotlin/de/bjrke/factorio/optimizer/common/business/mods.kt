package de.bjrke.factorio.optimizer.common.business

const val BASE_MOD = "base"
const val SPACE_AGE_MOD = "space-age"

val defaultMods = setOf(BASE_MOD, SPACE_AGE_MOD)

/** Adds the mods implied by a selection: Space Age always needs the base game. */
fun activeMods(selected: Set<String>) =
    if (SPACE_AGE_MOD in selected) setOf(BASE_MOD, SPACE_AGE_MOD) else setOf(BASE_MOD)

/** The `data.raw` dump (JSON) for the base game or for base plus the Space Age DLC mods. */
expect fun loadPrototypeDump(spaceAge: Boolean): String
