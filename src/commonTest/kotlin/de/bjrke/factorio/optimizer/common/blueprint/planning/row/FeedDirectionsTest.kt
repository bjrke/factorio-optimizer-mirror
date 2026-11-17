package de.bjrke.factorio.optimizer.common.blueprint.planning.row

import de.bjrke.factorio.optimizer.common.core.model.Direction
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.test.assertAll
import kotlin.test.Test
import kotlin.test.assertEquals

/** The network-weighted east/west split that decides which end a row's belts are fed from. */
class FeedDirectionsTest {

    private fun network(
        name: String,
        weight: Int,
        order: Double,
        import: Boolean = false,
    ) = FeedNetwork(Material(name), weight, order, import)

    @Test
    fun producedNetworksSplitByOrder() {
        val low = network("low", weight = 2, order = 10.0)
        val high = network("high", weight = 2, order = 30.0)

        assertEquals(
            mapOf(low.material to Direction.EAST, high.material to Direction.WEST),
            splitFeedDirections(listOf(high, low)),
        )
    }

    @Test
    fun anImportTakesTheEastSideBeforeAProducedNetwork() {
        val imported = network("imported", weight = 1, order = 0.0, import = true)
        val produced = network("produced", weight = 2, order = 10.0)

        assertEquals(
            mapOf(imported.material to Direction.EAST, produced.material to Direction.WEST),
            splitFeedDirections(listOf(produced, imported)),
        )
    }

    @Test
    fun importsThatReachHalfTheWeightKeepTheWholeEastSide() {
        val first = network("import-1", weight = 1, order = 0.0, import = true)
        val second = network("import-2", weight = 1, order = 0.0, import = true)
        val produced = network("produced", weight = 2, order = 10.0)

        assertEquals(
            mapOf(
                first.material to Direction.EAST,
                second.material to Direction.EAST,
                produced.material to Direction.WEST,
            ),
            splitFeedDirections(listOf(first, produced, second)),
        )
    }

    @Test
    fun theSplitBalancesWeightNotNetworkCount() {
        val light = network("light", weight = 1, order = 10.0)
        val heavy = network("heavy", weight = 4, order = 20.0)
        val other = network("other", weight = 4, order = 30.0)

        assertEquals(
            mapOf(
                light.material to Direction.EAST,
                heavy.material to Direction.EAST,
                other.material to Direction.WEST,
            ),
            splitFeedDirections(listOf(light, heavy, other)),
        )
    }

    @Test
    fun aTieKeepsTheSmallerPrefixEast() {
        val first = network("first", weight = 2, order = 10.0)
        val second = network("second", weight = 2, order = 20.0)
        val third = network("third", weight = 2, order = 30.0)

        assertAll(
            {
                assertEquals(
                    mapOf(
                        first.material to Direction.EAST,
                        second.material to Direction.WEST,
                        third.material to Direction.WEST,
                    ),
                    splitFeedDirections(listOf(first, second, third)),
                )
            },
            { assertEquals(emptyMap(), splitFeedDirections(emptyList())) },
        )
    }
}
