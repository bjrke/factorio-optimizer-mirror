#### Predefined Run Configs of Intellij:
`./gradlew browserProductionRun --continuous` or
`./gradlew browserDevelopmentRun --continuous`

#### recipe.json
Install Mod <https://mods.factorio.com/mod/recipelister> by copying the zip it into `~/.factorio/mods`
The export happens when you start a game with activated mod
The file can be found in `~/.factorio/script-output/recipe-lister/recipe.json`

#### Updating Icons

You need a linux factorio steam installation on your system to grab some files like:
`~/.steam/steam/steamapps/common/Factorio/data/base/graphics/icons/advanced-circuit.png`

You also need some tools installed:
```shell
sudo apt install imagemagick optipng pngquant 
```

run `./gradlew optimizeImages` to copy and optimize resource and recipe icons into the resource folder.
