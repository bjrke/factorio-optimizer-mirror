package de.bjrke.factorio.optimizer.buildlogic

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.logging.Logging

private const val imageChangedThreshold = 200_000

private val LOGGER = Logging.getLogger("de.bjrke.factorio.optimizer.buildlogic.updateImages")

fun updateImages(iconsDir: File, sources: ConfigurableFileTree) {
    iconsDir.mkdirs()

    val chosen = LinkedHashMap<String, Pair<File, BufferedImage>>()
    sources.files
        .sortedBy { it.path }
        .forEach { source ->
            val img = ImageIO.read(source)
            if (img == null || img.width != 120 || img.height != 64) return@forEach
            val previous = chosen.putIfAbsent(source.name, source to img)
            if (previous != null) {
                LOGGER.warn(
                    "Duplicate icon name '${source.name}': using ${previous.first.path}, ignoring ${source.path}",
                )
            }
        }

    val changedNames =
        chosen
            .filter { (name, image) -> significantlyChanged(image.second, File(iconsDir, name)) }
            .keys
            .toList()
    changedNames.forEach { name ->
        chosen.getValue(name).first.copyTo(File(iconsDir, name), overwrite = true)
    }
    if (changedNames.isEmpty()) {
        LOGGER.lifecycle("No significantly changed icons found")
        return
    }

    val files = changedNames.joinToString(" ") { "'$it'" }
    val script =
        """
            mogrify -crop 64x64+0+0 $files
            chmod 664 $files
            pngquant --strip --speed 1 --ext=.png --force --skip-if-larger $files
            optipng -o7 -zm1-9 $files
            """
            .trimIndent()
    val exitCode =
        ProcessBuilder("sh", "-c", script).directory(iconsDir).inheritIO().start().waitFor()
    check(exitCode == 0) { "icon optimization failed (exit $exitCode)" }
}

private fun significantlyChanged(img: BufferedImage, target: File): Boolean {
    if (!target.exists()) return true
    val old = ImageIO.read(target) ?: return true
    if (old.width != 64 || old.height != 64) return true
    var difference = 0
    for (y in 0 until 64) {
        for (x in 0 until 64) {
            val a = img.getRGB(x, y)
            val b = old.getRGB(x, y)
            val aAlpha = a ushr 24
            val bAlpha = b ushr 24
            difference +=
                maxOf(
                    abs((a shr 16 and 0xff) * aAlpha / 255 - (b shr 16 and 0xff) * bAlpha / 255),
                    abs((a shr 8 and 0xff) * aAlpha / 255 - (b shr 8 and 0xff) * bAlpha / 255),
                    abs((a and 0xff) * aAlpha / 255 - (b and 0xff) * bAlpha / 255),
                    abs(aAlpha - bAlpha),
                )
        }
    }
    return difference > imageChangedThreshold
}
