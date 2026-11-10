package de.bjrke.factorio.optimizer.common.fixtures

import de.bjrke.factorio.optimizer.common.game.prototype.Category
import de.bjrke.factorio.optimizer.common.game.prototype.Recipe
import de.bjrke.factorio.optimizer.common.game.prototype.Subgroup
import de.bjrke.factorio.optimizer.common.game.prototype.SurfaceCondition
import de.bjrke.factorio.optimizer.common.game.prototype.SurfaceProperty

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
        time = 0.5,
        categories = setOf(Category.CRAFTING),
    )

val copperCableRecipe =
    Recipe(
        name = "copper-cable",
        ingredients = mapOf(copperPlate to 1.0),
        result = mapOf(copperCable to 2.0),
        time = 0.5,
        categories = setOf(Category.CRAFTING, Category.ELECTROMAGNETICS),
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

val plasticBarRecipe =
    Recipe(
        name = "plastic-bar",
        ingredients = mapOf(petroleum to 20.0, coal to 1.0),
        result = mapOf(plasticBar to 2.0),
        time = 1.0,
        categories = setOf(Category.CHEMISTRY, Category.CRYOGENICS),
    )

val solidFuelFromLightOilRecipe =
    Recipe(
        name = "solid-fuel-from-light-oil",
        ingredients = mapOf(lightOil to 10.0),
        result = mapOf(solidFuel to 1.0),
        time = 1.0,
        categories = setOf(Category.CHEMISTRY),
    )

val nutrientsFromBiterEggRecipe =
    Recipe(
        name = "nutrients-from-biter-egg",
        ingredients = mapOf(biterEgg to 1.0),
        result = mapOf(nutrients to 20.0),
        time = 2.0,
        categories = setOf(Category.ORGANIC, Category.CRAFTING),
    )

val concreteRecipe =
    Recipe(
        name = "concrete",
        ingredients = mapOf(stoneBrick to 5.0, ironOre to 1.0, water to 100.0),
        result = mapOf(concrete to 10.0),
        time = 10.0,
        categories = setOf(Category.CRAFTING_WITH_FLUID),
    )

val oilRefineryRecipe =
    Recipe(
        name = "oil-refinery",
        ingredients =
            mapOf(
                steelPlate to 15.0,
                gear to 10.0,
                stoneBrick to 10.0,
                electronicCircuit to 10.0,
                pipe to 10.0,
            ),
        result = mapOf(oilRefinery to 1.0),
        time = 8.0,
        categories = setOf(Category.CRAFTING),
    )

// Uranium processing yields one of the two isotopes per craft; the fixture uses the expected
// amounts the recipe parser derives from the shared probabilities (0.7% U-235, 99.3% U-238).
val uraniumProcessingRecipe =
    Recipe(
        name = "uranium-processing",
        ingredients = mapOf(uraniumOre to 10.0),
        result = mapOf(uranium235 to 0.007, uranium238 to 0.993),
        time = 12.0,
        categories = setOf(Category.CENTRIFUGING),
        subgroup = Subgroup("uranium-processing"),
    )

val uraniumFuelCellRecipe =
    Recipe(
        name = "uranium-fuel-cell",
        ingredients = mapOf(ironPlate to 10.0, uranium235 to 1.0, uranium238 to 19.0),
        result = mapOf(uraniumFuelCell to 10.0),
        time = 10.0,
        categories = setOf(Category.CRAFTING),
    )

val ironPlateSmeltingRecipe =
    Recipe(
        name = "iron-plate",
        ingredients = mapOf(ironOre to 1.0),
        result = mapOf(ironPlate to 1.0),
        time = 3.2,
        categories = setOf(Category.SMELTING),
    )

val pipeRecipe =
    Recipe(
        name = "pipe",
        ingredients = mapOf(ironPlate to 1.0),
        result = mapOf(pipe to 1.0),
        time = 0.5,
        categories = setOf(Category.CRAFTING),
    )

val transportBeltRecipe =
    Recipe(
        name = "transport-belt",
        ingredients = mapOf(ironPlate to 1.0, gear to 1.0),
        result = mapOf(transportBelt to 2.0),
        time = 0.5,
        categories = setOf(Category.CRAFTING, Category.METALLURGY),
    )

val splitterRecipe =
    Recipe(
        name = "splitter",
        ingredients = mapOf(electronicCircuit to 5.0, ironPlate to 5.0, transportBelt to 4.0),
        result = mapOf(splitter to 1.0),
        time = 1.0,
        categories = setOf(Category.CRAFTING, Category.METALLURGY),
    )

val uraniumRoundsMagazineRecipe =
    Recipe(
        name = "uranium-rounds-magazine",
        ingredients = mapOf(piercingRoundsMagazine to 1.0, uranium238 to 1.0),
        result = mapOf(uraniumRoundsMagazine to 1.0),
        time = 10.0,
        categories = setOf(Category.CRAFTING),
    )

val nuclearFuelRecipe =
    Recipe(
        name = "nuclear-fuel",
        ingredients = mapOf(uranium235 to 1.0, rocketFuel to 1.0),
        result = mapOf(nuclearFuel to 1.0),
        time = 90.0,
        categories = setOf(Category.CENTRIFUGING),
    )

val ironOreMeltingRecipe =
    Recipe(
        name = "iron-ore-melting",
        ingredients = mapOf(ironOre to 50.0, calcite to 1.0),
        result = mapOf(moltenIron to 500.0),
        time = 32.0,
        categories = setOf(Category.METALLURGY),
    )

val moltenIronFromLavaRecipe =
    Recipe(
        name = "molten-iron-from-lava",
        ingredients = mapOf(lava to 500.0, calcite to 1.0),
        result = mapOf(moltenIron to 250.0, stone to 10.0),
        time = 16.0,
        categories = setOf(Category.METALLURGY),
        subgroup = Subgroup("vulcanus-processes"),
    )

val castingSteelRecipe =
    Recipe(
        name = "casting-steel",
        ingredients = mapOf(moltenIron to 30.0),
        result = mapOf(steelPlate to 1.0),
        time = 3.2,
        categories = setOf(Category.METALLURGY),
    )

val castingIronRecipe =
    Recipe(
        name = "casting-iron",
        ingredients = mapOf(moltenIron to 20.0),
        result = mapOf(ironPlate to 2.0),
        time = 3.2,
        categories = setOf(Category.METALLURGY),
        subgroup = Subgroup("vulcanus-processes"),
    )

val copperPlateSmeltingRecipe =
    Recipe(
        name = "copper-plate",
        ingredients = mapOf(copperOre to 1.0),
        result = mapOf(copperPlate to 1.0),
        time = 3.2,
        categories = setOf(Category.SMELTING),
    )

val iceMeltingRecipe =
    Recipe(
        name = "ice-melting",
        ingredients = mapOf(ice to 1.0),
        result = mapOf(water to 20.0),
        time = 1.0,
        categories = setOf(Category.CHEMISTRY),
    )

val foundryRecipe =
    Recipe(
        name = "foundry",
        ingredients =
            mapOf(
                tungstenCarbide to 50.0,
                steelPlate to 50.0,
                electronicCircuit to 30.0,
                refinedConcrete to 20.0,
                lubricant to 20.0,
            ),
        result = mapOf(foundry to 1.0),
        time = 10.0,
        categories = setOf(Category.METALLURGY, Category.CRAFTING_WITH_FLUID),
        surfaceConditions = listOf(SurfaceCondition(SurfaceProperty.PRESSURE, 4000..4000)),
    )

val quantumProcessorRecipe =
    Recipe(
        name = "quantum-processor",
        ingredients =
            mapOf(
                tungstenCarbide to 1.0,
                processingUnit to 1.0,
                superconductor to 1.0,
                carbonFiber to 1.0,
                lithiumPlate to 2.0,
                fluoroketoneCold to 10.0,
            ),
        result = mapOf(quantumProcessor to 1.0, fluoroketoneHot to 5.0),
        time = 30.0,
        categories = setOf(Category.ELECTROMAGNETICS),
        subgroup = Subgroup("aquilo-processes"),
        surfaceConditions = listOf(SurfaceCondition(SurfaceProperty.PRESSURE, Int.MIN_VALUE..600)),
    )

val railgunRecipe =
    Recipe(
        name = "railgun",
        ingredients =
            mapOf(
                tungstenPlate to 10.0,
                superconductor to 10.0,
                quantumProcessor to 20.0,
                fluoroketoneCold to 10.0,
            ),
        result = mapOf(railgun to 1.0),
        time = 10.0,
        categories = setOf(Category.CRYOGENICS),
    )

val ironGearWheelRecyclingRecipe =
    Recipe(
        name = "iron-gear-wheel-recycling",
        ingredients = mapOf(gear to 1.0),
        result = mapOf(ironPlate to 0.5),
        time = 0.03125,
        categories = setOf(Category.RECYCLING),
    )

val ironBacteriaRecipe =
    Recipe(
        name = "iron-bacteria",
        ingredients = mapOf(jelly to 6.0),
        result = mapOf(ironBacteria to 0.1, spoilage to 4.0),
        time = 1.0,
        categories = setOf(Category.ORGANIC, Category.CRAFTING),
        subgroup = Subgroup("agriculture-processes"),
        surfaceConditions = listOf(SurfaceCondition(SurfaceProperty.PRESSURE, 2000..2000)),
    )

val ironBacteriaSpoilingRecipe =
    Recipe(
        name = "iron-bacteria-spoiling",
        ingredients = mapOf(ironBacteria to 1.0),
        result = mapOf(ironOre to 1.0),
        time = 60.0,
        categories = setOf(Category.SPOILING),
    )

val copperBacteriaSpoilingRecipe =
    Recipe(
        name = "copper-bacteria-spoiling",
        ingredients = mapOf(copperBacteria to 1.0),
        result = mapOf(copperOre to 1.0),
        time = 60.0,
        categories = setOf(Category.SPOILING),
    )

val explosivesRecipe =
    Recipe(
        name = "explosives",
        ingredients = mapOf(sulfur to 1.0, coal to 1.0, water to 10.0),
        result = mapOf(explosives to 2.0),
        time = 4.0,
        categories = setOf(Category.CHEMISTRY, Category.CRYOGENICS),
    )

val advancedOilProcessingRecipe =
    Recipe(
        name = "advanced-oil-processing",
        ingredients = mapOf(water to 50.0, crudeOil to 100.0),
        result = mapOf(heavyOil to 25.0, lightOil to 45.0, petroleum to 55.0),
        time = 5.0,
        categories = setOf(Category.OIL_PROCESSING),
    )

val solidFuelFromPetroleumGasRecipe =
    Recipe(
        name = "solid-fuel-from-petroleum-gas",
        ingredients = mapOf(petroleum to 20.0),
        result = mapOf(solidFuel to 1.0),
        time = 1.0,
        categories = setOf(Category.CHEMISTRY),
    )

val lubricantRecipe =
    Recipe(
        name = "lubricant",
        ingredients = mapOf(heavyOil to 10.0),
        result = mapOf(lubricant to 10.0),
        time = 1.0,
        categories = setOf(Category.CHEMISTRY),
    )

val lightOilCrackingRecipe =
    Recipe(
        name = "light-oil-cracking",
        ingredients = mapOf(water to 30.0, lightOil to 30.0),
        result = mapOf(petroleum to 20.0),
        time = 2.0,
        categories = setOf(Category.CHEMISTRY, Category.ORGANIC),
    )

val steamCondensationRecipe =
    Recipe(
        name = "steam-condensation",
        ingredients = mapOf(steam to 1000.0),
        result = mapOf(water to 90.0),
        time = 1.0,
        categories = setOf(Category.CHEMISTRY, Category.CRYOGENICS),
    )

val processingUnitRecipe =
    Recipe(
        name = "processing-unit",
        ingredients = mapOf(electronicCircuit to 20.0, advancedCircuit to 2.0, sulfuricAcid to 5.0),
        result = mapOf(processingUnit to 1.0),
        time = 10.0,
        categories = setOf(Category.CRAFTING_WITH_FLUID, Category.ELECTROMAGNETICS),
    )

val coalLiquefactionRecipe =
    Recipe(
        name = "coal-liquefaction",
        ingredients = mapOf(coal to 10.0, heavyOil to 25.0, steam to 50.0),
        result = mapOf(heavyOil to 90.0, lightOil to 20.0, petroleum to 10.0),
        time = 5.0,
        categories = setOf(Category.OIL_PROCESSING),
    )

val rocketSiloRecipe =
    Recipe(
        name = "rocket-silo",
        ingredients =
            mapOf(
                steelPlate to 1000.0,
                concrete to 1000.0,
                pipe to 100.0,
                processingUnit to 200.0,
                electricEngineUnit to 200.0,
            ),
        result = mapOf(rocketSilo to 1.0),
        time = 30.0,
        categories = setOf(Category.CRAFTING),
    )

val rocketFuelRecipe =
    Recipe(
        name = "rocket-fuel",
        ingredients = mapOf(solidFuel to 10.0, lightOil to 10.0),
        result = mapOf(rocketFuel to 1.0),
        time = 15.0,
        categories = setOf(Category.CRAFTING_WITH_FLUID, Category.ORGANIC),
    )

// Extraction recipes are generated from `data.raw` resources, tile fluids and harvestable items;
// they have no `data.raw.recipe` entry of their own.
val ironOreMiningRecipe =
    Recipe(
        name = "iron-ore",
        ingredients = emptyMap(),
        result = mapOf(ironOre to 1.0),
        time = 1.0,
        categories = setOf(Category.MINING_BASIC_SOLID),
    )

val tungstenOreMiningRecipe =
    Recipe(
        name = "tungsten-ore",
        ingredients = emptyMap(),
        result = mapOf(tungstenOre to 1.0),
        time = 5.0,
        categories = setOf(Category.MINING_HARD_SOLID),
    )

val coalMiningRecipe =
    Recipe(
        name = "coal",
        ingredients = emptyMap(),
        result = mapOf(coal to 1.0),
        time = 1.0,
        categories = setOf(Category.MINING_BASIC_SOLID),
    )

val stoneMiningRecipe =
    Recipe(
        name = "stone",
        ingredients = emptyMap(),
        result = mapOf(stone to 1.0),
        time = 1.0,
        categories = setOf(Category.MINING_BASIC_SOLID),
    )

val calciteMiningRecipe =
    Recipe(
        name = "calcite",
        ingredients = emptyMap(),
        result = mapOf(calcite to 1.0),
        time = 1.0,
        categories = setOf(Category.MINING_BASIC_SOLID),
    )

val waterPumpingRecipe =
    Recipe(
        name = "water",
        ingredients = emptyMap(),
        result = mapOf(water to 1.0),
        time = 1.0,
        categories = setOf(Category.OFFSHORE_PUMPING),
    )

val heavyOilPumpingRecipe =
    Recipe(
        name = "heavy-oil",
        ingredients = emptyMap(),
        result = mapOf(heavyOil to 1.0),
        time = 1.0,
        categories = setOf(Category.OFFSHORE_PUMPING),
    )

val lavaPumpingRecipe =
    Recipe(
        name = "lava",
        ingredients = emptyMap(),
        result = mapOf(lava to 1.0),
        time = 1.0,
        categories = setOf(Category.OFFSHORE_PUMPING),
    )

val ammoniacalSolutionPumpingRecipe =
    Recipe(
        name = "ammoniacal-solution",
        ingredients = emptyMap(),
        result = mapOf(ammoniacalSolution to 1.0),
        time = 1.0,
        categories = setOf(Category.OFFSHORE_PUMPING),
    )

val yumakoHarvestingRecipe =
    Recipe(
        name = "yumako",
        ingredients = emptyMap(),
        result = mapOf(yumako to 50.0),
        time = 300.0,
        categories = setOf(Category.HARVESTING),
    )

val jellynutHarvestingRecipe =
    Recipe(
        name = "jellynut",
        ingredients = emptyMap(),
        result = mapOf(jellynut to 50.0),
        time = 300.0,
        categories = setOf(Category.HARVESTING),
    )

val woodHarvestingRecipe =
    Recipe(
        name = "wood",
        ingredients = emptyMap(),
        result = mapOf(wood to 4.0),
        time = 600.0,
        categories = setOf(Category.HARVESTING),
    )
