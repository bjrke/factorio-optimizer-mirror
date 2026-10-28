package de.bjrke.factorio.optimizer.common.blueprint.fixtures

import de.bjrke.factorio.optimizer.common.blueprint.blueprintLayoutDump
import de.bjrke.factorio.optimizer.common.layout.entity.InserterEntity
import de.bjrke.factorio.optimizer.common.layout.entity.MachineEntity
import de.bjrke.factorio.optimizer.common.layout.entity.Plumbing
import de.bjrke.factorio.optimizer.common.model.Area
import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.plan.BlueprintPlan
import de.bjrke.factorio.optimizer.common.plan.FluidPort
import de.bjrke.factorio.optimizer.common.plan.PlannedRecipe
import de.bjrke.factorio.optimizer.common.plan.PlannedRow
import de.bjrke.factorio.optimizer.common.plan.PlannedSource
import de.bjrke.factorio.optimizer.common.plan.isInputPort
import de.bjrke.factorio.optimizer.common.plan.isOutputPort
import de.bjrke.factorio.optimizer.common.plan.prototypeFluidPorts
import de.bjrke.factorio.optimizer.common.prototype.Category
import de.bjrke.factorio.optimizer.common.prototype.Factory
import de.bjrke.factorio.optimizer.common.service.FactoryProduction

/**
 * A water-only chemistry recipe: the whole row is fluid, so nothing but a pipe can reach a port.
 */
fun waterRecipe(also: Map<Material, Double> = emptyMap()) =
    recipe("water-boil", Category.CHEMISTRY, mapOf(gear to 1.0), mapOf(water to 1.0) + also)

/**
 * A plan of [factory] boiling water into a gear; [fluidInputs] join the water as further inputs.
 */
fun fluidRowPlan(
    factory: Factory,
    machines: Double = 1.0,
    fluidInputs: Map<Material, Double> = emptyMap(),
): BlueprintPlan {
    val boil = waterRecipe(fluidInputs)
    val planned = PlannedRecipe(FactoryProduction(gear, boil, factory, amount = 1.0, level = 0))
    return BlueprintPlan(
        target = gear,
        rows = listOf(PlannedRow(List(machines.toInt().coerceAtLeast(1)) { planned })),
        sources = (listOf(water) + fluidInputs.keys).map { PlannedSource(it, 1.0, fluid = true) },
        scale = 1.0,
        label = "water-boil",
    )
}

/** The machines of [plan]'s layout with the pipes and inserters the layout put against them. */
class FluidRowLayout(plan: BlueprintPlan) {

    private val dump = blueprintLayoutDump(plan, logistics)

    /** The feeds the layout could not place; empty for a valid fluid row. */
    val failures = dump.failures

    private val machineEntities = dump.entities.filterIsInstance<MachineEntity>()

    val machines: List<Area> = machineEntities.map { it.area }

    // keyed by corner: an Area is a Set, so two same-sized machines with different corners compare
    // by their tiles, not by their position
    private val machinesToName: Map<Coordinate, String> = machineEntities.associate {
        it.area.topLeft to it.name
    }

    /** Every pipe or pipe-to-ground of the layout, mapped to its fluid. */
    val plumbing: Map<Coordinate, Material> =
        dump.entities.filterIsInstance<Plumbing>().associate { it.area.topLeft to it.material }

    /** Every inserter of the layout, by tile. */
    val inserters: Map<Coordinate, InserterEntity> =
        dump.entities.filterIsInstance<InserterEntity>().associateBy { it.area.topLeft }

    /**
     * Every pipe or pipe-to-ground of the layout that stands on a machine's *port* tile, mapped to
     * its fluid. A route can run along a lane next to a machine without feeding it, so a pipe only
     * counts when the port table puts a port of that machine there. One entry is one fed (or
     * drained) port; a port the route tunnelled through still carries its fluid.
     */
    val portPipes: Map<Coordinate, Material> =
        machines
            .flatMap(::portsOf)
            .distinct()
            .mapNotNull { tile -> plumbing[tile]?.let { tile to it } }
            .toMap()

    /**
     * The fed pipes on a machine's own input side: every port the edge flow leaves open to inflow.
     */
    val inputPipes: Map<Coordinate, Material> = portPipes.filterKeys { tile ->
        portEntryAt(tile)?.let { (identifier, port) -> isInputPort(identifier, port) } == true
    }

    /** The fed pipes on a machine's own output side: every port the edge flow lets fluid out of. */
    val outputPipes: Map<Coordinate, Material> = portPipes.filterKeys { tile ->
        portEntryAt(tile)?.let { (identifier, port) -> isOutputPort(identifier, port) } == true
    }

    /** The port table entry a pipe at [tile] stands on, with the machine that owns it. */
    private fun portEntryAt(tile: Coordinate): Pair<String, FluidPort>? =
        machineEntities.firstNotNullOfOrNull { machine ->
            prototypeFluidPorts(machine.name)
                .firstOrNull { machine.area.topLeft + it.tile == tile }
                ?.let { machine.name to it }
        }

    /** The port tiles of [machine] alone, from the port table of the machine it runs. */
    fun portsOf(machine: Area): List<Coordinate> {
        val identifier = machinesToName[machine.topLeft] ?: return emptyList()
        return prototypeFluidPorts(identifier).map { machine.topLeft + it.tile }
    }
}
