package su.plo.voice.plugin.entrypoint

import com.github.javaparser.ast.CompilationUnit
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.gradle.api.Project
import su.plo.voice.plugin.AddonMeta
import java.io.File

object VelocityAddonEntryPoint : AddonEntryPoint() {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    override fun generate(project: Project, addons: List<AddonMeta>) {
        val proxyAddons = addons.filter { it.loaderScope.isProxy }
        if (proxyAddons.isEmpty()) return
        
        val packageName = proxyAddons[0].entryPoint.substringBeforeLast(".")

        generateJavaFile(project, packageName, proxyAddons)
        generateMetaFile(project, packageName, proxyAddons)
    }

    override fun generateMetaFile(project: Project, packageName: String, addons: List<AddonMeta>) {
        val addon = addons[0]

        val velocityJson = JsonObject()

        velocityJson.addProperty("id", addon.id)
        velocityJson.addProperty("name", addon.id)
        velocityJson.addProperty("version", addon.version)
        velocityJson.add("authors", JsonArray().also { authors ->
            addon.authors.forEach { authors.add(it) }
        })
        velocityJson.add("dependencies", JsonArray().also { dependencies ->
            dependencies.add(JsonObject().also {
                it.addProperty("id", "plasmovoice")
                it.addProperty("optional", false)
            })

            addon.dependencies.forEach { dependency ->
                dependencies.add(JsonObject().also {
                    it.addProperty("id", dependency.id)
                    it.addProperty("optional", dependency.isOptional)
                })
            }
        })
        velocityJson.addProperty("main", "${packageName}.VelocityEntryPoint")

        val resourcesDir = File(
            project.buildDir,
            "generated/sources/plasmovoice/resources"
        ).also { it.mkdirs() }

        File(
            resourcesDir,
            "velocity-plugin.json"
        ).writeText(gson.toJson(velocityJson))
    }

    override fun generateJavaFile(project: Project, packageName: String, addons: List<AddonMeta>) {
        val compilationUnit = CompilationUnit()
            .addImport("com.velocitypowered.api.event.Subscribe")
            .addImport("com.velocitypowered.api.event.proxy.ProxyInitializeEvent")
            .addImport("su.plo.voice.api.proxy.PlasmoVoiceProxy")
            .setPackageDeclaration(packageName)

        val entryPointClass = compilationUnit
            .addClass("VelocityEntryPoint")
            .setPublic(true)

        val methodBlock = generateProxyAddonsLoaders(entryPointClass, addons)

        entryPointClass.addMethod("onProxyInitialization")
            .setPublic(true)
            .addAnnotation("Subscribe")
            .addParameter("ProxyInitializeEvent", "event")
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
