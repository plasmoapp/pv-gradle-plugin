package su.plo.voice.plugin.util

import su.plo.voice.plugin.AddonMeta
import su.plo.voice.plugin.parser.JavaAddonParser
import su.plo.voice.plugin.parser.KotlinAddonParser
import java.io.File

fun File.parseAddonMeta() = parseAddon(this)

private fun parseAddon(file: File): AddonMeta? {
    val fileName = file.name

    if (fileName.endsWith(".java")) {
        return JavaAddonParser.parseAddon(file)
    } else if (fileName.endsWith(".kt")) {
        return KotlinAddonParser.parseAddon(file)
    }

    return null
}

