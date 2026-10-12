package su.plo.voice.plugin.entrypoint

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.gradle.api.Project
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import su.plo.voice.plugin.AddonMeta
import su.plo.voice.plugin.extension.visitCodeThenEnd
import java.io.File

class VelocityAddonEntryPoint(
    private val minPlasmoVoiceVersion: String?,
) : AddonEntryPoint() {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    override fun generate(project: Project, addons: List<AddonMeta>) {
        val proxyAddons = addons.filter { it.loaderScope.isProxy }
        if (proxyAddons.isEmpty()) return
        
        val packageName = proxyAddons[0].entryPoint.substringBeforeLast(".")

        generateJavaFile(project, packageName, proxyAddons)
        generateMetaFile(project, packageName, proxyAddons)
    }

    override fun generateMetaFile(project: Project, packageName: String, addons: List<AddonMeta>) {
        val addon = addons[0]

        val velocityJson = JsonObject()

        velocityJson.addProperty("id", addon.id)
        velocityJson.addProperty("name", addon.id)
        velocityJson.addProperty("version", addon.version)
        velocityJson.add("authors", JsonArray().also { authors ->
            addon.authors.forEach { authors.add(it) }
        })
        velocityJson.add("dependencies", JsonArray().also { dependencies ->
            dependencies.add(JsonObject().also {
                it.addProperty("id", "plasmovoice")
                it.addProperty("optional", false)
            })

            addon.dependencies.forEach { dependency ->
                dependencies.add(JsonObject().also {
                    it.addProperty("id", dependency.id)
                    it.addProperty("optional", dependency.isOptional)
                })
            }
        })
        velocityJson.addProperty("main", "${packageName}.VelocityEntryPoint")

        val resourcesDir = File(
            project.layout.buildDirectory.get().asFile,
            "generated/sources/plasmovoice/resources"
        ).also { it.mkdirs() }

        File(
            resourcesDir,
            "velocity-plugin.json"
        ).writeText(gson.toJson(velocityJson))
    }

    override fun generateJavaFile(project: Project, packageName: String, addons: List<AddonMeta>) {
        // Define class
        val className = "VelocityEntryPoint"
        val fullClassName = "${packageName.replace(".", "/")}/$className"
        val classOwner = "java/lang/Object"

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
        val loadMethod = cw.visitMethod(Opcodes.ACC_PUBLIC, "onProxyInitialization", "(Lcom/velocitypowered/api/event/proxy/ProxyInitializeEvent;)V", null, null)
        loadMethod.visitAnnotation("Lcom/velocitypowered/api/event/Subscribe;", true)
        loadMethod.visitCodeThenEnd {
            generatePlasmoVoiceVersionCheck(cw, loadMethod, fullClassName)
            generateProxyAddonsLoaders(loadMethod, fullClassName, addons)
        }

        // Generate bytecode
        cw.visitEnd()

        val packageDir = File(
            project.layout.buildDirectory.get().asFile,
            "generated/sources/plasmovoice/classes/${packageName.replace(".", "/")}"
        ).also { it.mkdirs() }

        File(
            packageDir,
            "${className}.class"
        ).writeBytes(cw.toByteArray())

        if (minPlasmoVoiceVersion != null) writeVersionCheckClass(project, packageName.replace(".", "/"))
    }

    private fun generatePlasmoVoiceVersionCheck(cv: ClassVisitor, method: MethodVisitor, className: String) {
        val minVersion = minPlasmoVoiceVersion ?: return

        generateInjectedField(cv, PROXY_SERVER_FIELD, "L$VELOCITY_PROXY_SERVER_CLASS;")
        generateInjectedField(cv, LOGGER_FIELD, "L$SLF4J_LOGGER_CLASS;")

        method.visitVarInsn(Opcodes.ALOAD, 0) // Load 'this'
        method.visitFieldInsn(Opcodes.GETFIELD, className, PROXY_SERVER_FIELD, "L$VELOCITY_PROXY_SERVER_CLASS;")
        method.visitMethodInsn(
            Opcodes.INVOKEINTERFACE,
            VELOCITY_PROXY_SERVER_CLASS,
            "getPluginManager",
            "()L$VELOCITY_PLUGIN_MANAGER_CLASS;",
            true
        )
        method.visitLdcInsn("plasmovoice")
        method.visitMethodInsn(
            Opcodes.INVOKEINTERFACE,
            VELOCITY_PLUGIN_MANAGER_CLASS,
            "getPlugin",
            "(Ljava/lang/String;)Ljava/util/Optional;",
            true
        )
        method.visitMethodInsn(
            Opcodes.INVOKEVIRTUAL,
            "java/util/Optional",
            "get",
            "()Ljava/lang/Object;",
            false
        )
        method.visitTypeInsn(Opcodes.CHECKCAST, VELOCITY_PLUGIN_CONTAINER_CLASS)
        method.visitMethodInsn(
            Opcodes.INVOKEINTERFACE,
            VELOCITY_PLUGIN_CONTAINER_CLASS,
            "getDescription",
            "()L$VELOCITY_PLUGIN_DESCRIPTION_CLASS;",
            true
        )
        method.visitMethodInsn(
            Opcodes.INVOKEINTERFACE,
            VELOCITY_PLUGIN_DESCRIPTION_CLASS,
            "getVersion",
            "()Ljava/util/Optional;",
            true
        )
        method.visitLdcInsn("0")
        method.visitMethodInsn(
            Opcodes.INVOKEVIRTUAL,
            "java/util/Optional",
            "orElse",
            "(Ljava/lang/Object;)Ljava/lang/Object;",
            false
        )
        method.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/String")

        // local 1 is the ProxyInitializeEvent argument
        generatePlasmoVoiceVersionCheck(method, className, minVersion, messageLocal = 2) { messageLocal ->
            method.visitVarInsn(Opcodes.ALOAD, 0) // Load 'this'
            method.visitFieldInsn(Opcodes.GETFIELD, className, LOGGER_FIELD, "L$SLF4J_LOGGER_CLASS;")
            method.visitVarInsn(Opcodes.ALOAD, messageLocal)
            method.visitMethodInsn(
                Opcodes.INVOKEINTERFACE,
                SLF4J_LOGGER_CLASS,
                "error",
                "(Ljava/lang/String;)V",
                true
            )
        }
    }

    private fun generateInjectedField(cv: ClassVisitor, name: String, descriptor: String) {
        val fv = cv.visitField(Opcodes.ACC_PRIVATE, name, descriptor, null, null)
        fv.visitAnnotation("Lcom/google/inject/Inject;", true).visitEnd()
        fv.visitEnd()
    }

    companion object {
        private const val PROXY_SERVER_FIELD = "plasmoVoiceProxyServer"
        private const val LOGGER_FIELD = "plasmoVoiceLogger"

        private const val VELOCITY_PROXY_SERVER_CLASS = "com/velocitypowered/api/proxy/ProxyServer"
        private const val VELOCITY_PLUGIN_MANAGER_CLASS = "com/velocitypowered/api/plugin/PluginManager"
        private const val VELOCITY_PLUGIN_CONTAINER_CLASS = "com/velocitypowered/api/plugin/PluginContainer"
        private const val VELOCITY_PLUGIN_DESCRIPTION_CLASS = "com/velocitypowered/api/plugin/PluginDescription"
        private const val SLF4J_LOGGER_CLASS = "org/slf4j/Logger"
    }
}
