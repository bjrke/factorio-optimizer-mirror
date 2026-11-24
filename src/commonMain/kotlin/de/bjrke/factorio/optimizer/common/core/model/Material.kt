package de.bjrke.factorio.optimizer.common.core.model

/**
 * Marks the only way to create a [Material]: the factory its run's registry calls.
 *
 * Kotlin has no friend visibility, so `internal` alone would let any file in the module construct a
 * second instance of a name, with a duplicate registry slot and a different identity. The opt-in
 * makes such a call a compile error instead; only `MaterialRegistry` opts in.
 */
@RequiresOptIn(message = "Materials come from a run's MaterialRegistry; create one only there.")
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION)
internal annotation class MaterialCreation

/**
 * A material (item or fluid) by its Factorio prototype name, interned by its run's registry.
 *
 * The registry assigns [index] and [isFluid] at creation. Equality is reference equality: the
 * registry returns one instance per name, so `==` is a pointer compare and [index] is the hash.
 * That keeps the many material-keyed maps and sets cheap without changing their type.
 *
 * The constructor is private so [copy] stays private too: a copy would be a second instance of a
 * name, which the registry's identity-based equality cannot account for. [interned] exists only to
 * give the registry a way in.
 */
@ConsistentCopyVisibility
data class Material
private constructor(
    val name: String,
    val prototypeName: String,
    val index: Int,
    val isFluid: Boolean,
) {

    override fun equals(other: Any?) = this === other

    override fun hashCode() = index

    companion object {

        /** Creates the material [MaterialRegistry] interns for a name; no other caller opts in. */
        @MaterialCreation
        internal fun interned(
            name: String,
            prototypeName: String,
            index: Int,
            isFluid: Boolean,
        ) = Material(name, prototypeName, index, isFluid)
    }

    override fun toString() =
        if (name == prototypeName) "Material(name=$name)"
        else "Material(name=$name, prototypeName=$prototypeName)"
}
