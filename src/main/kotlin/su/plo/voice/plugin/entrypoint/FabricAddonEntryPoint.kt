package su.plo.voice.plugin.entrypoint

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.gradle.api.Project
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import su.plo.voice.plugin.AddonMeta
import su.plo.voice.plugin.extension.visitCodeThenEnd
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
        // Define class
        val className = "FabricEntryPoint"
        val fullClassName = "${packageName.replace(".", "/")}/$className"
        val classOwner = "java/lang/Object"

        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)

        cw.visit(
            Opcodes.V1_8,
            Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL,
            fullClassName,
            null,
            classOwner,
            arrayOf("net/fabricmc/api/ModInitializer")
        )

        // Add constructor
        val constructorMethod = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        constructorMethod.visitCodeThenEnd {
            generateConstructor(cw, constructorMethod, fullClassName, classOwner, addons)
        }

        // Add onLoad method
        val loadMethod = cw.visitMethod(Opcodes.ACC_PUBLIC, "onInitialize", "()V", null, null)
        loadMethod.visitCodeThenEnd {
            generateServerAddonsLoaders(loadMethod, fullClassName, addons)
            generateClientAddonsLoaders(loadMethod, fullClassName, addons)
        }

        // Generate bytecode
        cw.visitEnd()

        val packageDir = File(
            project.buildDir,
            "generated/sources/plasmovoice/java/${packageName.replace(".", "/")}"
        ).also { it.mkdirs() }

        File(
            packageDir,
            "${className}.class"
        ).writeBytes(cw.toByteArray())
    }
}
