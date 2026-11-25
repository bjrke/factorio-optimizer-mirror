package de.bjrke.factorio.optimizer.common.model

import kotlin.jvm.JvmInline

/**
 * A result's productions addressed by their dense [RecipeIndex].
 *
 * The array an [OptimizationResult] owns and the caches the band search builds share this shape: a
 * lookup addresses the slot directly instead of hashing a recipe. [get] answers null for an index
 * the map does not cover, so probing a slot from another result cannot crash; [getValue] is the
 * throwing form for a slot the caller knows is present.
 */
interface ProductionMap<T> {

    /** The value at [index], or null when the map covers no slot there. */
    operator fun get(index: RecipeIndex): T?

    /** The value at [index]; throws [NoSuchElementException] when the map covers no slot there. */
    fun getValue(index: RecipeIndex): T

    /** The present values in index order. */
    val values: Collection<T>

    /** The number of addressable slots: every index below it is a valid lookup. */
    val arraySize: Int

    operator fun contains(index: RecipeIndex): Boolean
}

/**
 * The [ProductionMap] of one [OptimizationResult]: one production per minted [RecipeIndex], in
 * production order, so a lookup is a bounds-checked array read and [values] keeps the optimizer's
 * order.
 */
@JvmInline
value class FullProductionMap<T>(private val content: Array<T>) : ProductionMap<T> {

    override operator fun get(index: RecipeIndex) = content.getOrNull(index.value)

    override fun getValue(index: RecipeIndex): T =
        content.getOrNull(index.value) ?: throw NoSuchElementException("no value at $index")

    override val values
        get() = content.asList()

    override val arraySize: Int
        get() = content.size

    override operator fun contains(index: RecipeIndex) =
        index.value >= 0 && index.value < content.size
}

/**
 * A [ProductionMap] with holes, one array slot per [RecipeIndex]. The mutable form caches lookups
 * during a search; the block factory returns it as the read-only [ProductionMap] type, so no caller
 * can write to it after construction.
 */
@JvmInline
internal value class SparseProductionMap<T> private constructor(private val content: Array<T?>) :
    ProductionMap<T> {

    override operator fun get(index: RecipeIndex) = content.getOrNull(index.value)

    override fun getValue(index: RecipeIndex): T =
        content.getOrNull(index.value) ?: throw NoSuchElementException("no value at $index")

    override val values
        get() = content.filterNotNull()

    override val arraySize: Int
        get() = content.size

    override operator fun contains(index: RecipeIndex) = content.getOrNull(index.value) != null

    operator fun set(index: RecipeIndex, value: T?) {
        content[index.value] = value
    }

    companion object {

        internal inline operator fun <reified O> invoke(size: Int) =
            SparseProductionMap<O>(arrayOfNulls(size))

        internal inline operator fun <reified O> invoke(
            arraySize: Int,
            block: SparseProductionMap<O>.() -> Unit,
        ): ProductionMap<O> = SparseProductionMap<O>(arraySize).also { it.block() }
    }
}

/**
 * A mutable set of [RecipeIndex] slots, one byte each, for the band search's assignment
 * bookkeeping.
 *
 * It is deliberately not a [ProductionMap]: that contract lists only present values and throws on a
 * hole, while every slot of this set is addressable and simply answers false.
 */
@JvmInline
internal value class IndexSet private constructor(private val content: ByteArray) {

    constructor(size: Int) : this(ByteArray(size))

    operator fun contains(index: RecipeIndex) = content[index.value] != FALSE

    operator fun set(index: RecipeIndex, value: Boolean) {
        content[index.value] = if (value) TRUE else FALSE
    }

    companion object {
        private const val FALSE: Byte = 0
        private const val TRUE: Byte = Byte.MIN_VALUE
    }
}
