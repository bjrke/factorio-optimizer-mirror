package de.bjrke.factorio.optimizer.common.core.model

/** A material (item or fluid) by its Factorio prototype name, interned per name. */
@ConsistentCopyVisibility
data class Material private constructor(val name: String, val prototypeName: String) {

    companion object {
        private val cache = mutableMapOf<String, Material>()

        /**
         * The interned material for [name]. A pseudo plan material passes the real [prototypeName]
         * it stands for; [Material.prototypeName] defaults to [name] for every other material.
         */
        operator fun invoke(name: String, prototypeName: String = name) =
            cache
                .getOrPut(name) { Material(name, prototypeName) }
                .also { require(it.prototypeName == prototypeName) }
    }

    override fun toString() =
        if (name == prototypeName) "Material(name=$name)"
        else "Material(name=$name, prototypeName=$prototypeName)"
}
