package de.bjrke.factorio.optimizer.common.plan

import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.prototype.BeltTier
import de.bjrke.factorio.optimizer.common.prototype.InserterStats
import de.bjrke.factorio.optimizer.common.prototype.InserterTier
import de.bjrke.factorio.optimizer.common.prototype.Logistics

private const val TICKS_PER_SECOND = 60.0

/**
 * The item rates of one inserter tier at a selected hand size and belt stacking. Separated from
 * [LogisticsCalculator] because the belt, pump and inserter counts only need these three rates.
 */
class InserterRate(
    private val stats: InserterStats,
    private val handSize: Int,
    private val beltStack: Int,
    /** The selected belt's speed in tiles per tick. */
    private val beltSpeed: Double,
) {
    /** Whether the selected inserter can stack items on a belt (only the stack inserter can). */
    val stacksOnBelt: Boolean
        get() = stats.stacksOnBelt

    /** Items per second a single inserter moves between storages, one turn being one cycle. */
    fun inserterThroughput() = stats.rotationSpeed * TICKS_PER_SECOND * handSize

    /**
     * Items per second a single inserter drops onto a belt. Every placement needs the belt to make
     * room for it, so the rate depends on the belt speed: a stack inserter fills one belt slot with
     * up to [beltStack] items, any other inserter places a single item per slot and therefore has
     * to wait for a free slot for each item.
     */
    fun placingThroughput(): Double {
        val slots = if (stacksOnBelt) (handSize + beltStack - 1) / beltStack else handSize
        val cycleTicks = 1.0 / stats.rotationSpeed + slots * beltSlotTicks()
        return handSize * TICKS_PER_SECOND / cycleTicks
    }

    /** Ticks the belt needs to move past the space a single item occupies on a lane. */
    private fun beltSlotTicks() = 1.0 / (beltSpeed * BeltTier.ITEMS_PER_LANE)
}

class LogisticsCalculatorSelection(
    val belt: BeltTier = BeltTier.FAST_TRANSPORT_BELT,
    val inserter: InserterTier = InserterTier.FAST_INSERTER,
    val lanes: Int = 2,
    val beltStack: Int = 1,
    val inserterStack: Int = 1,
)

/**
 * Turns a material rate (items or fluid per second) into the number of belts/inserters/pumps needed
 * to move it. The stats come from the dump ([Logistics]); a prototype missing there is not usable,
 * so there is no fallback.
 *
 * The counts are continuous so they line up with the fractional machine counts the optimizer
 * produces; callers round for display.
 */
class LogisticsCalculator(
    private val logistics: Logistics,
    val selection: LogisticsCalculatorSelection = LogisticsCalculatorSelection(),
) {
    val inserterStack = selection.inserterStack
    val beltStack = selection.beltStack
    val lanes = selection.lanes
    val inserter = selection.inserter
    val belt = selection.belt

    private val inserterStats
        get() = logistics.inserters.getValue(inserter.identifier)

    /** The items per hand the selected inserter actually carries; never below its hand size. */
    val handSize
        get() = inserterStats.stackSize(inserterStack)

    /**
     * The selected inserter's rates at this hand size and belt stacking. Lazy so a calculator can
     * still be built for a partial prototype dump when only its material predicates are queried.
     */
    val rates by lazy {
        InserterRate(
            inserterStats,
            handSize,
            beltStack,
            logistics.belts.getValue(belt.identifier),
        )
    }

    /** Fluids move through pipes and pumps, not on belts. */
    fun isFluid(material: Material) = material in logistics.fluids

    /** The default temperature of a fluid, for an infinity pipe source; 0 when unknown. */
    fun fluidTemperature(material: Material) = logistics.fluidTemperatures[material] ?: 0

    /** Items per second a single belt carries over [lanes] lanes with [stack] items per slot. */
    fun beltThroughput(stack: Int) =
        logistics.belts.getValue(belt.identifier) *
            TICKS_PER_SECOND *
            BeltTier.ITEMS_PER_LANE *
            lanes *
            stack

    /** Items per second a single inserter moves between storages, one turn being one cycle. */
    fun inserterThroughput() = rates.inserterThroughput()

    /** Items per second a single inserter drops onto a belt. */
    fun placingThroughput() = rates.placingThroughput()

    /** Fluid per second a single pump moves; the pipe network itself is not the limit. */
    fun pumpThroughput() = logistics.pumpRate

    fun beltCount(rate: Double, stack: Int) = rate / beltThroughput(stack)

    /** Inserters needed for [rate]; [ontoBelt] uses the slower belt-limited placing rate. */
    fun inserterCount(rate: Double, ontoBelt: Boolean = false) =
        if (ontoBelt) beltInserterCount(rate) else directInserterCount(rate)

    /** Inserters needed for [rate]; uses the slower belt-limited placing rate. */
    fun beltInserterCount(rate: Double) = rate / placingThroughput()

    /** Inserters needed for [rate]; uses the faster direct inserting rate. */
    fun directInserterCount(rate: Double) = rate / inserterThroughput()

    fun pumpCount(rate: Double) = rate / pumpThroughput()
}
