package de.bjrke.factorio.optimizer.common.model

/**
 * Belt and inserter throughput prototypes parsed from the dump. This is the [MachineStats]
 * counterpart for logistics; there are no fallbacks, a prototype absent from the dump is simply not
 * usable.
 *
 * [belts] maps a belt prototype name to its speed in tiles per tick, [inserters] maps an inserter
 * name to its stats, [pumpRate] is the fastest pump's throughput in fluid per second and [fluids]
 * holds the materials that are fluids (which move in pipes, not on belts).
 */
data class Logistics(
    val belts: Map<String, Double> = emptyMap(),
    val inserters: Map<String, InserterStats> = emptyMap(),
    val pumpRate: Double? = null,
    val fluids: Set<Material> = emptySet(),
)

/**
 * Throughput-relevant stats of one inserter prototype.
 *
 * [minStack] is the hand size before any inserter capacity bonus research (2 for a bulk inserter, 6
 * for the stack inserter); [maxStack] is the hand size once all of that research is done. A bulk or
 * stack inserter therefore always carries several items per swing, whether or not the user picked a
 * lower number.
 */
data class InserterStats(
    val rotationSpeed: Double,
    val bulk: Boolean,
    val minStack: Int,
    val maxStack: Int,
    /**
     * Whether the prototype has a `max_belt_stack_size`; only the stack inserter can stack belts.
     */
    val stacksOnBelt: Boolean,
) {
    /** The items per hand to use for [requested], clamped to what this inserter can carry. */
    fun stackSize(requested: Int) = requested.coerceIn(minStack, maxStack)
}

/** A belt tier. The speed comes from the dump; a tier without a prototype is not selectable. */
enum class Belt(val code: Char, val identifier: String, val spaceAgeOnly: Boolean = false) {
    TRANSPORT_BELT('t', "transport-belt"),
    FAST_TRANSPORT_BELT('f', "fast-transport-belt"),
    EXPRESS_TRANSPORT_BELT('e', "express-transport-belt"),
    TURBO_TRANSPORT_BELT('u', "turbo-transport-belt", spaceAgeOnly = true);

    companion object {
        /** Items per belt lane per tile. A belt has [MAX_LANES] lanes. */
        const val ITEMS_PER_LANE = 4.0

        const val MAX_LANES = 2

        /** Belt stacking caps at four items per slot. */
        const val MAX_STACK = 4

        private val byCode = entries.associateBy { it.code }

        fun fromCode(code: Char) = byCode[code]
    }
}

/**
 * An inserter tier. The rotation speed and stack capacity come from the dump; a tier without a
 * prototype is not selectable.
 */
enum class Inserter(val code: Char, val identifier: String, val spaceAgeOnly: Boolean = false) {
    BURNER_INSERTER('b', "burner-inserter"),
    INSERTER('i', "inserter"),
    LONG_HANDED_INSERTER('l', "long-handed-inserter"),
    FAST_INSERTER('f', "fast-inserter"),
    BULK_INSERTER('B', "bulk-inserter"),
    STACK_INSERTER('s', "stack-inserter", spaceAgeOnly = true);

    companion object {
        private val byCode = entries.associateBy { it.code }

        fun fromCode(code: Char) = byCode[code]
    }
}

/**
 * The belts and inserters that can be selected for a given mod set and dump: a prototype missing
 * from [logistics] is dropped. Mirrors [FactoryConfig] for the production machines.
 */
class LogisticsConfig(val logistics: Logistics, val spaceAge: Boolean = true) {

    val availableBelts: List<Belt> =
        Belt.entries.filter { (!it.spaceAgeOnly || spaceAge) && it.identifier in logistics.belts }

    val availableInserters: List<Inserter> =
        Inserter.entries.filter {
            (!it.spaceAgeOnly || spaceAge) && it.identifier in logistics.inserters
        }

    /** The fastest available tier. */
    val defaultBelt: Belt = availableBelts.last()

    val defaultInserter: Inserter = availableInserters.last()

    /** The items per hand [inserter] always carries, from its prototype. */
    fun minStack(inserter: Inserter) = logistics.inserters.getValue(inserter.identifier).minStack

    /** The items per hand [inserter] can reach with all capacity research, from its prototype. */
    fun maxStack(inserter: Inserter) = logistics.inserters.getValue(inserter.identifier).maxStack

    fun belt(value: String?): Belt =
        value?.firstOrNull()?.let(Belt::fromCode)?.takeIf { it in availableBelts } ?: defaultBelt

    fun inserter(value: String?): Inserter =
        value?.firstOrNull()?.let(Inserter::fromCode)?.takeIf { it in availableInserters }
            ?: defaultInserter

    /**
     * Belt stacking level. Belt stacking is unlocked by the Space Age stack inserters/belt capacity
     * research, so without Space Age it is fixed at one.
     */
    val beltStackMax: Int = if (spaceAge) Belt.MAX_STACK else 1

    fun beltStack(value: String?): Int = (value?.toIntOrNull() ?: 1).coerceIn(1, beltStackMax)

    /** Number of belt lanes in use; defaults to the single lane factories usually feed. */
    fun beltLanes(value: String?): Int = (value?.toIntOrNull() ?: 1).coerceIn(1, Belt.MAX_LANES)

    /**
     * Inserter stacking level, clamped to what [inserter] can carry. Picking a bulk or stack
     * inserter raises the level to its base hand size (2 resp. 6) even if a smaller number was
     * requested, since those inserters never carry fewer items.
     */
    fun inserterStack(value: String?, inserter: Inserter): Int {
        val stats = logistics.inserters.getValue(inserter.identifier)
        return stats.stackSize(value?.toIntOrNull() ?: stats.minStack)
    }
}
