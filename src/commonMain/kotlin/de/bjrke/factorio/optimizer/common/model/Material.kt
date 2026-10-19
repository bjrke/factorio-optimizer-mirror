package de.bjrke.factorio.optimizer.common.model

/** A material (item or fluid) by its Factorio prototype name, interned per name. */
@ConsistentCopyVisibility
data class Material private constructor(val name: String) {

    companion object {
        private val cache = mutableMapOf<String, Material>()

        operator fun invoke(name: String) = cache.getOrPut(name) { Material(name) }
    }
}
