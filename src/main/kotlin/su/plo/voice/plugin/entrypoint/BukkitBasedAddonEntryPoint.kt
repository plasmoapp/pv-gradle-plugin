package su.plo.voice.plugin.entrypoint

import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

abstract class BukkitBasedAddonEntryPoint : AddonEntryPoint() {
    protected fun generatePlasmoVoiceEnabledCheck(method: MethodVisitor, disableSelf: Boolean) {
        val enabled = Label()

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
            method.visitVarInsn(Opcodes.ALOAD, 0) // Load 'this'
            method.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                BUKKIT_PLUGIN_CLASS,
                "getLogger",
                "()Ljava/util/logging/Logger;",
                false
            )
            method.visitLdcInsn("$PLASMO_VOICE_PLUGIN_NAME is not enabled, disabling")
            method.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/util/logging/Logger",
                "severe",
                "(Ljava/lang/String;)V",
                false
            )

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
            method.visitVarInsn(Opcodes.ALOAD, 0) // Load 'this'
            method.visitMethodInsn(
                Opcodes.INVOKEINTERFACE,
                BUKKIT_PLUGIN_MANAGER_CLASS,
                "disablePlugin",
                "(L$BUKKIT_PLUGIN_INTERFACE_CLASS;)V",
                true
            )
        }

        method.visitInsn(Opcodes.RETURN)
        method.visitLabel(enabled)
    }

    companion object {
        const val BUKKIT_PLUGIN_CLASS = "org/bukkit/plugin/java/JavaPlugin"

        private const val PLASMO_VOICE_PLUGIN_NAME = "PlasmoVoice"
        private const val BUKKIT_SERVER_CLASS = "org/bukkit/Server"
        private const val BUKKIT_PLUGIN_MANAGER_CLASS = "org/bukkit/plugin/PluginManager"
        private const val BUKKIT_PLUGIN_INTERFACE_CLASS = "org/bukkit/plugin/Plugin"
    }
}
