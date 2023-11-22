# su.plo.voice.plugin.relocate-kotlin
<img alt="version" src="https://img.shields.io/badge/dynamic/xml?label=%20&query=/metadata/versioning/versions/version[not(contains(text(),'%2B'))][last()]&url=https://repo.plasmoverse.com/snapshots/su/plo/voice/plugin/pv-gradle-plugin/maven-metadata.xml">

Gradle plugin for relocating Kotlin, so you can use Kotlin bundled with [Plasmo Voice](https://github.com/plasmoapp/plasmo-voice). 

`settings.gradle.kts`
```kotlin
pluginManagement {
    repositories {
        maven("https://repo.plasmoverse.com/snapshots")
    }
}
```

`build.gradle.kts`
```kotlin
plugins {
    id("su.plo.voice.plugin.relocate-kotlin") version "${version}"
}
```

# su.plo.voice.plugin.entrypoints
<img alt="version" src="https://img.shields.io/badge/dynamic/xml?label=%20&query=/metadata/versioning/versions/version[not(contains(text(),'%2B'))][last()]&url=https://repo.plasmoverse.com/snapshots/su/plo/voice/plugin/pv-gradle-plugin/maven-metadata.xml">

Gradle plugin for generating [Plasmo Voice](https://github.com/plasmoapp/plasmo-voice) universal add-ons entrypoints.

You only need to add the `su.plo.voice.plugin` Gradle plugin, and entrypoints will be generated and added to source sets.

Note: 
This plugin adds all platforms (Spigot/BungeeCord/Velocity/Fabric/Forge) to your project classpath.

`settings.gradle.kts`
```kotlin
pluginManagement {
    repositories {
        maven("https://repo.plasmoverse.com/snapshots")
    }
}
```

`build.gradle.kts`
```kotlin
plugins {
    kotlin("jvm") version("1.8.22") // you also need to add kotlin plugin
    id("su.plo.voice.plugin.entrypoints") version "${version}"
}
```
