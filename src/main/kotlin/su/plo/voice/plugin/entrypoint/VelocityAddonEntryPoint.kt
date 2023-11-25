package su.plo.voice.plugin.entrypoint

import com.github.javaparser.ast.CompilationUnit
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.gradle.api.Project
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import su.plo.voice.plugin.AddonMeta
import su.plo.voice.plugin.extension.visitCodeThenEnd
import java.io.File

object VelocityAddonEntryPoint : AddonEntryPoint() {

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
            project.buildDir,
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
            null,
            null
        )

        // Add constructor
        val constructorMethod = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        constructorMethod.visitCodeThenEnd {
            generateConstructor(cw, constructorMethod, fullClassName, classOwner, addons)
        }

        // Add onLoad method
        val loadMethod = cw.visitMethod(Opcodes.ACC_PUBLIC, "onProxyInitialization", "(Lcom.velocitypowered.api.event.proxy.ProxyInitializeEvent;)V", null, null)
        loadMethod.visitAnnotation("Lcom.velocitypowered.api.event.Subscribe;", true)
        loadMethod.visitCodeThenEnd {
            generateProxyAddonsLoaders(loadMethod, fullClassName, addons)
        }

        // Generate bytecode
        cw.visitEnd()

        val packageDir = File(
            project.buildDir,
            "classes/java/main/${packageName.replace(".", "/")}"
        ).also { it.mkdirs() }

        File(
            packageDir,
            "${className}.class"
        ).writeBytes(cw.toByteArray())
    }
}
