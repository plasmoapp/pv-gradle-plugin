package su.plo.voice.plugin.entrypoint

import org.gradle.api.Project
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import su.plo.voice.plugin.AddonMeta
import su.plo.voice.plugin.EntryPointsExtension
import java.io.File

abstract class AddonEntryPoint {

    protected val AddonMeta.fieldName: String
        get() = id.replace("-[a-z]".toRegex()) { matchResult ->
            matchResult.value[1].uppercase()
        }

    protected val AddonMeta.entryPointClass: String
        get() = entryPoint.replace(".", "/")

    protected val AddonMeta.entryPointDescriptor: String
        get() = "L$entryPointClass;"

    protected fun addonsConstructorDescriptor(addons: List<AddonMeta>): String =
        addons.joinToString("", prefix = "(", postfix = ")V") { it.entryPointDescriptor }

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
        generateFields(cv, addons)

        // Generate default constructor
        method.visitVarInsn(Opcodes.ALOAD, 0) // Load 'this'

        method.visitMethodInsn(Opcodes.INVOKESPECIAL, classOwner, "<init>", "()V", false)

        // Initialize the field
        addons.forEach { addon ->
            method.visitVarInsn(Opcodes.ALOAD, 0) // Load 'this'

            method.visitTypeInsn(Opcodes.NEW, addon.entryPointClass)
            method.visitInsn(Opcodes.DUP)
            method.visitMethodInsn(Opcodes.INVOKESPECIAL, addon.entryPointClass, "<init>", "()V", false)
            method.visitFieldInsn(
                Opcodes.PUTFIELD,
                className,
                addon.fieldName,
                addon.entryPointDescriptor
            )
        }
    }

    protected fun generateAddonsConstructor(
        cv: ClassVisitor,
        method: MethodVisitor,
        className: String,
        classOwner: String,
        addons: List<AddonMeta>
    ) {
        generateFields(cv, addons)

        // Generate constructor accepting already instantiated addons
        method.visitVarInsn(Opcodes.ALOAD, 0) // Load 'this'

        method.visitMethodInsn(Opcodes.INVOKESPECIAL, classOwner, "<init>", "()V", false)

        // Initialize the field
        addons.forEachIndexed { index, addon ->
            method.visitVarInsn(Opcodes.ALOAD, 0) // Load 'this'
            method.visitVarInsn(Opcodes.ALOAD, index + 1) // Load addon argument

            method.visitFieldInsn(
                Opcodes.PUTFIELD,
                className,
                addon.fieldName,
                addon.entryPointDescriptor
            )
        }
    }

    private fun generateFields(cv: ClassVisitor, addons: List<AddonMeta>) = addons.forEach { addon ->
        val fv = cv.visitField(
            Opcodes.ACC_PRIVATE + Opcodes.ACC_FINAL,
            addon.fieldName,
            addon.entryPointDescriptor,
            null,
            null
        )
        fv.visitEnd()
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
            addon.fieldName,
            addon.entryPointDescriptor
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

        fun processAddons(project: Project, addons: List<AddonMeta>, extension: EntryPointsExtension): List<File> {
            if (extension.paperPlugin) {
                PaperAddonEntryPoint(extension.paperOpenClassloader).generate(project, addons)
            } else {
                BukkitAddonEntryPoint.generate(project, addons)
            }
            MinestomAddonEntryPoint.generate(project, addons)

            FabricAddonEntryPoint.generate(project, addons)
            NeoForgeAddonEntryPoint.generate(project, addons)

            VelocityAddonEntryPoint.generate(project, addons)
            BungeeCordAddonEntryPoint.generate(project, addons)

            return emptyList()
        }
    }
}
