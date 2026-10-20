package de.bjrke.factorio.optimizer.common.model

/**
 * A single image layer of an icon. [scale] and the optional [left]/[top] (fractions of the icon
 * box) mirror Factorio's `icons` layering; the default centres the layer.
 */
data class IconLayer(
    val file: String,
    val scale: Double = 1.0,
    val left: Double? = null,
    val top: Double? = null,
)

/** Maps the few prototype names whose icon file differs from the prototype name. */
private fun iconName(s: String) =
    when (s) {
        "stone-wall" -> "wall"
        "raw-fish" -> "fish"
        "asteroid-collection" -> "asteroid-collector"
        "space-age" -> "factorio-space-age-logo-11tiles"
        // the recipe auto-derives its icon from the hot fluid it produces
        "fluoroketone" -> "fluoroketone-hot"
        // these prototypes reuse another prototype's icon file
        "barrel" -> "empty-barrel"
        "heat-exchanger" -> "heat-boiler"
        "capture-robot-rocket" -> "capture-bot"
        "landing-pad-unloading-bay" -> "cargo-unloading-bay"
        else -> s
    }

/**
 * Factorio composes the auto-generated `*-recycling` recipe icon from the recycling background, the
 * recycled item and the recycling top overlay (see `recycler/recycling.lua`). The `data.raw` dump
 * drops those icons, so the layers are reconstructed from the recipe name; the recycled item equals
 * the name suffix. The synthetic `*-spoiling` recipes get the spoiling item plus a spoilage badge.
 */
fun iconLayers(name: String): List<IconLayer> =
    when {
        name.endsWith("-recycling") ->
            listOf(
                IconLayer("recycling"),
                IconLayer(iconName(name.removeSuffix("-recycling")), 0.6),
                IconLayer("recycling-top"),
            )
        name.endsWith("-spoiling") ->
            listOf(
                IconLayer(iconName(name.removeSuffix("-spoiling"))),
                IconLayer("spoilage", 0.5, left = 0.5, top = 0.5),
            )
        else -> listOf(IconLayer(iconName(name)))
    }
