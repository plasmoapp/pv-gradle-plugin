plugins {
    `kotlin-dsl`
    `maven-publish`
}

dependencies {
    implementation(gradleApi())
    implementation(localGroovy())

    compileOnly(libs.kotlin)
    compileOnly(kotlin("stdlib-jdk8"))

    implementation(libs.shadow)

    implementation(libs.javaparser)
    implementation(libs.kotlinparser)

    implementation(libs.snakeyaml)
    implementation(libs.toml4j)

    implementation(libs.forge)

    implementation("su.plo.voice.api:server:${libs.versions.plasmovoice.get()}")
    implementation("su.plo.voice.api:client:${libs.versions.plasmovoice.get()}")
    implementation("su.plo.voice.api:proxy:${libs.versions.plasmovoice.get()}")
}

repositories {
    mavenLocal()
    mavenCentral()
    gradlePluginPortal()
    maven("https://jitpack.io")
    maven("https://maven.minecraftforge.net")
    maven("https://repo.plo.su")
    maven("https://repo.plasmoverse.com/releases")
    maven("https://repo.plasmoverse.com/snapshots")
}

publishing {
    repositories {
        maven("https://repo.plasmoverse.com/snapshots") {
            name = "PlasmoVerseSnapshots"

            credentials {
                username = System.getenv("MAVEN_USERNAME")
                password = System.getenv("MAVEN_PASSWORD")
            }
        }
    }
}
