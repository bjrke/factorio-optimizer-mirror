package de.bjrke.factorio.optimizer.common.fixtures

import de.bjrke.factorio.optimizer.common.game.prototype.Category
import de.bjrke.factorio.optimizer.common.game.prototype.Recipe

// Named game recipes the blueprint and band suites build rows from. The ingredient amounts, results
// and times follow `data.raw`, so a test reads like the recipe it exercises.

val ironPlateRecipe =
    Recipe(
        name = "iron-plate",
        ingredients = mapOf(ironOre to 1.0),
        result = mapOf(ironPlate to 1.0),
        time = 1.0,
        categories = setOf(Category.CRAFTING),
    )

val ironGearWheelRecipe =
    Recipe(
        name = "iron-gear-wheel",
        ingredients = mapOf(ironPlate to 2.0),
        result = mapOf(gear to 1.0),
        time = 1.0,
        categories = setOf(Category.CRAFTING),
    )

val copperCableRecipe =
    Recipe(
        name = "copper-cable",
        ingredients = mapOf(copperPlate to 1.0),
        result = mapOf(copperCable to 2.0),
        time = 0.5,
        categories = setOf(Category.CRAFTING),
    )

val electronicCircuitRecipe =
    Recipe(
        name = "electronic-circuit",
        ingredients = mapOf(ironPlate to 1.0, copperCable to 3.0),
        result = mapOf(electronicCircuit to 1.0),
        time = 0.5,
        categories = setOf(Category.CRAFTING),
    )

val advancedCircuitRecipe =
    Recipe(
        name = "advanced-circuit",
        ingredients = mapOf(electronicCircuit to 2.0, plasticBar to 2.0, copperCable to 4.0),
        result = mapOf(advancedCircuit to 1.0),
        time = 6.0,
        categories = setOf(Category.CRAFTING),
    )

val smallElectricPoleRecipe =
    Recipe(
        name = "small-electric-pole",
        ingredients = mapOf(wood to 1.0, copperCable to 2.0),
        result = mapOf(smallElectricPole to 2.0),
        time = 0.5,
        categories = setOf(Category.CRAFTING),
    )

val engineUnitRecipe =
    Recipe(
        name = "engine-unit",
        ingredients = mapOf(steelPlate to 1.0, gear to 1.0, pipe to 2.0),
        result = mapOf(engineUnit to 1.0),
        time = 10.0,
        categories = setOf(Category.CRAFTING),
    )

val electricEngineUnitRecipe =
    Recipe(
        name = "electric-engine-unit",
        ingredients = mapOf(engineUnit to 1.0, lubricant to 15.0, electronicCircuit to 2.0),
        result = mapOf(electricEngineUnit to 1.0),
        time = 10.0,
        categories = setOf(Category.CRAFTING),
    )

val flyingRobotFrameRecipe =
    Recipe(
        name = "flying-robot-frame",
        ingredients =
            mapOf(
                electricEngineUnit to 1.0,
                battery to 2.0,
                steelPlate to 1.0,
                electronicCircuit to 3.0,
            ),
        result = mapOf(flyingRobotFrame to 1.0),
        time = 20.0,
        categories = setOf(Category.CRAFTING),
    )

val batteryRecipe =
    Recipe(
        name = "battery",
        ingredients = mapOf(sulfuricAcid to 20.0, ironPlate to 1.0, copperPlate to 1.0),
        result = mapOf(battery to 1.0),
        time = 4.0,
        categories = setOf(Category.CHEMISTRY),
    )

val sulfurRecipe =
    Recipe(
        name = "sulfur",
        ingredients = mapOf(water to 30.0, petroleum to 30.0),
        result = mapOf(sulfur to 2.0),
        time = 1.0,
        categories = setOf(Category.CHEMISTRY),
    )

val sulfuricAcidRecipe =
    Recipe(
        name = "sulfuric-acid",
        ingredients = mapOf(sulfur to 5.0, ironPlate to 1.0, water to 100.0),
        result = mapOf(sulfuricAcid to 50.0),
        time = 1.0,
        categories = setOf(Category.CHEMISTRY),
    )
