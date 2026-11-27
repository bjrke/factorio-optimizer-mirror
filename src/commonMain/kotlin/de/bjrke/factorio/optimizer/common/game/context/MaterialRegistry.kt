package de.bjrke.factorio.optimizer.common.game.context

import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.core.model.MaterialCreation

/**
 * The materials of one universe, interned by name and numbered densely from zero.
 *
 * A material's [Material.index] is its slot here and its identity: equality is reference equality,
 * so a registry must be the only creator of the materials a run uses. Mixing two registries makes
 * two materials of the same name unequal.
 *
 * [size] is the number of used slots and therefore the next free index, so a caller can size an
 * array by material index. [fluidNames] is fixed at construction because [Material.isFluid] is
 * immutable: production and the test games seed the dump's real `data.raw.fluid` keys, and only a
 * universe that genuinely has no fluids passes `emptySet()`.
 */
class MaterialRegistry(private val fluidNames: Set<String>) {
    private val cache = mutableMapOf<String, Material>()
    private val byIndex = mutableListOf<Material>()

    /** The number of interned materials; also the next free index. */
    val size: Int
        get() = byIndex.size

    /**
     * The interned material for [name]. A pseudo plan material passes the real [prototypeName] it
     * stands for; [Material.prototypeName] defaults to [name] for every other material.
     *
     * The only place that opts into [MaterialCreation]: a construction anywhere else is a compile
     * error, so a run cannot hold two instances of a name.
     */
    @OptIn(MaterialCreation::class)
    fun Material(name: String, prototypeName: String = name): Material {
        cache[name]?.let { material ->
            require(material.prototypeName == prototypeName) {
                "$name already stands for ${material.prototypeName}, not $prototypeName"
            }
            return material
        }
        return Material.interned(name, prototypeName, byIndex.size, name in fluidNames).also {
            cache[name] = it
            byIndex += it
        }
    }

    /** The material at [index], the inverse of [Material.index]. */
    fun atIndex(index: Int): Material = byIndex[index]

    /**
     * The interned material for [name], or null when this universe does not have it. Unlike
     * [Material], it never creates or numbers a material, so a name decoded from a URL cannot grow
     * the registry.
     */
    fun existing(name: String): Material? = cache[name]
}
