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

    /** Whether [index] addresses a present slot; a sparse map is false for its holes. */
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
 * A mutable membership set of [RecipeIndex] slots, one byte each, sized once to a result's array
 * size so a lookup is a byte read with no hashing.
 *
 * A slot answers false until it is marked. [set] marks or clears it, and [add] marks it and reports
 * whether it was unset before, so a caller can stop on a repeat.
 *
 * It is deliberately not a [ProductionMap]: that contract lists only present values and throws on a
 * hole, while a membership set must make every slot addressable.
 */
@JvmInline
internal value class IndexSet private constructor(private val content: ByteArray) {

    constructor(size: Int) : this(ByteArray(size))

    operator fun contains(index: RecipeIndex) = content[index.value] != FALSE

    operator fun set(index: RecipeIndex, value: Boolean) {
        content[index.value] = if (value) TRUE else FALSE
    }

    /** Marks [index] and answers whether it was unset before, so a walk can stop on a repeat. */
    fun add(index: RecipeIndex): Boolean {
        val result = content[index.value] == FALSE
        content[index.value] = TRUE
        return result
    }

    companion object {
        private const val FALSE: Byte = 0
        private const val TRUE: Byte = Byte.MIN_VALUE
    }
}

/**
 * Dense per-recipe values for the band search: one slot per [RecipeIndex], read and written without
 * hashing. A slot that was never written answers 0.
 */
@JvmInline
internal value class ProductionIntMap private constructor(private val content: IntArray) {

    constructor(size: Int) : this(IntArray(size))

    operator fun get(index: RecipeIndex) = content[index.value]

    operator fun set(index: RecipeIndex, value: Int) {
        content[index.value] = value
    }
}

/**
 * Dense per-recipe rates for the band search: one slot per [RecipeIndex], read and written without
 * hashing. A slot that was never written answers 0.0.
 */
@JvmInline
internal value class ProductionDoubleMap private constructor(private val content: DoubleArray) {

    constructor(size: Int) : this(DoubleArray(size))

    operator fun get(index: RecipeIndex) = content[index.value]

    operator fun set(index: RecipeIndex, value: Double) {
        content[index.value] = value
    }
}
