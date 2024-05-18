package su.plo.voice.plugin.entrypoint

import com.google.common.base.CaseFormat
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.gradle.api.Project
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import su.plo.voice.plugin.AddonMeta
import su.plo.voice.plugin.extension.visitCodeThenEnd
import java.io.File

object MinestomAddonEntryPoint : AddonEntryPoint() {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    override fun generate(project: Project, addons: List<AddonMeta>) {
        val serverAddons = addons.filter { it.loaderScope.isServer }
        if (serverAddons.isEmpty()) return

        val packageName = serverAddons[0].entryPoint.substringBeforeLast(".")

        generateJavaFile(project, packageName, serverAddons)
        generateMetaFile(project, packageName, serverAddons)
    }

    override fun generateMetaFile(project: Project, packageName: String, addons: List<AddonMeta>) {
        val addon = addons[0]

        val fabricJson = JsonObject()

        fabricJson.addProperty("name", transformAddonId(addon.id))
        fabricJson.addProperty("entrypoint", "$packageName.MinestomEntryPoint")
        fabricJson.addProperty("version", addon.version)
        fabricJson.add("dependencies", JsonArray().also { depends ->
            depends.add("PlasmoVoice")
            addon.dependencies.filter { !it.isOptional }.forEach { dependency ->
                depends.add(transformAddonId(dependency.id))
            }
        })

        val resourcesDir = File(
            project.buildDir,
            "generated/sources/plasmovoice/resources"
        ).also { it.mkdirs() }

        File(
            resourcesDir,
            "extension.json"
        ).writeText(gson.toJson(fabricJson))
    }

    override fun generateJavaFile(project: Project, packageName: String, addons: List<AddonMeta>) {
        // Define class
        val className = "MinestomEntryPoint"
        val fullClassName = "${packageName.replace(".", "/")}/$className"
        val classOwner = "net/minestom/server/extensions/Extension"

        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)

        cw.visit(
            Opcodes.V1_8,
            Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL,
            fullClassName,
            null,
            classOwner,
            null
        )

        // Add constructor
        val constructorMethod = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        constructorMethod.visitCodeThenEnd {
            generateConstructor(cw, constructorMethod, fullClassName, classOwner, addons)
        }

        // Add initialize method
        val loadMethod = cw.visitMethod(Opcodes.ACC_PUBLIC, "initialize", "()V", null, null)
        loadMethod.visitCodeThenEnd {
            generateServerAddonsLoaders(loadMethod, fullClassName, addons)
        }

        // Add terminate method
        val unloadMethod = cw.visitMethod(Opcodes.ACC_PUBLIC, "terminate", "()V", null, null)
        unloadMethod.visitCodeThenEnd {
            generateServerAddonsUnloaders(unloadMethod, fullClassName, addons)
        }

        // Generate bytecode
        cw.visitEnd()

        val packageDir = File(
            project.buildDir,
            "generated/sources/plasmovoice/classes/${packageName.replace(".", "/")}"
        ).also { it.mkdirs() }

        File(
            packageDir,
            "${className}.class"
        ).writeBytes(cw.toByteArray())
    }

    private fun transformAddonId(addonId: String): String =
        CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_CAMEL, addonId)
}
