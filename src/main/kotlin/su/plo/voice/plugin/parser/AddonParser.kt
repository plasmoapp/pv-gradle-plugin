package su.plo.voice.plugin.parser

import su.plo.voice.plugin.AddonMeta
import java.io.File

interface AddonParser {

    fun parseAddon(sourceFiles: Collection<File>, file: File): AddonMeta?
}
