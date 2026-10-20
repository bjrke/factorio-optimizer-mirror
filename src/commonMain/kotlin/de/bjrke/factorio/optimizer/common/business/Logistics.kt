package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.*

private const val TICKS_PER_SECOND = 60.0

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
    val belt: BeltTier,
    val inserter: InserterTier,
    val lanes: Int,
    val beltStack: Int,
    private val inserterStack: Int,
) {
    private val inserterStats: InserterStats
        get() = logistics.inserters.getValue(inserter.identifier)

    /** The items per hand the selected inserter actually carries; never below its hand size. */
    val handSize: Int
        get() = inserterStats.stackSize(inserterStack)

    /** Fluids move through pipes and pumps, not on belts. */
    fun isFluid(material: Material) = material in logistics.fluids

    /** The default temperature of a fluid, for an infinity pipe source; 0 when unknown. */
    fun fluidTemperature(material: Material) = logistics.fluidTemperatures[material] ?: 0

    /** Whether the selected inserter can stack items on a belt (only the stack inserter can). */
    fun stacksOnBelt() = inserterStats.stacksOnBelt

    /** Items per second a single belt carries over [lanes] lanes with [stack] items per slot. */
    fun beltThroughput(stack: Int): Double =
        logistics.belts.getValue(belt.identifier) *
            TICKS_PER_SECOND *
            BeltTier.ITEMS_PER_LANE *
            lanes *
            stack

    /** Items per second a single inserter moves between storages, one turn being one cycle. */
    fun inserterThroughput() = inserterStats.rotationSpeed * TICKS_PER_SECOND * handSize

    /** Ticks the belt needs to move past the space a single item occupies on a lane. */
    private fun beltSlotTicks() =
        1.0 / (logistics.belts.getValue(belt.identifier) * BeltTier.ITEMS_PER_LANE)

    /**
     * Items per second a single inserter drops onto a belt. Every placement needs the belt to make
     * room for it, so the rate depends on the belt speed: a stack inserter fills one belt slot with
     * up to [beltStack] items, any other inserter places a single item per slot and therefore has
     * to wait for a free slot for each item.
     */
    fun placingThroughput(): Double {
        val slots = if (stacksOnBelt()) (handSize + beltStack - 1) / beltStack else handSize
        val cycleTicks = 1.0 / inserterStats.rotationSpeed + slots * beltSlotTicks()
        return handSize * TICKS_PER_SECOND / cycleTicks
    }

    /** Fluid per second a single pump moves; the pipe network itself is not the limit. */
    fun pumpThroughput(): Double? = logistics.pumpRate

    fun beltCount(rate: Double, stack: Int) = rate / beltThroughput(stack)

    /** Inserters needed for [rate]; [ontoBelt] uses the slower belt-limited placing rate. */
    fun inserterCount(rate: Double, ontoBelt: Boolean = false) =
        if (ontoBelt) beltInserterCount(rate) else directInserterCount(rate)

    /** Inserters needed for [rate]; uses the slower belt-limited placing rate. */
    fun beltInserterCount(rate: Double): Double = rate / placingThroughput()

    /** Inserters needed for [rate]; uses the faster direct inserting rate. */
    fun directInserterCount(rate: Double): Double = rate / inserterThroughput()

    fun pumpCount(rate: Double): Double? = pumpThroughput()?.let { rate / it }
}
