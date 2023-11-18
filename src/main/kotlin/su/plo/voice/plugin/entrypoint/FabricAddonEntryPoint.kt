package su.plo.voice.plugin.entrypoint

import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.stmt.BlockStmt
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.gradle.api.Project
import su.plo.voice.plugin.AddonMeta
import java.io.File

object FabricAddonEntryPoint : AddonEntryPoint() {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    override fun generate(project: Project, addons: List<AddonMeta>) {
        val serverClientAddons = addons.filter { it.loaderScope.isClient || it.loaderScope.isServer }
        if (serverClientAddons.isEmpty()) return

        val packageName = serverClientAddons[0].entryPoint.substringBeforeLast(".")

        generateJavaFile(project, packageName, serverClientAddons)
        generateMetaFile(project, packageName, serverClientAddons)
    }

    override fun generateMetaFile(project: Project, packageName: String, addons: List<AddonMeta>) {
        val addon = addons[0]

        val fabricJson = JsonObject()
        fabricJson.addProperty("schemaVersion", 1)
        fabricJson.addProperty("id", addon.id)
        fabricJson.addProperty("name", addon.id)
        fabricJson.addProperty("version", addon.version)
        fabricJson.addProperty("license", addon.license)
        fabricJson.add("authors", JsonArray().also { authors ->
            addon.authors.forEach { authors.add(it) }
        })
        fabricJson.addProperty("environment", "*")
        fabricJson.add("entrypoints", JsonObject().also { entryPoints ->
            entryPoints.add("main", JsonArray().also { main ->
                main.add("${packageName}.FabricEntryPoint")
            })
        })
        fabricJson.add("depends", JsonObject().also { depends ->
            depends.addProperty("plasmovoice", ">=2.1.0-0")
            addon.dependencies.filter { !it.isOptional }.forEach { dependency ->
                depends.addProperty(dependency.id, "*")
            }
        })
        addon.dependencies.filter { it.isOptional }.also { dependencies ->
            if (dependencies.isEmpty()) return@also

            fabricJson.add("suggests", JsonObject().also { suggests ->
                dependencies.forEach { dependency ->
                    suggests.addProperty(dependency.id, "*")
                }
            })
        }

        val resourcesDir = File(
            project.buildDir,
            "generated/sources/plasmovoice/resources"
        ).also { it.mkdirs() }

        File(
            resourcesDir,
            "fabric.mod.json"
        ).writeText(gson.toJson(fabricJson))
    }

    override fun generateJavaFile(project: Project, packageName: String, addons: List<AddonMeta>) {
        val compilationUnit = CompilationUnit()
            .addImport("net.fabricmc.api.ModInitializer")
            .setPackageDeclaration(packageName)

        if (addons.any { it.loaderScope.isServer }) {
            compilationUnit.addImport("su.plo.voice.api.server.PlasmoVoiceServer")
        }

        if (addons.any { it.loaderScope.isClient }) {
            compilationUnit.addImport("su.plo.voice.api.client.PlasmoVoiceClient")
        }

        val entryPointClass = compilationUnit
            .addClass("FabricEntryPoint")
            .setPublic(true)
            .addImplementedType("ModInitializer")

        val methodBlock = BlockStmt().also { block ->
            generateServerAddonsLoaders(entryPointClass, addons, block)
            generateClientAddonsLoaders(entryPointClass, addons, block)
        }

        entryPointClass.addMethod("onInitialize")
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
