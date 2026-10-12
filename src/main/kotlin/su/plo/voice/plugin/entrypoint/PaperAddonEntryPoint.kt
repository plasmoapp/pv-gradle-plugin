package su.plo.voice.plugin.entrypoint

import org.gradle.api.Project
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import su.plo.voice.plugin.AddonMeta
import su.plo.voice.plugin.extension.visitCodeThenEnd
import java.io.File

class PaperAddonEntryPoint(
    private val openClassloader: Boolean,
    minPlasmoVoiceVersion: String?
) : BukkitBasedAddonEntryPoint(minPlasmoVoiceVersion) {

    override fun generate(project: Project, addons: List<AddonMeta>) {
        val serverAddons = addons.filter { it.loaderScope.isServer }
        if (serverAddons.isEmpty()) return

        val packageName = serverAddons[0].entryPoint.substringBeforeLast(".")

        generateJavaFile(project, packageName, serverAddons)
        generateMetaFile(project, packageName, serverAddons)
    }

    override fun generateMetaFile(project: Project, packageName: String, addons: List<AddonMeta>) {
        val addon = addons[0]

        val pluginYml: MutableMap<String, Any> = LinkedHashMap()

        pluginYml["name"] = addon.id
        pluginYml["version"] = addon.version
        pluginYml["main"] = "${packageName}.$PLUGIN_CLASS_NAME"
        pluginYml["bootstrapper"] = "${packageName}.$BOOTSTRAP_CLASS_NAME"
        pluginYml["load"] = "STARTUP"
        pluginYml["api-version"] = "1.21"
        pluginYml["authors"] = addon.authors
        pluginYml["folia-supported"] = true
        if (openClassloader) pluginYml["has-open-classloader"] = true

        pluginYml["dependencies"] = linkedMapOf(
            "bootstrap" to dependencies(addon),
            "server" to dependencies(addon)
        )

        val resourcesDir = File(
            project.buildDir,
            "generated/sources/plasmovoice/resources"
        ).also { it.mkdirs() }

        val dumperOptions = DumperOptions().also {
            it.defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
        }

        File(
            resourcesDir,
            "paper-plugin.yml"
        ).writeText(Yaml(dumperOptions).dump(pluginYml))
    }

    override fun generateJavaFile(project: Project, packageName: String, addons: List<AddonMeta>) {
        val packagePath = packageName.replace(".", "/")

        val packageDir = File(
            project.buildDir,
            "generated/sources/plasmovoice/classes/$packagePath"
        ).also { it.mkdirs() }

        File(packageDir, "${BOOTSTRAP_CLASS_NAME}.class")
            .writeBytes(generateBootstrapClass(packagePath, addons))
        File(packageDir, "${PLUGIN_CLASS_NAME}.class")
            .writeBytes(generatePluginClass(packagePath, addons))

        if (minPlasmoVoiceVersion != null) writeVersionCheckClass(project, packagePath)
    }

    private fun generateBootstrapClass(packagePath: String, addons: List<AddonMeta>): ByteArray {
        // Define class
        val fullClassName = "$packagePath/$BOOTSTRAP_CLASS_NAME"
        val pluginClassName = "$packagePath/$PLUGIN_CLASS_NAME"
        val classOwner = "java/lang/Object"

        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)

        cw.visit(
            Opcodes.V1_8,
            Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL,
            fullClassName,
            null,
            classOwner,
            arrayOf(BOOTSTRAP_CLASS)
        )

        // Add constructor, addons are instantiated here, at the bootstrap phase
        val constructorMethod = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        constructorMethod.visitCodeThenEnd {
            generateConstructor(cw, constructorMethod, fullClassName, classOwner, addons)
        }

        // Add bootstrap method
        val bootstrapMethod = cw.visitMethod(
            Opcodes.ACC_PUBLIC,
            "bootstrap",
            "(L$BOOTSTRAP_CONTEXT_CLASS;)V",
            null,
            null
        )
        bootstrapMethod.visitCodeThenEnd {}

        // Add createPlugin method
        val createPluginMethod = cw.visitMethod(
            Opcodes.ACC_PUBLIC,
            "createPlugin",
            "(L$PROVIDER_CONTEXT_CLASS;)L$BUKKIT_PLUGIN_CLASS;",
            null,
            null
        )
        createPluginMethod.visitCode()
        createPluginMethod.visitTypeInsn(Opcodes.NEW, pluginClassName)
        createPluginMethod.visitInsn(Opcodes.DUP)
        addons.forEach { addon ->
            createPluginMethod.visitVarInsn(Opcodes.ALOAD, 0) // Load 'this'
            createPluginMethod.visitFieldInsn(
                Opcodes.GETFIELD,
                fullClassName,
                addon.fieldName,
                addon.entryPointDescriptor
            )
        }
        createPluginMethod.visitMethodInsn(
            Opcodes.INVOKESPECIAL,
            pluginClassName,
            "<init>",
            addonsConstructorDescriptor(addons),
            false
        )
        createPluginMethod.visitInsn(Opcodes.ARETURN)
        createPluginMethod.visitMaxs(0, 0)
        createPluginMethod.visitEnd()

        // Generate bytecode
        cw.visitEnd()

        return cw.toByteArray()
    }

    private fun generatePluginClass(packagePath: String, addons: List<AddonMeta>): ByteArray {
        // Define class
        val fullClassName = "$packagePath/$PLUGIN_CLASS_NAME"

        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)

        cw.visit(
            Opcodes.V1_8,
            Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL,
            fullClassName,
            null,
            BUKKIT_PLUGIN_CLASS,
            null
        )

        // Add constructor accepting addons instantiated at the bootstrap phase
        val constructorMethod = cw.visitMethod(
            Opcodes.ACC_PUBLIC,
            "<init>",
            addonsConstructorDescriptor(addons),
            null,
            null
        )
        constructorMethod.visitCodeThenEnd {
            generateAddonsConstructor(cw, constructorMethod, fullClassName, BUKKIT_PLUGIN_CLASS, addons)
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

        return cw.toByteArray()
    }

    private fun dependencies(addon: AddonMeta): Map<String, Any> {
        val dependencies = LinkedHashMap<String, Any>()

        dependencies["PlasmoVoice"] = dependency(required = true)
        addon.dependencies.forEach { dependency ->
            dependencies[dependency.id] = dependency(required = !dependency.isOptional)
        }

        return dependencies
    }

    private fun dependency(required: Boolean): Map<String, Any> = linkedMapOf(
        "load" to "BEFORE",
        "required" to required
    )

    companion object {

        private const val BOOTSTRAP_CLASS_NAME = "PaperBootstrap"
        private const val PLUGIN_CLASS_NAME = "PaperEntryPoint"

        private const val BOOTSTRAP_CLASS = "io/papermc/paper/plugin/bootstrap/PluginBootstrap"
        private const val BOOTSTRAP_CONTEXT_CLASS = "io/papermc/paper/plugin/bootstrap/BootstrapContext"
        private const val PROVIDER_CONTEXT_CLASS = "io/papermc/paper/plugin/bootstrap/PluginProviderContext"
    }
}
