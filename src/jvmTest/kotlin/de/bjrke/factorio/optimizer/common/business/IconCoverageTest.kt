package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.BeltEnd
import de.bjrke.factorio.optimizer.common.model.BeltTier
import de.bjrke.factorio.optimizer.common.model.Factory
import de.bjrke.factorio.optimizer.common.model.InserterTier
import de.bjrke.factorio.optimizer.common.model.iconLayers
import de.bjrke.test.assertTable
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Verifies that every icon the web UI can render points at an icon file that actually ships in
 * `src/jsMain/resources/icons`. The UI names each icon after its prototype (see [iconLayers]), so a
 * prototype whose icon file is named differently silently renders a broken image; this test catches
 * that for every material, recipe, machine and logistics prototype.
 */
class IconCoverageTest {

    @Test
    fun everyIconTheUiCanRenderHasAFile() {
        val dir = iconsDirectory()
        val cases = iconNames().flatMap { name -> iconLayers(name).map { (file) -> name to file } }
        assertTable(cases) { (name, fileBaseName) ->
            val file = File(dir, "$fileBaseName.png")
            assertTrue(file.isFile, "$file should exist to show $name")
        }
    }

    /** Every prototype name the UI passes to `icon(...)`, for the base game and Space Age. */
    private fun iconNames(): Set<String> = buildSet {
        for (spaceAge in listOf(true, false)) {
            val parsed = RecipeProvider.forActiveMods(spaceAge)
            addAll(parsed.materials.map { it.name })
            addAll(parsed.recipes.map { it.name })
        }
        addAll(Factory.entries.map { it.identifier })
        addAll(BeltTier.entries.map { it.identifier })
        addAll(InserterTier.entries.map { it.identifier })
        addAll(BeltEnd.entries.map { it.identifier })
        // fixed icons the UI renders without a prototype behind them
        addAll(listOf("pipe", "recycling", "recycling-top", "spoilage", "space-age"))
    }

    /**
     * Locates `src/jsMain/resources/icons` from the test working directory, walking up so the test
     * also runs when Gradle or an IDE launches it from a subdirectory.
     */
    private fun iconsDirectory(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val candidate = File(dir, "src/jsMain/resources/icons")
            if (candidate.isDirectory) return candidate
            dir = dir.parentFile
        }
        error("could not locate src/jsMain/resources/icons from ${System.getProperty("user.dir")}")
    }
}
