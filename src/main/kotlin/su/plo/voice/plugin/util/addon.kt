package su.plo.voice.plugin.util

import su.plo.voice.plugin.AddonMeta
import su.plo.voice.plugin.parser.JavaAddonParser
import su.plo.voice.plugin.parser.KotlinAddonParser
import java.io.File

fun File.parseAddonMeta(sourceFiles: Collection<File>) = parseAddon(sourceFiles, this)

private fun parseAddon(sourceFiles: Collection<File>, file: File): AddonMeta? {
    val fileName = file.name

    if (fileName.endsWith(".java")) {
        return JavaAddonParser.parseAddon(sourceFiles, file)
    } else if (fileName.endsWith(".kt")) {
        return KotlinAddonParser.parseAddon(sourceFiles, file)
    }

    return null
}

