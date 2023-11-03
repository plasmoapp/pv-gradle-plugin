package su.plo.voice.plugin.parser

import su.plo.voice.plugin.AddonMeta
import java.io.File

interface AddonParser {

    fun parseAddon(file: File): AddonMeta?
}
