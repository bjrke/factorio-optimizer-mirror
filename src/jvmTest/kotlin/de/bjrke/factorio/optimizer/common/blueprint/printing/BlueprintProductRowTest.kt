package de.bjrke.factorio.optimizer.common.blueprint.printing

import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.BeltEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.ConveyorEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.LoaderEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.UndergroundBeltEntity
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.assertNoLoaderOverTunnel
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.layoutFromUrl
import de.bjrke.factorio.optimizer.common.core.model.Io
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.test.assertAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** The product row is a routing wall, and a trimmed import marker stays off tunnel spans. */
class BlueprintProductRowTest {

    /**
     * The target product row is a routing wall from the output belt east: the LDS feed that crosses
     * it must tunnel, and no loader may sit above a tunnel span.
     */
    @Test
    fun theReportedUtilityScienceUrlKeepsTheProductRowClear() {
        val layout =
            layoutFromUrl(
                "http://localhost:8080/#/0.178571428571/utility-science-pack/FSXbr/1/1",
            )
        val target = Material("utility-science-pack")
        val lowDensity = Material("low-density-structure")
        val belts = layout.entities.filterIsInstance<BeltEntity>()
        val productRow = belts.filter { it.material == target }.maxOf { it.area.topLeft.y }
        val productEnd =
            belts
                .filter { it.material == target && it.area.topLeft.y == productRow }
                .maxOf { it.area.topLeft.x }
        val foreignOnRow = belts.filter {
            it.area.topLeft.y == productRow &&
                it.area.topLeft.x > productEnd &&
                it.material != target
        }
        val ldsTunnels =
            layout.entities.filterIsInstance<UndergroundBeltEntity>().filter {
                it.material == lowDensity
            }
        val crossing =
            ldsTunnels
                .filter { it.io == Io.INPUT && it.area.topLeft.y > productRow }
                .firstOrNull { input ->
                    ldsTunnels.any { output ->
                        output.io == Io.OUTPUT &&
                            output.direction == input.direction &&
                            output.area.topLeft.x == input.area.topLeft.x &&
                            output.area.topLeft.y < productRow
                    }
                }
        val outputLoader =
            layout.entities.filterIsInstance<LoaderEntity>().filter {
                it.material == target
            }
        val eastOfLoader =
            layout.entities.filterIsInstance<ConveyorEntity>().filter { conveyor ->
                outputLoader.any { it.area.topLeft.x <= conveyor.area.topLeft.x } &&
                    conveyor.material != target
            }

        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            { assertNotNull(outputLoader.firstOrNull(), "no target output loader") },
            { assertEquals(emptyList(), foreignOnRow, "a foreign belt runs on the product row") },
            { assertEquals(emptyList(), eastOfLoader, "a belt runs east of the output loader") },
            { assertNotNull(crossing, "the LDS feed does not tunnel across the product row") },
            { assertNoLoaderOverTunnel(layout) },
        )
    }
}
