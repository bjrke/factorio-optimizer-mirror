package de.bjrke.factorio.optimizer.js.component

import de.bjrke.test.ComponentTest
import de.bjrke.test.RenderResult
import de.bjrke.test.assertAll
import de.bjrke.test.assertTableOf
import de.bjrke.test.render
import kotlin.test.Test
import kotlin.test.assertEquals
import react.FC
import react.Props
import react.create
import web.dom.Element

private external interface IconHostProps : Props {
    var name: String
    var size: Int?
    var alt: String?
    var rotation: Int?
    var flipHorizontal: Boolean?
}

private val iconHost =
    FC<IconHostProps>("iconHost") { props ->
        icon(props.name) {
            size = props.size
            alt = props.alt
            rotation = props.rotation
            flipHorizontal = props.flipHorizontal
        }
    }

private fun RenderResult.images(): List<Element> {
    val nodes = container.querySelectorAll("img")
    return (0 until nodes.length).mapNotNull { nodes.item(it) }
}

private fun RenderResult.sources() = images().map { it.getAttribute("src") }

class IconTest : ComponentTest() {
    private fun renderIcon(
        name: String,
        size: Int? = null,
        alt: String? = null,
        rotation: Int? = null,
        flipHorizontal: Boolean? = null,
    ) =
        render(
            iconHost.create {
                this.name = name
                this.size = size
                this.alt = alt
                this.rotation = rotation
                this.flipHorizontal = flipHorizontal
            }
        )

    @Test
    fun plainMaterialRendersASingleImage() {
        val result = renderIcon("iron-gear-wheel")

        assertAll(
            { assertEquals(listOf("icons/iron-gear-wheel.png"), result.sources()) },
            { assertEquals("iron-gear-wheel", result.images().single().getAttribute("title")) },
        )
    }

    @Test
    fun prototypeNamesWithADifferentIconFileAreMapped() =
        assertTableOf(
            "stone-wall" to "wall",
            "raw-fish" to "fish",
            "asteroid-collection" to "asteroid-collector",
            "space-age" to "factorio-space-age-logo-11tiles",
        ) { (name, file) ->
            assertEquals(listOf("icons/$file.png"), renderIcon(name).sources())
        }

    @Test
    fun theAltTextCanOverrideThePrototypeName() {
        val image = renderIcon("space-age", alt = "Space Age").images().single()

        assertAll(
            { assertEquals("Space Age", image.getAttribute("alt")) },
            { assertEquals("Space Age", image.getAttribute("title")) },
        )
    }

    @Test
    fun recyclingRecipeIsComposedOfTheRecyclingLayers() {
        val result = renderIcon("iron-gear-wheel-recycling")

        assertAll(
            {
                assertEquals(
                    listOf(
                        "icons/recycling.png",
                        "icons/iron-gear-wheel.png",
                        "icons/recycling-top.png",
                    ),
                    result.sources(),
                )
            },
            {
                assertEquals(
                    "iron-gear-wheel-recycling",
                    result.container.querySelector("span")?.getAttribute("title"),
                )
            },
        )
    }

    @Test
    fun recyclingRecipeMapsTheRecycledItemName() {
        val result = renderIcon("raw-fish-recycling")

        assertEquals(
            listOf("icons/recycling.png", "icons/fish.png", "icons/recycling-top.png"),
            result.sources(),
        )
    }

    @Test
    fun spoilingRecipeShowsTheItemWithASpoilageBadge() {
        val result = renderIcon("raw-fish-spoiling")

        assertEquals(listOf("icons/fish.png", "icons/spoilage.png"), result.sources())
    }

    @Test
    fun sizeControlsTheRenderedDimensions() {
        val plain = renderIcon("iron-gear-wheel", size = 64)
        val layered = renderIcon("scrap-recycling", size = 100)

        assertAll(
            { assertEquals("64", plain.images().single().getAttribute("width")) },
            { assertEquals("100px", computedWidth(layered.container.querySelector("span"))) },
            // the recycled item is scaled to 0.6 of the icon size
            { assertEquals("60px", computedWidth(layered.images()[1])) },
        )
    }

    private fun computedWidth(element: Element?) =
        js("(function (el) { return getComputedStyle(el).width; })")(element)

    private fun computedTransform(element: Element?) =
        js("(function (el) { return getComputedStyle(el).transform; })")(element)

    @Test
    fun rotationTurnsTheIconClockwise() {
        val rotated = renderIcon("transport-belt", rotation = 90)

        assertEquals("matrix(0, 1, -1, 0, 0, 0)", computedTransform(rotated.images().single()))
    }

    @Test
    fun flipHorizontalMirrorsTheIconAcrossTheVerticalAxis() {
        val mirrored = renderIcon("inserter", flipHorizontal = true)

        assertEquals("matrix(-1, 0, 0, 1, 0, 0)", computedTransform(mirrored.images().single()))
    }

    @Test
    fun anUprightIconIsNotTransformed() {
        val plain = renderIcon("transport-belt")

        assertEquals("none", computedTransform(plain.images().single()))
    }
}
