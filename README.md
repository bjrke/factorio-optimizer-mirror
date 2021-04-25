#### Needed Factorio installation files

You need a factorio installation on your system to grab some files:
`~/.steam/steam/steamapps/common/Factorio/data/base/graphics/icons/advanced-circuit.png`
`~/.steam/steam/steamapps/common/Factorio/data/base/prototypes/recipe.lua`

run `./gradlew copyImages` to copy resource and recipe icons into the resource folder.

#### Predefined Run Configs of Intellij:
`./gradlew browserProductionRun --continuous` or
`./gradlew browserDevelopmentRun --continuous`

#### recipe.json
Install Mod <https://mods.factorio.com/mod/recipelister> by copying the zip it into `~/.factorio/mods`
The export happens when you start a game with activated mod
The file can be found in `~/.factorio/script-output/recipe-lister/recipe.json`
