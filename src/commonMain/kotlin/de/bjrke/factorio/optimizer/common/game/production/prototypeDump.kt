package de.bjrke.factorio.optimizer.common.game.production

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** The `data.raw` dump (JSON) for the base game or for base plus the Space Age DLC mods. */
internal expect fun loadPrototypeDump(spaceAge: Boolean): String

/**
 * One immutable `data.raw` dump. The JSON tree is decoded once and shared, so building a
 * `ParsedRecipe` for a new universe does not read or decode the file again.
 *
 * The dump carries no materials, so it is safe to share between universes; a parsed recipe is not,
 * because its material keys belong to exactly one registry. [load] is the process-wide facade;
 * `recipeParser.kt` builds a fresh result in a caller-owned universe.
 */
internal class PrototypeDump(val root: JsonObject) {

    /**
     * The `data.raw.fluid` keys. A registry built from them marks those materials as fluids, so
     * this is the input that fixes every material's fluid flag for a universe.
     */
    val fluidNames: Set<String> = root.table("fluid").keys

    companion object {

        private val base by lazy { of(loadPrototypeDump(spaceAge = false)) }

        private val spaceAge by lazy { of(loadPrototypeDump(spaceAge = true)) }

        /** The shipped dump, decoded once per process. */
        fun load(spaceAge: Boolean): PrototypeDump = if (spaceAge) this.spaceAge else base

        /** Decodes [json]; a root that is not an object is not a dump and fails. */
        internal fun of(json: String) =
            PrototypeDump(
                Json.parseToJsonElement(json) as? JsonObject
                    ?: error("the prototype dump is not a JSON object"),
            )
    }
}
