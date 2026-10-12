package de.bjrke.factorio.optimizer.common.business

const val BASE_MOD = "base"
const val SPACE_AGE_MOD = "space-age"

val defaultMods = setOf(BASE_MOD, SPACE_AGE_MOD)

/**
 * The mods the UI offers. Data is dumped for exactly two configurations (see `dumpPrototypes`), so
 * selecting Space Age switches to the dump with the DLC mods enabled and everything else uses the
 * base game dump.
 */
val selectableMods = listOf(BASE_MOD, SPACE_AGE_MOD)

/** Adds the mods implied by a selection: Space Age always needs the base game. */
fun activeMods(selected: Set<String>) =
    if (SPACE_AGE_MOD in selected) setOf(BASE_MOD, SPACE_AGE_MOD) else setOf(BASE_MOD)

/** The `data.raw` dump (JSON) for the base game or for base plus the Space Age DLC mods. */
expect fun loadPrototypeDump(spaceAge: Boolean): String
