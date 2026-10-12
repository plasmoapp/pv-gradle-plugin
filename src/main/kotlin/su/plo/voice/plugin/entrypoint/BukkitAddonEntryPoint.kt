package su.plo.voice.plugin.entrypoint

import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.yaml.snakeyaml.Yaml
import su.plo.voice.plugin.AddonMeta
import su.plo.voice.plugin.extension.visitCodeThenEnd
import java.io.File

class BukkitAddonEntryPoint(
    minPlasmoVoiceVersion: String?,
) : BukkitBasedAddonEntryPoint(minPlasmoVoiceVersion) {

    override fun generate(outputDir: File, addons: List<AddonMeta>) {
        val serverAddons = addons.filter { it.loaderScope.isServer }
        if (serverAddons.isEmpty()) return

        val packageName = serverAddons[0].entryPoint.substringBeforeLast(".")

        generateJavaFile(outputDir, packageName, serverAddons)
        generateMetaFile(outputDir, packageName, serverAddons)
    }

    override fun generateMetaFile(outputDir: File, packageName: String, addons: List<AddonMeta>) {
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

            pluginYml["softdepend"] = dependencies.map { it.id }
        }

        val resourcesDir = File(
            outputDir,
            "resources"
        ).also { it.mkdirs() }

        File(
            resourcesDir,
            "plugin.yml"
        ).writeText(Yaml().dump(pluginYml))
    }

    override fun generateJavaFile(outputDir: File, packageName: String, addons: List<AddonMeta>) {
        // Define class
        val className = "BukkitEntryPoint"
        val fullClassName = "${packageName.replace(".", "/")}/$className"
        val classOwner = "org/bukkit/plugin/java/JavaPlugin"

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

        // Add onEnable method
        val enableMethod = cw.visitMethod(Opcodes.ACC_PUBLIC, "onEnable", "()V", null, null)
        enableMethod.visitCodeThenEnd {
            generatePlasmoVoiceEnabledCheck(enableMethod, disableSelf = true)
            generatePlasmoVoiceVersionCheck(enableMethod, fullClassName, disableSelf = true)
            generateServerAddonsLoaders(enableMethod, fullClassName, addons)
        }

        // Add onDisable method
        val unloadMethod = cw.visitMethod(Opcodes.ACC_PUBLIC, "onDisable", "()V", null, null)
        unloadMethod.visitCodeThenEnd {
            generatePlasmoVoiceEnabledCheck(unloadMethod, disableSelf = false)
            generatePlasmoVoiceVersionCheck(unloadMethod, fullClassName, disableSelf = false)
            generateServerAddonsUnloaders(unloadMethod, fullClassName, addons)
        }

        // Generate bytecode
        cw.visitEnd()

        val packageDir = File(
            outputDir,
            "classes/${packageName.replace(".", "/")}"
        ).also { it.mkdirs() }

        File(
            packageDir,
            "${className}.class"
        ).writeBytes(cw.toByteArray())

        if (minPlasmoVoiceVersion != null) writeVersionCheckClass(outputDir, packageName.replace(".", "/"))
    }
}
