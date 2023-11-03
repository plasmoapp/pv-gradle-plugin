package su.plo.voice.plugin.entrypoint

import com.github.javaparser.ast.CompilationUnit
import org.gradle.api.Project
import org.yaml.snakeyaml.Yaml
import su.plo.voice.plugin.AddonMeta
import java.io.File

object BungeeCordAddonEntryPoint : AddonEntryPoint() {

    override fun generate(project: Project, addons: List<AddonMeta>) {
        val proxyAddons = addons.filter { it.loaderScope.isProxy }
        if (proxyAddons.isEmpty()) return
        
        val packageName = proxyAddons[0].entryPoint.substringBeforeLast(".")

        generateJavaFile(project, packageName, proxyAddons)
        generateMetaFile(project, packageName, proxyAddons)
    }

    override fun generateMetaFile(project: Project, packageName: String, addons: List<AddonMeta>) {
        val addon = addons[0]

        val pluginYml: MutableMap<String, Any> = HashMap()

        pluginYml["name"] = addon.id
        pluginYml["version"] = addon.version
        pluginYml["author"] = addon.authors.joinToString(", ")
        pluginYml["main"] = "${packageName}.BungeeCordEntryPoint"
        pluginYml["depends"] = listOf("PlasmoVoice").plus(addon.dependencies.filter { !it.isOptional }.map { it.id })
        addon.dependencies.filter { it.isOptional }.also { dependencies ->
            if (dependencies.isEmpty()) return@also

            pluginYml["softDepends"] = dependencies
        }

        val resourcesDir = File(
            project.buildDir,
            "generated/sources/plasmovoice/resources"
        ).also { it.mkdirs() }

        File(
            resourcesDir,
            "bungee.yml"
        ).writeText(Yaml().dump(pluginYml))
    }

    override fun generateJavaFile(project: Project, packageName: String, addons: List<AddonMeta>) {
        val compilationUnit = CompilationUnit()
            .addImport("net.md_5.bungee.api.plugin.Plugin")
            .addImport("su.plo.voice.api.proxy.PlasmoVoiceProxy")
            .setPackageDeclaration(packageName)

        val entryPointClass = compilationUnit
            .addClass("BungeeCordEntryPoint")
            .setPublic(true)
            .addExtendedType("Plugin")

        val methodBlock = generateProxyAddonsLoaders(entryPointClass, addons)

        entryPointClass.addMethod("onLoad")
            .setPublic(true)
            .addAnnotation(Override::class.java)
            .setBody(methodBlock)

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
