package su.plo.voice.plugin

import org.gradle.api.tasks.SourceSet

plugins {
    java // apply java plugin to compile generated code properly
    id("su.plo.voice.plugin.relocate-kotlin")
}

project.extensions.create("entrypoints", EntryPointsExtension::class)

val generateLoadersEntrypoints = tasks.register("generateLoadersEntrypoints", GenerateLoadersEntryPointsTask::class.java)

val sourceSets = extensions.getByType(SourceSetContainer::class.java)
val mainSourceSet = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)

mainSourceSet.resources.srcDir(layout.buildDirectory.dir("generated/sources/plasmovoice/classes"))
mainSourceSet.resources.srcDir(layout.buildDirectory.dir("generated/sources/plasmovoice/resources"))

tasks {
    named("classes") { dependsOn(generateLoadersEntrypoints) }
}
