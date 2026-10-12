package su.plo.voice.plugin.entrypoint

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.SimpleRemapper
import su.plo.voice.plugin.AddonMeta
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

    protected fun generatePlasmoVoiceVersionCheck(
        method: MethodVisitor,
        className: String,
        minVersion: String,
        messageLocal: Int,
        generateUnsupported: (messageLocal: Int) -> Unit
    ) {
        val supported = Label()

        method.visitLdcInsn(minVersion)
        method.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            versionCheckClass(className.substringBeforeLast("/")),
            "unsupportedMessage",
            "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
            false
        )
        method.visitVarInsn(Opcodes.ASTORE, messageLocal)

        method.visitVarInsn(Opcodes.ALOAD, messageLocal)
        method.visitJumpInsn(Opcodes.IFNULL, supported)
        generateUnsupported(messageLocal)
        method.visitInsn(Opcodes.RETURN)

        method.visitLabel(supported)
    }

    protected fun writeVersionCheckClass(outputDir: File, packagePath: String) {
        val template = AddonEntryPoint::class.java.getResourceAsStream("/$VERSION_CHECK_TEMPLATE.class")
            ?.use { ClassReader(it) }
            ?: error("$VERSION_CHECK_TEMPLATE.class is missing from the plugin jar")

        val writer = ClassWriter(0)
        template.accept(ClassRemapper(writer, SimpleRemapper(Opcodes.ASM9, VERSION_CHECK_TEMPLATE, versionCheckClass(packagePath))), 0)

        File(outputDir, "classes/${versionCheckClass(packagePath)}.class")
            .also { it.parentFile.mkdirs() }
            .writeBytes(writer.toByteArray())
    }

    private fun versionCheckClass(packagePath: String) = "$packagePath/PlasmoVoiceVersionCheck"

    abstract fun generate(outputDir: File, addons: List<AddonMeta>)

    protected abstract fun generateMetaFile(outputDir: File, packageName: String, addons: List<AddonMeta>)

    protected abstract fun generateJavaFile(outputDir: File, packageName: String, addons: List<AddonMeta>)

    companion object {

        private const val VERSION_CHECK_TEMPLATE = "su/plo/voice/plugin/template/PlasmoVoiceVersionCheck"
        private const val DEFAULT_MIN_PLASMO_VOICE_VERSION = "2.1.0"
        private val VERSION_REGEX = "\\d+(\\.\\d+)*".toRegex()

        fun processAddons(
            outputDir: File,
            addons: List<AddonMeta>,
            paperPlugin: Boolean,
            paperOpenClassloader: Boolean,
            minPlasmoVoiceVersion: String?,
        ): List<File> {
            require(minPlasmoVoiceVersion == null || minPlasmoVoiceVersion.matches(VERSION_REGEX)) {
                "minPlasmoVoiceVersion should be dot-separated numbers (e.g. '2.1.5'), got '$minPlasmoVoiceVersion'"
            }

            if (paperPlugin) {
                PaperAddonEntryPoint(paperOpenClassloader, minPlasmoVoiceVersion).generate(outputDir, addons)
            } else {
                BukkitAddonEntryPoint(minPlasmoVoiceVersion).generate(outputDir, addons)
            }
            MinestomAddonEntryPoint.generate(outputDir, addons)

            FabricAddonEntryPoint(minPlasmoVoiceVersion ?: DEFAULT_MIN_PLASMO_VOICE_VERSION).generate(outputDir, addons)
            NeoForgeAddonEntryPoint(minPlasmoVoiceVersion ?: DEFAULT_MIN_PLASMO_VOICE_VERSION).generate(outputDir, addons)

            VelocityAddonEntryPoint(minPlasmoVoiceVersion).generate(outputDir, addons)
            BungeeCordAddonEntryPoint(minPlasmoVoiceVersion).generate(outputDir, addons)

            return emptyList()
        }
    }
}
