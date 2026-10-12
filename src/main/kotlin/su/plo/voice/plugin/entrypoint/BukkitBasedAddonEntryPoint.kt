package su.plo.voice.plugin.entrypoint

import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

abstract class BukkitBasedAddonEntryPoint(
    protected val minPlasmoVoiceVersion: String?,
) : AddonEntryPoint() {

    protected fun generatePlasmoVoiceEnabledCheck(method: MethodVisitor, disableSelf: Boolean) {
        val enabled = Label()

        generateGetPluginManager(method)
        method.visitLdcInsn(PLASMO_VOICE_PLUGIN_NAME)
        method.visitMethodInsn(
            Opcodes.INVOKEINTERFACE,
            BUKKIT_PLUGIN_MANAGER_CLASS,
            "isPluginEnabled",
            "(Ljava/lang/String;)Z",
            true
        )
        method.visitJumpInsn(Opcodes.IFNE, enabled)

        if (disableSelf) {
            generateDisableSelf(method) {
                method.visitLdcInsn("$PLASMO_VOICE_PLUGIN_NAME is not enabled, disabling")
            }
        }

        method.visitInsn(Opcodes.RETURN)
        method.visitLabel(enabled)
    }

    protected fun generatePlasmoVoiceVersionCheck(method: MethodVisitor, className: String, disableSelf: Boolean) {
        val minVersion = minPlasmoVoiceVersion ?: return

        generateGetPluginManager(method)
        method.visitLdcInsn(PLASMO_VOICE_PLUGIN_NAME)
        method.visitMethodInsn(
            Opcodes.INVOKEINTERFACE,
            BUKKIT_PLUGIN_MANAGER_CLASS,
            "getPlugin",
            "(Ljava/lang/String;)L$BUKKIT_PLUGIN_INTERFACE_CLASS;",
            true
        )
        method.visitMethodInsn(
            Opcodes.INVOKEINTERFACE,
            BUKKIT_PLUGIN_INTERFACE_CLASS,
            "getPluginMeta",
            "()L$PAPER_PLUGIN_META_CLASS;",
            true
        )
        method.visitMethodInsn(
            Opcodes.INVOKEINTERFACE,
            PAPER_PLUGIN_META_CLASS,
            "getVersion",
            "()Ljava/lang/String;",
            true
        )

        generatePlasmoVoiceVersionCheck(method, className, minVersion, messageLocal = 1) { messageLocal ->
            if (disableSelf) {
                generateDisableSelf(method) {
                    method.visitVarInsn(Opcodes.ALOAD, messageLocal)
                }
            }
        }
    }

    private fun generateDisableSelf(method: MethodVisitor, generateMessage: () -> Unit) {
        method.visitVarInsn(Opcodes.ALOAD, 0) // Load 'this'
        method.visitMethodInsn(
            Opcodes.INVOKEVIRTUAL,
            BUKKIT_PLUGIN_CLASS,
            "getLogger",
            "()Ljava/util/logging/Logger;",
            false
        )
        generateMessage()
        method.visitMethodInsn(
            Opcodes.INVOKEVIRTUAL,
            "java/util/logging/Logger",
            "severe",
            "(Ljava/lang/String;)V",
            false
        )

        generateGetPluginManager(method)
        method.visitVarInsn(Opcodes.ALOAD, 0) // Load 'this'
        method.visitMethodInsn(
            Opcodes.INVOKEINTERFACE,
            BUKKIT_PLUGIN_MANAGER_CLASS,
            "disablePlugin",
            "(L$BUKKIT_PLUGIN_INTERFACE_CLASS;)V",
            true
        )
    }

    private fun generateGetPluginManager(method: MethodVisitor) {
        method.visitVarInsn(Opcodes.ALOAD, 0) // Load 'this'
        method.visitMethodInsn(
            Opcodes.INVOKEVIRTUAL,
            BUKKIT_PLUGIN_CLASS,
            "getServer",
            "()L$BUKKIT_SERVER_CLASS;",
            false
        )
        method.visitMethodInsn(
            Opcodes.INVOKEINTERFACE,
            BUKKIT_SERVER_CLASS,
            "getPluginManager",
            "()L$BUKKIT_PLUGIN_MANAGER_CLASS;",
            true
        )
    }

    companion object {
        const val BUKKIT_PLUGIN_CLASS = "org/bukkit/plugin/java/JavaPlugin"

        private const val PLASMO_VOICE_PLUGIN_NAME = "PlasmoVoice"
        private const val BUKKIT_SERVER_CLASS = "org/bukkit/Server"
        private const val BUKKIT_PLUGIN_MANAGER_CLASS = "org/bukkit/plugin/PluginManager"
        private const val BUKKIT_PLUGIN_INTERFACE_CLASS = "org/bukkit/plugin/Plugin"
        private const val PAPER_PLUGIN_META_CLASS = "io/papermc/paper/plugin/configuration/PluginMeta"
    }
}
