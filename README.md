# Factorio-Optimizer

[![pipeline status](https://gitlab.com/bjrke/factorio-optimizer/badges/main/pipeline.svg)](https://gitlab.com/bjrke/factorio-optimizer/-/pipelines/latest?ref=main)

#### Website
<https://bjrke.gitlab.io/factorio-optimizer>

#### Predefined Run Configs of Intellij:
`./gradlew jsBrowserProductionRun --continuous` or
`./gradlew jsBrowserDevelopmentRun --continuous`

#### recipes / prototypes
The recipe and prototype data is dumped from the factorio data stage with `factorio --dump-data`
for two configurations: the base game and base plus the Space Age DLC mods (`elevated-rails`,
`quality`, `recycler`, `space-age`). The resolved `data.raw` JSON is stored in
`src/commonMain/resources/prototypes/{base,space-age}.json`. Because the game resolves the whole data
stage itself, the dump already contains everything `data-updates.lua` / `data-final-fixes.lua`
change; the optimizer reads categories and recipe fields straight from it and ignores the prototype
types it does not need. Luckily there is a gradle task for it (it requires a factorio install).

`./gradlew dumpPrototypes`

At runtime the web UI and the CLI use the base dump, or the Space Age dump when the Space Age mod is
active.

#### Updating Icons

You need a linux factorio steam installation on your system to grab some files like:
`~/.steam/steam/steamapps/common/Factorio/data/base/graphics/icons/advanced-circuit.png`

You also need some tools installed:
```shell
sudo apt install imagemagick optipng pngquant 
```

run `./gradlew updateImages` to copy and optimize new or significantly changed resource and recipe icons from all mods into the resource folder.
