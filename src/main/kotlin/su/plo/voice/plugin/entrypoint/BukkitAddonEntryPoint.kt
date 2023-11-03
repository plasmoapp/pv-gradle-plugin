package su.plo.voice.plugin.entrypoint

import com.github.javaparser.ast.CompilationUnit
import org.gradle.api.Project
import org.yaml.snakeyaml.Yaml
import su.plo.voice.plugin.AddonMeta
import java.io.File

object BukkitAddonEntryPoint : AddonEntryPoint() {

    override fun generate(project: Project, addons: List<AddonMeta>) {
        val serverAddons = addons.filter { it.loaderScope.isServer }
        if (serverAddons.isEmpty()) return

        val packageName = serverAddons[0].entryPoint.substringBeforeLast(".")

        generateJavaFile(project, packageName, serverAddons)
        generateMetaFile(project, packageName, serverAddons)
    }

    override fun generateMetaFile(project: Project, packageName: String, addons: List<AddonMeta>) {
        val addon = addons[0]

        val pluginYml: MutableMap<String, Any> = HashMap()

        pluginYml["name"] = addon.id
        pluginYml["version"] = addon.version
        pluginYml["main"] = "${packageName}.BukkitEntryPoint"
        pluginYml["load"] = "STARTUP"
        pluginYml["api-version"] = "1.16"
        pluginYml["authors"] = addon.authors
        pluginYml["folia-supported"] = true
        pluginYml["depend"] = listOf("PlasmoVoice").plus(addon.dependencies.filter { !it.isOptional }.map { it.id })
        addon.dependencies.filter { it.isOptional }.also { dependencies ->
            if (dependencies.isEmpty()) return@also

            pluginYml["softdepend"] = dependencies
        }

        val resourcesDir = File(
            project.buildDir,
            "generated/sources/plasmovoice/resources"
        ).also { it.mkdirs() }

        File(
            resourcesDir,
            "plugin.yml"
        ).writeText(Yaml().dump(pluginYml))
    }

    override fun generateJavaFile(project: Project, packageName: String, addons: List<AddonMeta>) {
        val compilationUnit = CompilationUnit()
            .addImport("org.bukkit.plugin.java.JavaPlugin")
            .addImport("su.plo.voice.api.server.PlasmoVoiceServer")
            .setPackageDeclaration(packageName)

        val entryPointClass = compilationUnit
            .addClass("BukkitEntryPoint")
            .setPublic(true)
            .addExtendedType("JavaPlugin")

        val loadMethodBlock = generateServerAddonsLoaders(entryPointClass, addons)
        val unloadMethodBlock = generateServerAddonsUnloaders(entryPointClass, addons)

        entryPointClass.addMethod("onLoad")
            .setPublic(true)
            .addAnnotation(Override::class.java)
            .setBody(loadMethodBlock)

        entryPointClass.addMethod("onDisable")
            .setPublic(true)
            .addAnnotation(Override::class.java)
            .setBody(unloadMethodBlock)

        val packageDir = File(
            project.buildDir,
            "generated/sources/plasmovoice/java/${packageName.replace(".", "/")}"
        ).also { it.mkdirs() }

        File(
            packageDir,
            "${entryPointClass.name}.java"
        ).writeText(compilationUnit.toString())
    }
}
