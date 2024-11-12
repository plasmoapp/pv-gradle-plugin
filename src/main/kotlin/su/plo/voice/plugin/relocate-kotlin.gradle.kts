package su.plo.voice.plugin

plugins {
    java
    id("com.gradleup.shadow")
}

tasks {
    shadowJar {
        relocate("kotlin", "su.plo.voice.libs.kotlin")
        relocate("kotlinx.coroutines", "su.plo.voice.libs.kotlinx.coroutines")
        relocate("kotlinx.serialization", "su.plo.voice.libs.kotlinx.serialization")
    }

    build {
        dependsOn(shadowJar)
    }
}
