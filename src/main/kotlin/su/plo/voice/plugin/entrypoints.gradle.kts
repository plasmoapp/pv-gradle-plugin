package su.plo.voice.plugin

import org.gradle.api.tasks.SourceSet
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension

plugins {
    java // apply java plugin to compile generated code properly
    id("su.plo.voice.plugin.relocate-kotlin")
}

val extension = project.extensions.create("entrypoints", EntryPointsExtension::class)

val sourceSets = extensions.getByType(SourceSetContainer::class.java)
val mainSourceSet = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)

val generateLoadersEntrypoints = tasks.register("generateLoadersEntrypoints", GenerateLoadersEntryPointsTask::class.java) {
    sources.from(mainSourceSet.java.sourceDirectories)
    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        sources.from(kotlinExtension.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME).kotlin.sourceDirectories)
    }

    paperPlugin.set(provider { extension.paperPlugin })
    paperOpenClassloader.set(provider { extension.paperOpenClassloader })
    minPlasmoVoiceVersion.set(provider { extension.minPlasmoVoiceVersion })

    outputDir.set(layout.buildDirectory.dir("generated/sources/plasmovoice"))
}

mainSourceSet.resources.srcDir(generateLoadersEntrypoints.flatMap { it.outputDir.dir("classes") })
mainSourceSet.resources.srcDir(generateLoadersEntrypoints.flatMap { it.outputDir.dir("resources") })
