package de.bjrke.factorio.optimizer.js.component

import de.bjrke.factorio.optimizer.common.blueprint.layout.BeltEnd
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.BlueprintPlanResult
import de.bjrke.factorio.optimizer.common.fixtures.copperOre
import de.bjrke.factorio.optimizer.common.fixtures.ironOre
import de.bjrke.factorio.optimizer.common.fixtures.testLogisticsConfig
import de.bjrke.factorio.optimizer.common.game.prototype.FactoryConfig
import de.bjrke.factorio.optimizer.common.game.prototype.Surface
import de.bjrke.test.ComponentTest
import de.bjrke.test.assertAll
import de.bjrke.test.render
import kotlin.test.Test
import kotlin.test.assertEquals
import react.create
import web.dom.Element

/**
 * The selection controls are grouped into rows. A line may break between groups, never inside one,
 * so every group is a `nowrap` flex row and neighbouring groups are separate siblings.
 */
class SelectionGroupTest : ComponentTest() {
    private val factoryConfig = FactoryConfig(spaceAge = true)

    private fun renderFactorySelector() =
        render(
            factorySelector.create {
                config = factoryConfig
                selectedFactories = factoryConfig.defaults
                selectFactory = {}
            },
        )

    private fun renderPlanetSelector() =
        render(
            planetSelector.create {
                selectedSurface = Surface.NAUVIS
                availableSurfaces = Surface.entries
                enabled = true
                selectSurface = {}
            },
        )

    private fun renderLogisticsSelector() =
        render(
            logisticsSelector.create {
                config = testLogisticsConfig
                surface = Surface.NAUVIS
                selectedBelt = testLogisticsConfig.defaultBelt
                selectedInserter = testLogisticsConfig.defaultInserter
                beltLanes = 1
                beltStack = testLogisticsConfig.beltStackMin(testLogisticsConfig.defaultInserter)
                inserterStack = 1
                beltEnd = BeltEnd.CONSTANT_COMBINATOR
                selectBelt = {}
                selectInserter = {}
                selectBeltLanes = {}
                selectBeltStack = {}
                selectInserterStack = {}
                selectBeltEnd = {}
            },
        )

    private fun renderMaterialSelector() =
        render(
            materialSelector.create {
                material = ironOre
                availableMaterials = listOf(ironOre, copperOre)
                changeMaterial = {}
            },
        )

    private fun renderIntermediateLimit() =
        render(
            intermediateLimitInput.create {
                value = 10
                change = {}
            },
        )

    private fun renderSpaceAgeToggle() =
        render(
            spaceAgeToggle.create {
                enabled = true
                toggle = {}
            },
        )

    private fun renderCopyButton() =
        render(
            blueprintCopyButton.create {
                outcome = BlueprintPlanResult.Unavailable("unavailable")
                blueprint = null
            },
        )

    private fun groups(container: Element): List<Element> {
        // every top-level element a selector renders is one group row
        return (0 until container.children.length).map { container.children.item(it) as Element }
    }

    private fun computedStyle(element: Element, property: String): String =
        js("(function (el, prop) { return getComputedStyle(el)[prop]; })")(element, property)

    private fun rows(groups: List<Element>) = groups.map {
        computedStyle(it, "display") to computedStyle(it, "flex-wrap")
    }

    private fun allGroups() =
        groups(renderFactorySelector().container) +
            groups(renderPlanetSelector().container) +
            groups(renderLogisticsSelector().container) +
            groups(renderMaterialSelector().container) +
            groups(renderIntermediateLimit().container) +
            groups(renderSpaceAgeToggle().container) +
            groups(renderCopyButton().container)

    private fun assertNeighboursAreSiblings(groups: List<Element>) =
        assertEquals(
            emptyList(),
            groups.zipWithNext().filterNot { (left, right) -> left.nextElementSibling === right },
        )

    @Test
    fun everySelectionGroupIsOneNowrapRow() {
        val groups = allGroups()

        assertEquals(List(groups.size) { "flex" to "nowrap" }, rows(groups))
    }

    @Test
    fun everySelectionGroupIsBoxedWithAThinBorder() {
        val groups = allGroups()

        assertEquals(
            List(groups.size) { Triple("solid", "1px", "rgb(120, 120, 120)") },
            groups.map {
                Triple(
                    computedStyle(it, "border-top-style"),
                    computedStyle(it, "border-top-width"),
                    computedStyle(it, "border-top-color"),
                )
            },
        )
    }

    @Test
    fun singleControlsCarryTheIconOptionsInnerSpacing() {
        val controls =
            listOf(renderMaterialSelector().container, renderIntermediateLimit().container).map {
                (it.children.item(0) as Element).children.item(0) as Element
            }

        assertEquals(
            List(controls.size) { listOf("0px", "0px", "2px", "2px") },
            controls.map {
                listOf(
                    computedStyle(it, "margin-top"),
                    computedStyle(it, "margin-left"),
                    computedStyle(it, "padding-top"),
                    computedStyle(it, "padding-left"),
                )
            },
        )
    }

    @Test
    fun neighbouringGroupsAreSeparateSiblings() {
        val factoryGroups = groups(renderFactorySelector().container)
        val logisticsGroups = groups(renderLogisticsSelector().container)

        assertAll(
            { assertNeighboursAreSiblings(factoryGroups) },
            { assertNeighboursAreSiblings(logisticsGroups) },
        )
    }

    @Test
    fun eachLogicalGroupGetsItsOwnContainer() {
        val factories = groups(renderFactorySelector().container)

        assertAll(
            {
                // one row per exclusive group plus one row for the independently toggled machines
                assertEquals(
                    factoryConfig.groups.map { it.size } + factoryConfig.singles.size,
                    factories.map { it.querySelectorAll("input").length },
                )
            },
            {
                // the surface radios stay together in one row
                assertEquals(
                    listOf(Surface.entries.size),
                    groups(renderPlanetSelector().container).map {
                        it.querySelectorAll("input").length
                    },
                )
            },
            {
                // the belt, the inserter and the belt end each get their own row
                assertEquals(3, groups(renderLogisticsSelector().container).size)
            },
            // a single control is still a group of one
            { assertEquals(1, groups(renderMaterialSelector().container).size) },
            { assertEquals(1, groups(renderIntermediateLimit().container).size) },
            { assertEquals(1, groups(renderSpaceAgeToggle().container).size) },
            { assertEquals(1, groups(renderCopyButton().container).size) },
        )
    }
}
