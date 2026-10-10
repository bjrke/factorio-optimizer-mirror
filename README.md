# Factorio-Optimizer

#### Website
<https://bjrke.gitlab.io/factorio-optimizer>

#### Predefined Run Configs of Intellij:
`./gradlew jsBrowserProductionRun --continuous` or
`./gradlew jsBrowserDevelopmentRun --continuous`

#### recipe.lua
The recipes are scraped from **all** mods in the factorio data folder (`base`, `core`, `quality`,
`recycler`, `elevated-rails`, `space-age`, ...) into `src/commonMain/resources/recipes/<mod>/...`.
Each mod keeps its own recipe file(s), plus a generated `mods.txt` that records mod dependencies.
Luckily there is a gradle task for it.

`./gradlew copyRecipes`

At runtime the web UI and the CLI let you pick which mods are active (dependencies are resolved
automatically, e.g. `space-age` enables `recycler` and `base`).

#### Updating Icons

You need a linux factorio steam installation on your system to grab some files like:
`~/.steam/steam/steamapps/common/Factorio/data/base/graphics/icons/advanced-circuit.png`

You also need some tools installed:
```shell
sudo apt install imagemagick optipng pngquant 
```

run `./gradlew updateImages` to copy and optimize new or significantly changed resource and recipe icons from all mods into the resource folder.
