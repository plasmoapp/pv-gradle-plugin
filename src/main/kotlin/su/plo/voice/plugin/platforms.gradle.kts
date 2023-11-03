package su.plo.voice.plugin

plugins {
    id("net.minecraftforge.gradle")
}

minecraft {
    mappings("official", "1.16.5")
}

dependencies {
    val paperVersion = "1.16.5-R0.1-SNAPSHOT"
    compileOnly("com.destroystokyo.paper:paper-api:${paperVersion}")
    val fabricLoaderVersion = "0.14.18"
    compileOnly("net.fabricmc:fabric-loader:${fabricLoaderVersion}")
    val forgeVersion = "1.16.5-36.2.34"
    "minecraft"("net.minecraftforge:forge:${forgeVersion}")
    val velocityVersion = "3.1.1"
    compileOnly("com.velocitypowered:velocity-api:$velocityVersion")
    val bungeeVersion = "1.19-R0.1-SNAPSHOT"
    compileOnly("net.md-5:bungeecord-api:$bungeeVersion")
}

repositories {
    mavenCentral()
    mavenLocal()

    maven("https://maven.fabricmc.net")
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://maven.minecraftforge.net")
    maven("https://oss.sonatype.org/content/repositories/snapshots")
}
