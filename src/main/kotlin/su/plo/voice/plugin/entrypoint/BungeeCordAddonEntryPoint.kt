package su.plo.voice.plugin.entrypoint

import com.github.javaparser.ast.CompilationUnit
import org.gradle.api.Project
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.yaml.snakeyaml.Yaml
import su.plo.voice.plugin.AddonMeta
import su.plo.voice.plugin.extension.visitCodeThenEnd
import java.io.File

object BungeeCordAddonEntryPoint : AddonEntryPoint() {

    override fun generate(project: Project, addons: List<AddonMeta>) {
        val proxyAddons = addons.filter { it.loaderScope.isProxy }
        if (proxyAddons.isEmpty()) return
        
        val packageName = proxyAddons[0].entryPoint.substringBeforeLast(".")

        generateJavaFile(project, packageName, proxyAddons)
        generateMetaFile(project, packageName, proxyAddons)
    }

    override fun generateMetaFile(project: Project, packageName: String, addons: List<AddonMeta>) {
        val addon = addons[0]

        val pluginYml: MutableMap<String, Any> = HashMap()

        pluginYml["name"] = addon.id
        pluginYml["version"] = addon.version
        pluginYml["author"] = addon.authors.joinToString(", ")
        pluginYml["main"] = "${packageName}.BungeeCordEntryPoint"
        pluginYml["depends"] = listOf("PlasmoVoice").plus(addon.dependencies.filter { !it.isOptional }.map { it.id })
        addon.dependencies.filter { it.isOptional }.also { dependencies ->
            if (dependencies.isEmpty()) return@also

            pluginYml["softDepends"] = dependencies
        }

        val resourcesDir = File(
            project.buildDir,
            "generated/sources/plasmovoice/resources"
        ).also { it.mkdirs() }

        File(
            resourcesDir,
            "bungee.yml"
        ).writeText(Yaml().dump(pluginYml))
    }

    override fun generateJavaFile(project: Project, packageName: String, addons: List<AddonMeta>) {
        // Define class
        val className = "BungeeCordEntryPoint"
        val fullClassName = "${packageName.replace(".", "/")}/$className"
        val classOwner = "net/md_5/bungee/api/plugin/Plugin"

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
            generateProxyAddonsLoaders(loadMethod, fullClassName, addons)
        }

        // Add onDisable method
        val unloadMethod = cw.visitMethod(Opcodes.ACC_PUBLIC, "onDisable", "()V", null, null)
        unloadMethod.visitCodeThenEnd {
            generateProxyAddonsUnloaders(unloadMethod, fullClassName, addons)
        }

        // Generate bytecode
        cw.visitEnd()

        val packageDir = File(
            project.buildDir,
            "generated/sources/plasmovoice/classes/${packageName.replace(".", "/")}"
        ).also { it.mkdirs() }

        File(
            packageDir,
            "${className}.class"
        ).writeBytes(cw.toByteArray())
    }
}
