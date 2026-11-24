package de.bjrke.factorio.optimizer.common.fixtures

/**
 * The real fluids the fixture recipes use. `BaseGameCtxTest` marks them in its registry, so a
 * synthetic recipe sees the same fluid/solid split the dump gives the real prototypes.
 */
internal val TEST_FLUID_NAMES =
    setOf(
        "water",
        "steam",
        "sulfuric-acid",
        "crude-oil",
        "heavy-oil",
        "light-oil",
        "petroleum-gas",
        "lubricant",
        "ammoniacal-solution",
        "ammonia",
        "fluorine",
        "fluoroketone-cold",
        "fluoroketone-hot",
        "holmium-solution",
        "electrolyte",
        "lithium-brine",
        "lava",
        "molten-iron",
        "molten-copper",
        "thruster-fuel",
        "thruster-oxidizer",
        "fusion-plasma",
    )

val GameCtxTestMixin.speedModule
    get() = Material("speed-module")
val GameCtxTestMixin.ironOre
    get() = Material("iron-ore")
val GameCtxTestMixin.copperOre
    get() = Material("copper-ore")
val GameCtxTestMixin.tungstenOre
    get() = Material("tungsten-ore")
val GameCtxTestMixin.copperCable
    get() = Material("copper-cable")
val GameCtxTestMixin.copperPlate
    get() = Material("copper-plate")
val GameCtxTestMixin.electronicCircuit
    get() = Material("electronic-circuit")
val GameCtxTestMixin.advancedCircuit
    get() = Material("advanced-circuit")
val GameCtxTestMixin.processingUnit
    get() = Material("processing-unit")
val GameCtxTestMixin.coal
    get() = Material("coal")
val GameCtxTestMixin.ironPlate
    get() = Material("iron-plate")
val GameCtxTestMixin.gear
    get() = Material("iron-gear-wheel")
val GameCtxTestMixin.pipe
    get() = Material("pipe")
val GameCtxTestMixin.steelPlate
    get() = Material("steel-plate")
val GameCtxTestMixin.engineUnit
    get() = Material("engine-unit")
val GameCtxTestMixin.electricEngineUnit
    get() = Material("electric-engine-unit")
val GameCtxTestMixin.flyingRobotFrame
    get() = Material("flying-robot-frame")
val GameCtxTestMixin.battery
    get() = Material("battery")
val GameCtxTestMixin.smallElectricPole
    get() = Material("small-electric-pole")
val GameCtxTestMixin.wood
    get() = Material("wood")
val GameCtxTestMixin.water
    get() = Material("water")
val GameCtxTestMixin.lubricant
    get() = Material("lubricant")
val GameCtxTestMixin.sulfuricAcid
    get() = Material("sulfuric-acid")
val GameCtxTestMixin.sulfur
    get() = Material("sulfur")
val GameCtxTestMixin.plasticBar
    get() = Material("plastic-bar")
val GameCtxTestMixin.crudeOil
    get() = Material("crude-oil")
val GameCtxTestMixin.transportBelt
    get() = Material("transport-belt")
val GameCtxTestMixin.splitter
    get() = Material("splitter")
val GameCtxTestMixin.petroleum
    get() = Material("petroleum-gas")
val GameCtxTestMixin.utilitySciencePack
    get() = Material("utility-science-pack")
val GameCtxTestMixin.ice
    get() = Material("ice")
val GameCtxTestMixin.efficiencyModule2
    get() = Material("efficiency-module-2")
val GameCtxTestMixin.efficiencyModule
    get() = Material("efficiency-module")
val GameCtxTestMixin.ironBacteria
    get() = Material("iron-bacteria")
val GameCtxTestMixin.copperBacteria
    get() = Material("copper-bacteria")
val GameCtxTestMixin.yumako
    get() = Material("yumako")
val GameCtxTestMixin.jellynut
    get() = Material("jellynut")
val GameCtxTestMixin.moltenIron
    get() = Material("molten-iron")
val GameCtxTestMixin.heavyOil
    get() = Material("heavy-oil")
val GameCtxTestMixin.ammoniacalSolution
    get() = Material("ammoniacal-solution")
val GameCtxTestMixin.holmiumSolution
    get() = Material("holmium-solution")
val GameCtxTestMixin.electrolyte
    get() = Material("electrolyte")
