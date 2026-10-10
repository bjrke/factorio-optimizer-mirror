package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.Category
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.Recipe

/**
 * Spoilable items and the item they rot into. Spoiling is defined on the item prototypes
 * (`spoil_ticks` / `spoil_result`), which the recipe parser never sees, so the normal-quality spoil
 * times and results are hardcoded from the factorio prototypes. Bacteria are the only source of
 * iron/copper ore on Gleba, so these conversions are essential there.
 *
 * `biter-egg` and `pentapod-egg` are intentionally omitted: they spoil into entities (biters,
 * wrigglers), not items, so they cannot be modelled as a recipe.
 */
private data class Spoilage(
    val source: String,
    val result: String,
    val time: Double,
    val amount: Double = 1.0,
)

private val spoilages =
    listOf(
        // bacteria rot into the ore they are made from
        Spoilage("iron-bacteria", "iron-ore", 60.0),
        Spoilage("copper-bacteria", "copper-ore", 60.0),
        // everything else rots into spoilage
        Spoilage("raw-fish", "spoilage", 7550.0),
        Spoilage("bioflux", "spoilage", 7200.0),
        Spoilage("jellynut", "spoilage", 3600.0),
        Spoilage("yumako", "spoilage", 3600.0),
        Spoilage("agricultural-science-pack", "spoilage", 3600.0),
        Spoilage("nutrients", "spoilage", 300.0),
        Spoilage("jelly", "spoilage", 240.0),
        Spoilage("yumako-mash", "spoilage", 180.0),
    )

/**
 * Hardcoded spoilage recipes. Named `<source>-rotting` so they don't collide with the recipe that
 * produces the source item; the icon component maps that suffix back to the source icon.
 */
fun spoilageRecipes(material: (String) -> Material): List<Recipe> = spoilages.map {
    Recipe(
        name = "${it.source}-rotting",
        ingredients = mapOf(material(it.source) to 1.0),
        result = mapOf(material(it.result) to it.amount),
        time = it.time,
        category = Category.ROTTING,
    )
}
