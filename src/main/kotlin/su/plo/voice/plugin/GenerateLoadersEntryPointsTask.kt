package su.plo.voice.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
import su.plo.voice.plugin.entrypoint.AddonEntryPoint
import su.plo.voice.plugin.util.parseAddonMeta
import java.io.File

//@CacheableTask
open class GenerateLoadersEntryPointsTask : DefaultTask() {

//    init {
//        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
//        val mainSourceSet = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
//
//        inputs.files(mainSourceSet.java.sourceDirectories.asFileTree)
//        outputs.files(File(project.buildDir, CACHE_FILE_PATH))
//        outputs.cacheIf { true }
//    }

    @TaskAction
    fun action() {
        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        val mainSourceSet = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)

        val kotlinMainSourceSet = project.kotlinExtension.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)

        val sourceFiles = HashSet(mainSourceSet.java.sourceDirectories.asFileTree.files)
            .plus(kotlinMainSourceSet.kotlin.sourceDirectories.asFileTree.files)

        val addons = sourceFiles.mapNotNull { it.parseAddonMeta() }
        if (addons.isEmpty()) return

        AddonEntryPoint.processAddons(project, addons)
        File(project.buildDir, CACHE_FILE_PATH).writeText("")
    }

    companion object {

        private const val CACHE_FILE_PATH = "generated/sources/plasmovoice/.cache"
    }
}
