package su.plo.voice.plugin.entrypoint

import org.gradle.api.Project
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import su.plo.voice.plugin.AddonMeta
import java.io.File

abstract class AddonEntryPoint {

    protected fun generateServerAddonsLoaders(
        method: MethodVisitor,
        className: String,
        addons: List<AddonMeta>
    ) = generateLoadMethods(method, className, "su/plo/voice/api/server/PlasmoVoiceServer", addons.filter { it.loaderScope.isServer })

    protected fun generateServerAddonsUnloaders(
        method: MethodVisitor,
        className: String,
        addons: List<AddonMeta>
    ) = generateUnloadMethods(method, className, "su/plo/voice/api/server/PlasmoVoiceServer", addons.filter { it.loaderScope.isServer })

    protected fun generateClientAddonsLoaders(
        method: MethodVisitor,
        className: String,
        addons: List<AddonMeta>
    ) = generateLoadMethods(method, className, "su/plo/voice/api/client/PlasmoVoiceClient", addons.filter { it.loaderScope.isClient })

    protected fun generateProxyAddonsLoaders(
        method: MethodVisitor,
        className: String,
        addons: List<AddonMeta>
    ) = generateLoadMethods(method, className, "su/plo/voice/api/proxy/PlasmoVoiceProxy", addons.filter { it.loaderScope.isProxy })

    protected fun generateProxyAddonsUnloaders(
        method: MethodVisitor,
        className: String,
        addons: List<AddonMeta>
    ) = generateUnloadMethods(method, className, "su/plo/voice/api/proxy/PlasmoVoiceProxy", addons.filter { it.loaderScope.isProxy })

    protected fun generateConstructor(
        cv: ClassVisitor,
        method: MethodVisitor,
        className: String,
        classOwner: String,
        addons: List<AddonMeta>
    ) {
        val addonIds = addons.map {
            val addonId = it.id.replace("-[a-z]".toRegex()) { matchResult ->
                matchResult.value.substring(1).capitalize()
            }
            val addonEntryPoint = it.entryPoint.replace(".", "/")

            Pair(addonId, addonEntryPoint)
        }

        // Define field
        addonIds.forEach { (addonId, addonEntryPoint) ->
            val fv = cv.visitField(Opcodes.ACC_PRIVATE + Opcodes.ACC_FINAL, addonId, "L$addonEntryPoint;", null, null)
            fv.visitEnd()
        }

        // Generate default constructor
        method.visitVarInsn(Opcodes.ALOAD, 0) // Load 'this'

        method.visitMethodInsn(Opcodes.INVOKESPECIAL, classOwner, "<init>", "()V", false)

        // Initialize the field
        addonIds.forEach { (addonId, addonEntryPoint) ->
            method.visitVarInsn(Opcodes.ALOAD, 0) // Load 'this'

            method.visitTypeInsn(Opcodes.NEW, addonEntryPoint)
            method.visitInsn(Opcodes.DUP)
            method.visitMethodInsn(Opcodes.INVOKESPECIAL, addonEntryPoint, "<init>", "()V", false)
            method.visitFieldInsn(
                Opcodes.PUTFIELD,
                className,
                addonId,
                "L$addonEntryPoint;"
            )
        }
    }

    private fun generateLoadMethods(
        method: MethodVisitor,
        className: String,
        addonLoaderClass: String,
        addons: List<AddonMeta>
    ) = addons.forEach {
        generateLoaderMethod(method, className, addonLoaderClass, it, "load")
    }

    private fun generateUnloadMethods(
        method: MethodVisitor,
        className: String,
        addonLoaderClass: String,
        addons: List<AddonMeta>
    ) =  addons.forEach {
        generateLoaderMethod(method, className, addonLoaderClass, it, "unload")
    }

    private fun generateLoaderMethod(
        method: MethodVisitor,
        className: String,

        addonLoaderClass: String,
        addon: AddonMeta,
        methodName: String,
    ) {
        val addonId = addon.id.replace("-[a-z]".toRegex()) { matchResult ->
            matchResult.value.substring(1).capitalize()
        }
        val addonEntryPoint = addon.entryPoint.replace(".", "/")

        // Generate method body
        method.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            addonLoaderClass,
            "getAddonsLoader",
            "()Lsu/plo/voice/api/addon/AddonsLoader;",
            true
        )
        method.visitVarInsn(Opcodes.ALOAD, 0) // Load 'this'

        method.visitFieldInsn(
            Opcodes.GETFIELD,
            className,
            addonId,
            "L$addonEntryPoint;"
        )

        method.visitMethodInsn(
            Opcodes.INVOKEINTERFACE,
            "su/plo/voice/api/addon/AddonsLoader",
            methodName,
            "(Ljava/lang/Object;)V",
            true
        )
    }

    abstract fun generate(project: Project, addons: List<AddonMeta>)

    protected abstract fun generateMetaFile(project: Project, packageName: String, addons: List<AddonMeta>)

    protected abstract fun generateJavaFile(project: Project, packageName: String, addons: List<AddonMeta>)

    companion object {

        fun processAddons(project: Project, addons: List<AddonMeta>): List<File> {
            BukkitAddonEntryPoint.generate(project, addons)
            MinestomAddonEntryPoint.generate(project, addons)

            FabricAddonEntryPoint.generate(project, addons)
            ForgeAddonEntryPoint.generate(project, addons)
            NeoForgeAddonEntryPoint.generate(project, addons)

            VelocityAddonEntryPoint.generate(project, addons)
            BungeeCordAddonEntryPoint.generate(project, addons)

            return emptyList()
        }
    }
}
