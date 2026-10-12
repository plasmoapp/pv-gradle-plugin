package su.plo.voice.plugin.entrypoint

import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.yaml.snakeyaml.Yaml
import su.plo.voice.plugin.AddonMeta
import su.plo.voice.plugin.extension.visitCodeThenEnd
import java.io.File

class BungeeCordAddonEntryPoint(
    private val minPlasmoVoiceVersion: String?,
) : AddonEntryPoint() {

    override fun generate(outputDir: File, addons: List<AddonMeta>) {
        val proxyAddons = addons.filter { it.loaderScope.isProxy }
        if (proxyAddons.isEmpty()) return
        
        val packageName = proxyAddons[0].entryPoint.substringBeforeLast(".")

        generateJavaFile(outputDir, packageName, proxyAddons)
        generateMetaFile(outputDir, packageName, proxyAddons)
    }

    override fun generateMetaFile(outputDir: File, packageName: String, addons: List<AddonMeta>) {
        val addon = addons[0]

        val pluginYml: MutableMap<String, Any> = HashMap()

        pluginYml["name"] = addon.id
        pluginYml["version"] = addon.version
        pluginYml["author"] = addon.authors.joinToString(", ")
        pluginYml["main"] = "${packageName}.BungeeCordEntryPoint"
        pluginYml["depends"] = listOf("PlasmoVoice").plus(addon.dependencies.filter { !it.isOptional }.map { it.id })
        addon.dependencies.filter { it.isOptional }.also { dependencies ->
            if (dependencies.isEmpty()) return@also

            pluginYml["softDepends"] = dependencies.map { it.id }
        }

        val resourcesDir = File(
            outputDir,
            "resources"
        ).also { it.mkdirs() }

        File(
            resourcesDir,
            "bungee.yml"
        ).writeText(Yaml().dump(pluginYml))
    }

    override fun generateJavaFile(outputDir: File, packageName: String, addons: List<AddonMeta>) {
        // Define class
        val className = "BungeeCordEntryPoint"
        val fullClassName = "${packageName.replace(".", "/")}/$className"
        val classOwner = BUNGEE_PLUGIN_CLASS

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

        // Add onLoad method
        val loadMethod = cw.visitMethod(Opcodes.ACC_PUBLIC, "onLoad", "()V", null, null)
        loadMethod.visitCodeThenEnd {
            generatePlasmoVoiceVersionCheck(loadMethod, fullClassName, logUnsupported = true)
            generateProxyAddonsLoaders(loadMethod, fullClassName, addons)
        }

        // Add onDisable method
        val unloadMethod = cw.visitMethod(Opcodes.ACC_PUBLIC, "onDisable", "()V", null, null)
        unloadMethod.visitCodeThenEnd {
            generatePlasmoVoiceVersionCheck(unloadMethod, fullClassName, logUnsupported = false)
            generateProxyAddonsUnloaders(unloadMethod, fullClassName, addons)
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

    private fun generatePlasmoVoiceVersionCheck(method: MethodVisitor, className: String, logUnsupported: Boolean) {
        val minVersion = minPlasmoVoiceVersion ?: return

        method.visitVarInsn(Opcodes.ALOAD, 0) // Load 'this'
        method.visitMethodInsn(
            Opcodes.INVOKEVIRTUAL,
            BUNGEE_PLUGIN_CLASS,
            "getProxy",
            "()L$BUNGEE_PROXY_SERVER_CLASS;",
            false
        )
        method.visitMethodInsn(
            Opcodes.INVOKEVIRTUAL,
            BUNGEE_PROXY_SERVER_CLASS,
            "getPluginManager",
            "()L$BUNGEE_PLUGIN_MANAGER_CLASS;",
            false
        )
        method.visitLdcInsn("PlasmoVoice")
        method.visitMethodInsn(
            Opcodes.INVOKEVIRTUAL,
            BUNGEE_PLUGIN_MANAGER_CLASS,
            "getPlugin",
            "(Ljava/lang/String;)L$BUNGEE_PLUGIN_CLASS;",
            false
        )
        method.visitMethodInsn(
            Opcodes.INVOKEVIRTUAL,
            BUNGEE_PLUGIN_CLASS,
            "getDescription",
            "()L$BUNGEE_PLUGIN_DESCRIPTION_CLASS;",
            false
        )
        method.visitMethodInsn(
            Opcodes.INVOKEVIRTUAL,
            BUNGEE_PLUGIN_DESCRIPTION_CLASS,
            "getVersion",
            "()Ljava/lang/String;",
            false
        )

        generatePlasmoVoiceVersionCheck(method, className, minVersion, messageLocal = 1) { messageLocal ->
            if (!logUnsupported) return@generatePlasmoVoiceVersionCheck

            method.visitVarInsn(Opcodes.ALOAD, 0) // Load 'this'
            method.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                BUNGEE_PLUGIN_CLASS,
                "getLogger",
                "()Ljava/util/logging/Logger;",
                false
            )
            method.visitVarInsn(Opcodes.ALOAD, messageLocal)
            method.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/util/logging/Logger",
                "severe",
                "(Ljava/lang/String;)V",
                false
            )
        }
    }

    companion object {
        private const val BUNGEE_PLUGIN_CLASS = "net/md_5/bungee/api/plugin/Plugin"
        private const val BUNGEE_PROXY_SERVER_CLASS = "net/md_5/bungee/api/ProxyServer"
        private const val BUNGEE_PLUGIN_MANAGER_CLASS = "net/md_5/bungee/api/plugin/PluginManager"
        private const val BUNGEE_PLUGIN_DESCRIPTION_CLASS = "net/md_5/bungee/api/plugin/PluginDescription"
    }
}
