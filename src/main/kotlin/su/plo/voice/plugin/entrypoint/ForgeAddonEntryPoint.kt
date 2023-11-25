package su.plo.voice.plugin.entrypoint

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import org.gradle.api.Project
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import su.plo.config.toml.TomlWriter
import su.plo.voice.api.addon.AddonLoaderScope
import su.plo.voice.plugin.AddonMeta
import su.plo.voice.plugin.extension.visitCodeThenEnd
import java.io.File

object ForgeAddonEntryPoint : AddonEntryPoint() {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    override fun generate(project: Project, addons: List<AddonMeta>) {
        val serverClientAddons = addons.filter { it.loaderScope.isClient || it.loaderScope.isServer }
        if (serverClientAddons.isEmpty()) return

        val packageName = serverClientAddons[0].entryPoint.substringBeforeLast(".")

        generateJavaFile(project, packageName, serverClientAddons)
        generateMetaFile(project, packageName, serverClientAddons)
    }

    override fun generateMetaFile(project: Project, packageName: String, addons: List<AddonMeta>) {
        val addon = addons[0]

        val modsToml: MutableMap<String, Any> = HashMap()

        modsToml["modLoader"] = "javafml"
        modsToml["loaderVersion"] = "[36,)"
        modsToml["license"] = addon.license
        modsToml["mods"] = listOf(
            HashMap<String, Any>().also { mod ->
                mod["modId"] = addon.id.replace("-", "_")
                mod["version"] = addon.version
                mod["displayName"] = addon.id
                mod["authors"] = addon.authors.joinToString(", ")
            }
        )
        modsToml["dependencies"] = HashMap<String, Any>().also { dependencies ->
            val addonDependencies: MutableList<HashMap<String, Any>> = ArrayList()

            addonDependencies.add(HashMap<String, Any>().also { dependency ->
                dependency["modId"] = "plasmovoice"
                dependency["mandatory"] = true
                dependency["versionRange"] = "[2.1.0,)"
                dependency["ordering"] = "AFTER"
            })

            addon.dependencies.forEach { addonDependency ->
                addonDependencies.add(HashMap<String, Any>().also { dependency ->
                    dependency["modId"] = addonDependency.id.replace("-", "_")
                    dependency["mandatory"] = !addonDependency.isOptional
                    dependency["ordering"] = "AFTER"
                    dependency["versionRange"] = "[0,)"

                    if (addon.loaderScope != AddonLoaderScope.ANY) {
                        if (addon.loaderScope.isClient) {
                            dependency["side"] = "CLIENT"
                        } else {
                            dependency["side"] = "SERVER"
                        }
                    }
                })
            }

            dependencies[addon.id.replace("-", "_")] = addonDependencies
        }

        val packMcMeta = JsonObject()
        packMcMeta.add("pack", JsonObject().also { pack ->
            pack.addProperty("description", "${addon.id} generated resources")
            pack.addProperty("pack_format", 6)
        })

        val resourcesDir = File(
            project.buildDir,
            "generated/sources/plasmovoice/resources"
        ).also { it.mkdirs() }

        val metaInfDir = File(resourcesDir, "META-INF")
            .also { it.mkdirs() }

        TomlWriter().write(modsToml, File(
            metaInfDir,
            "mods.toml"
        ))

        File(
            resourcesDir,
            "pack.mcmeta"
        ).writeText(gson.toJson(packMcMeta))
    }

    override fun generateJavaFile(project: Project, packageName: String, addons: List<AddonMeta>) {
        // Define class
        val className = "ForgeEntryPoint"
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

        // Add @Mod annotation
        val addon = addons[0]
        val modAnnotation = cw.visitAnnotation("Lnet/minecraftforge/fml/common/Mod;", true)
        modAnnotation.visit("value", addon.id.replace("-", "_"))
        modAnnotation.visitEnd()

        // Add constructor
        val constructorMethod = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        constructorMethod.visitCodeThenEnd {
            generateConstructor(cw, constructorMethod, fullClassName, classOwner, addons)
            generateServerAddonsLoaders(constructorMethod, fullClassName, addons)
            generateClientAddonsLoaders(constructorMethod, fullClassName, addons)
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
