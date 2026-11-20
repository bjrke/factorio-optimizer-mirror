package de.bjrke.factorio.optimizer.common.blueprint.planning.row

import de.bjrke.factorio.optimizer.common.blueprint.layout.IMPORT_FEED
import de.bjrke.factorio.optimizer.common.core.model.Direction
import de.bjrke.factorio.optimizer.common.core.model.Material
import kotlin.math.abs

/**
 * One material's belt network: its route weight, the order it sorts by and whether it is imported.
 */
internal data class FeedNetwork(
    val material: Material,
    val weight: Int,
    val order: Double,
    val import: Boolean = false,
)

/**
 * The feed side of every material on a belt: [Direction.EAST] when the consumer row takes it from
 * its west end, [Direction.WEST] when from its east end.
 *
 * The split balances the layout's belt **networks** instead of counting belt rows. A produced
 * material is one network of its producer row plus every row that takes it, so its weight is the
 * number of those rows (`consumers + 1`; a 1:1 feed is 2, one producer feeding two rows is 3). Its
 * order is the mean east extent of those rows, which puts a network where its routes live rather
 * than at its producer alone. Imports have no producer row, weigh 1, are keyed at the west feed
 * column and are always [Direction.EAST], so they reserve the low end: once their weight reaches
 * half the total every produced network runs [Direction.WEST].
 *
 * A producer has one output side, so the split gives every material one feed side for all of its
 * consumer belts. Its product belt exits on the opposite side, where the consumers are fed from.
 */
internal class FeedSides(
    private val inputs: List<Map<Material, Double>>,
    /** The east extent of each plan row, in row-position order. */
    private val rowRights: List<Int>,
    private val producerIndex: Map<Material, Int>,
    private val beltMaterials: Set<Material>,
    private val target: Material,
) {

    private val directions =
        splitFeedDirections(inputs.flatMapTo(mutableSetOf()) { it.keys }.map(::network))

    /** The direction [material]'s consumer belts run, or null when no row takes it over a belt. */
    fun feedDirection(material: Material): Direction? = directions[material]

    /**
     * The side [material]'s product belt exits on: opposite to where its consumer belts take the
     * material, so a producer hands its product to the end its consumers are fed from. The target
     * product always exits east, where `placeTargetOutput` puts the connector.
     */
    fun productDirection(material: Material): Direction =
        if (material == target) Direction.EAST
        else feedDirection(material)?.opposite() ?: Direction.EAST

    /** One material's belt network: its producer plus every row that takes it over a belt. */
    private fun networkRows(material: Material) =
        when (val producer = producerIndex[material]) {
            // an import has no producer, so it is keyed at the west feed column: below every row's
            // east extent, which keeps the imports on the low end of the order
            null -> listOf(IMPORT_FEED)
            else ->
                inputs.indices
                    .filter { it != producer && material in inputs[it] }
                    .plus(producer)
                    .map { rowRights[it] }
        }

    private fun network(material: Material): FeedNetwork {
        val rows = networkRows(material)
        return FeedNetwork(
            material = material,
            weight = rows.size,
            order = rows.average(),
            import = material !in beltMaterials,
        )
    }
}

/**
 * Splits [networks] into the materials whose consumer belts run [Direction.EAST] (fed from the
 * west) and [Direction.WEST] (fed from the east).
 *
 * Imports always run east so they keep the west corridor. The rest are sorted by their order and
 * the split is the prefix whose cumulative weight is closest to half the total, ties keeping the
 * smaller prefix; a heavier network can therefore move the boundary past a lighter one. See
 * [FeedSides] for how a network's weight and order are built.
 */
internal fun splitFeedDirections(networks: List<FeedNetwork>) = buildMap {
    val sortedNetworks = networks.sortedWith(compareBy({ it.order }, { it.material.name }))

    var bestDistance = sortedNetworks.sumOf { it.weight }
    var diff = -bestDistance

    for (network in sortedNetworks) {
        diff += 2 * network.weight
        val distance = abs(diff)
        val direction =
            when {
                distance < bestDistance -> {
                    bestDistance = distance
                    Direction.EAST
                }
                network.import -> Direction.EAST
                else -> Direction.WEST
            }
        put(network.material, direction)
    }
}
