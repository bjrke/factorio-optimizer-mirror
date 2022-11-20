# Factorio-Optimizer

#### Website
<https://bjrke.gitlab.io/factorio-optimizer>

#### Predefined Run Configs of Intellij:
`./gradlew jsBrowserProductionRun --continuous` or
`./gradlew jsBrowserDevelopmentRun --continuous`

#### recipe.lua
The recipes have to be copied from `data/base/prototypes/recipe.lua` in the factorio folder into the recipes directory `src/commonMain/resources/recipes`.
Luckily there is a gradle task for it.

`./gradlew copyRecipes`

#### Updating Icons

You need a linux factorio steam installation on your system to grab some files like:
`~/.steam/steam/steamapps/common/Factorio/data/base/graphics/icons/advanced-circuit.png`

You also need some tools installed:
```shell
sudo apt install imagemagick optipng pngquant 
```

run `./gradlew optimizeImages` to copy and optimize resource and recipe icons into the resource folder.
