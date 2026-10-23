package de.bjrke.factorio.optimizer.common.prototype

import de.bjrke.factorio.optimizer.common.model.Material

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
    /** The default temperature of each fluid, used to configure an infinity pipe source. */
    val fluidTemperatures: Map<Material, Int> = emptyMap(),
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
enum class BeltTier(val code: Char?, val identifier: String, val spaceAgeOnly: Boolean = false) {
    TRANSPORT_BELT('t', "transport-belt"),
    // the red belt is the default, so it carries no code
    FAST_TRANSPORT_BELT(null, "fast-transport-belt"),
    EXPRESS_TRANSPORT_BELT('e', "express-transport-belt"),
    TURBO_TRANSPORT_BELT('u', "turbo-transport-belt", spaceAgeOnly = true);

    companion object {
        /** Items per belt lane per tile. A belt has [MAX_LANES] lanes. */
        const val ITEMS_PER_LANE = 4.0

        const val MAX_LANES = 2

        /** The Stack inserter research sets the belt stack to two items per slot. */
        const val MIN_STACK_INSERTER_STACK = 2

        /** Belt stacking caps at four items per slot. */
        const val MAX_STACK = 4

        private val byCode = entries.filter { it.code != null }.associateBy { it.code }

        /** Picks the tier whose code appears in the combined `selection` parameter. */
        fun fromSelection(selection: String?) = selection?.firstNotNullOfOrNull { byCode[it] }
    }
}

/**
 * An inserter tier. The rotation speed and stack capacity come from the dump; a tier without a
 * prototype is not selectable.
 */
enum class InserterTier(
    val code: Char?,
    val identifier: String,
    val spaceAgeOnly: Boolean = false,
) {
    BURNER_INSERTER('w', "burner-inserter"),
    INSERTER('i', "inserter"),
    LONG_HANDED_INSERTER('l', "long-handed-inserter"),
    // the fast inserter is the default, so it carries no code
    FAST_INSERTER(null, "fast-inserter"),
    BULK_INSERTER('y', "bulk-inserter"),
    STACK_INSERTER('z', "stack-inserter", spaceAgeOnly = true);

    companion object {
        private val byCode = entries.filter { it.code != null }.associateBy { it.code }

        /** Picks the tier whose code appears in the combined `selection` parameter. */
        fun fromSelection(selection: String?) = selection?.firstNotNullOfOrNull { byCode[it] }
    }
}

/**
 * The belts and inserters that can be selected for a given mod set and dump: a prototype missing
 * from [logistics] is dropped. Mirrors [FactoryConfig] for the production machines.
 */
class LogisticsConfig(val logistics: Logistics, val spaceAge: Boolean = true) {

    val availableBelts =
        BeltTier.entries.filter {
            (!it.spaceAgeOnly || spaceAge) && it.identifier in logistics.belts
        }

    val availableInserters =
        InserterTier.entries.filter {
            (!it.spaceAgeOnly || spaceAge) && it.identifier in logistics.inserters
        }

    /** The red belt by default, or the fastest available tier if it is missing from the dump. */
    val defaultBelt = availableBelts.firstOrNull { it.code == null } ?: availableBelts.last()

    /**
     * The fast inserter by default, or the fastest available tier if it is missing from the dump.
     */
    val defaultInserter =
        availableInserters.firstOrNull { it.code == null } ?: availableInserters.last()

    /** The items per hand [inserter] always carries, from its prototype. */
    fun minStack(inserter: InserterTier) =
        logistics.inserters.getValue(inserter.identifier).minStack

    /** The items per hand [inserter] can reach with all capacity research, from its prototype. */
    fun maxStack(inserter: InserterTier) =
        logistics.inserters.getValue(inserter.identifier).maxStack

    fun belt(selection: String?): BeltTier =
        BeltTier.fromSelection(selection)?.takeIf { it in availableBelts } ?: defaultBelt

    fun inserter(selection: String?) =
        InserterTier.fromSelection(selection)?.takeIf { it in availableInserters }
            ?: defaultInserter

    /**
     * The lowest belt stacking level for [inserter]. Belt stacking is unlocked by the Space Age
     * stack inserters, so without Space Age it is fixed at one. Once the stack inserter loads the
     * belt it always builds whole stacks, so the level never drops below
     * [BeltTier.MIN_STACK_INSERTER_STACK].
     */
    fun beltStackMin(inserter: InserterTier) =
        if (spaceAge && logistics.inserters.getValue(inserter.identifier).stacksOnBelt)
            BeltTier.MIN_STACK_INSERTER_STACK
        else 1

    /**
     * The highest belt stacking level; belt capacity research raises it to [BeltTier.MAX_STACK].
     */
    val beltStackMax = if (spaceAge) BeltTier.MAX_STACK else 1

    /**
     * The belt stacking level for [value], clamped to the range the selected [inserter] supports.
     */
    fun beltStack(value: String?, inserter: InserterTier) =
        (value?.toIntOrNull() ?: 1).coerceIn(beltStackMin(inserter), beltStackMax)

    /**
     * Number of belt lanes in use. [fullBelt] selects both lanes; without it the single lane
     * factories usually feed is used. The `selection` decoder is read in `prototype.selection`.
     */
    fun beltLanes(fullBelt: Boolean) = if (fullBelt) BeltTier.MAX_LANES else 1

    /**
     * InserterTier stacking level, clamped to what [inserter] can carry. Picking a bulk or stack
     * inserter raises the level to its base hand size (2 resp. 6) even if a smaller number was
     * requested, since those inserters never carry fewer items.
     */
    fun inserterStack(value: String?, inserter: InserterTier) =
        logistics.inserters.getValue(inserter.identifier).let {
            it.stackSize(value?.toIntOrNull() ?: it.minStack)
        }
}
