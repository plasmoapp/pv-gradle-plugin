package su.plo.voice.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import su.plo.voice.plugin.entrypoint.AddonEntryPoint
import su.plo.voice.plugin.util.parseAddonMeta

@CacheableTask
abstract class GenerateLoadersEntryPointsTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:Input
    abstract val paperPlugin: Property<Boolean>

    @get:Input
    abstract val paperOpenClassloader: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val minPlasmoVoiceVersion: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun action() {
        val outputDir = outputDir.get().asFile

        // entrypoints of a platform that is no longer generated would stay in the jar otherwise
        outputDir.deleteRecursively()

        val sourceFiles = sources.asFileTree.files
        val addons = sourceFiles.mapNotNull { it.parseAddonMeta(sourceFiles) }
        if (addons.isEmpty()) return

        AddonEntryPoint.processAddons(
            outputDir,
            addons,
            paperPlugin.get(),
            paperOpenClassloader.get(),
            minPlasmoVoiceVersion.orNull,
        )
    }
}