val GameCtxTestMixin.spaceScience
    get() = Material("space-science-pack")
val GameCtxTestMixin.satellite
    get() = Material("satellite")
val GameCtxTestMixin.quantumProcessor
    get() = Material("quantum-processor")
val GameCtxTestMixin.tungstenPlate
    get() = Material("tungsten-plate")
val GameCtxTestMixin.railgun
    get() = Material("railgun")
val GameCtxTestMixin.lava
    get() = Material("lava")
val GameCtxTestMixin.calcite
    get() = Material("calcite")
val GameCtxTestMixin.assemblingMachine1
    get() = Material("assembling-machine-1")
val GameCtxTestMixin.carbon
    get() = Material("carbon")
val GameCtxTestMixin.rawFish
    get() = Material("raw-fish")
val GameCtxTestMixin.nutrients
    get() = Material("nutrients")
val GameCtxTestMixin.bioflux
    get() = Material("bioflux")
val GameCtxTestMixin.uranium235
    get() = Material("uranium-235")
val GameCtxTestMixin.piercingRoundsMagazine
    get() = Material("piercing-rounds-magazine")
val GameCtxTestMixin.uraniumRoundsMagazine
    get() = Material("uranium-rounds-magazine")
val GameCtxTestMixin.metallicAsteroidChunk
    get() = Material("metallic-asteroid-chunk")
val GameCtxTestMixin.carbonicAsteroidChunk
    get() = Material("carbonic-asteroid-chunk")
val GameCtxTestMixin.oxideAsteroidChunk
    get() = Material("oxide-asteroid-chunk")
val GameCtxTestMixin.promethiumAsteroidChunk
    get() = Material("promethium-asteroid-chunk")
val GameCtxTestMixin.scrap
    get() = Material("scrap")
val GameCtxTestMixin.yumakoMash
    get() = Material("yumako-mash")
val GameCtxTestMixin.spoilage
    get() = Material("spoilage")
val GameCtxTestMixin.stone
    get() = Material("stone")
val GameCtxTestMixin.stoneBrick
    get() = Material("stone-brick")
val GameCtxTestMixin.concrete
    get() = Material("concrete")
val GameCtxTestMixin.refinedConcrete
    get() = Material("refined-concrete")
val GameCtxTestMixin.oilRefinery
    get() = Material("oil-refinery")
val GameCtxTestMixin.rocketSilo
    get() = Material("rocket-silo")
val GameCtxTestMixin.rocketPart
    get() = Material("rocket-part")
val GameCtxTestMixin.lowDensityStructure
    get() = Material("low-density-structure")
val GameCtxTestMixin.tungstenCarbide
    get() = Material("tungsten-carbide")
val GameCtxTestMixin.superconductor
    get() = Material("superconductor")
val GameCtxTestMixin.carbonFiber
    get() = Material("carbon-fiber")
val GameCtxTestMixin.lithiumPlate
    get() = Material("lithium-plate")
val GameCtxTestMixin.fluoroketoneCold
    get() = Material("fluoroketone-cold")
val GameCtxTestMixin.fluoroketoneHot
    get() = Material("fluoroketone-hot")
val GameCtxTestMixin.explosives
    get() = Material("explosives")
val GameCtxTestMixin.jelly
    get() = Material("jelly")
val GameCtxTestMixin.solarPanel
    get() = Material("solar-panel")
val GameCtxTestMixin.solidFuel
    get() = Material("solid-fuel")
val GameCtxTestMixin.rocketFuel
    get() = Material("rocket-fuel")
val GameCtxTestMixin.nuclearFuel
    get() = Material("nuclear-fuel")
val GameCtxTestMixin.biterEgg
    get() = Material("biter-egg")
val GameCtxTestMixin.lightOil
    get() = Material("light-oil")
val GameCtxTestMixin.steam
    get() = Material("steam")
val GameCtxTestMixin.foundry
    get() = Material("foundry")
val GameCtxTestMixin.flourine
    get() = Material("fluorine")
val GameCtxTestMixin.uranium238
    get() = Material("uranium-238")
val GameCtxTestMixin.uraniumFuelCell
    get() = Material("uranium-fuel-cell")
val GameCtxTestMixin.uraniumOre
    get() = Material("uranium-ore")
