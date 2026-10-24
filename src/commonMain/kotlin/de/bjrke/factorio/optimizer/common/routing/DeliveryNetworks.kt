package de.bjrke.factorio.optimizer.common.routing

import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Material

/**
 * The route tiles of every produced material, so a nearer consumer can split off an existing belt.
 * A band or import material has no belt trunk, so its tiles are never recorded.
 */
internal class DeliveryNetworks(private val beltMaterials: List<Material>) {

    private val tiles = mutableMapOf<Material, MutableSet<Coordinate>>()

    fun record(material: Material, nodes: List<Coordinate>, sink: Sink) {
        if (material !in beltMaterials) return
        tiles.getOrPut(material) { mutableSetOf() } += nodes.filter { it != sink.feed }
    }

    operator fun get(material: Material): Set<Coordinate> = tiles[material].orEmpty()
}
