package su.plo.voice.plugin.entrypoint

import com.github.javaparser.ast.Modifier
import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.expr.*
import com.github.javaparser.ast.stmt.BlockStmt
import com.github.javaparser.ast.type.ClassOrInterfaceType
import org.gradle.api.Project
import su.plo.voice.plugin.AddonMeta
import java.io.File

abstract class AddonEntryPoint {

    protected fun generateServerAddonsLoaders(
        entryPointClass: ClassOrInterfaceDeclaration,
        addons: List<AddonMeta>,
        block: BlockStmt = BlockStmt()
    ): BlockStmt {
        addons.filter { it.loaderScope.isServer }.forEach { addon ->
            generateLoadMethod(entryPointClass, block, "PlasmoVoiceServer", addon)
        }

        return block
    }

    protected fun generateServerAddonsUnloaders(
        entryPointClass: ClassOrInterfaceDeclaration,
        addons: List<AddonMeta>,
        block: BlockStmt = BlockStmt()
    ): BlockStmt {
        addons.filter { it.loaderScope.isServer }.forEach { addon ->
            generateUnloadMethod(entryPointClass, block, "PlasmoVoiceServer", addon)
        }

        return block
    }

    protected fun generateClientAddonsLoaders(
        entryPointClass: ClassOrInterfaceDeclaration,
        addons: List<AddonMeta>,
        block: BlockStmt = BlockStmt()
    ): BlockStmt {
        addons.filter { it.loaderScope.isClient }.forEach { addon ->
            generateLoadMethod(entryPointClass, block, "PlasmoVoiceClient", addon)
        }

        return block
    }

    protected fun generateProxyAddonsLoaders(
        entryPointClass: ClassOrInterfaceDeclaration,
        addons: List<AddonMeta>,
        block: BlockStmt = BlockStmt()
    ): BlockStmt {
        addons.filter { it.loaderScope.isProxy }.forEach { addon ->
            generateLoadMethod(entryPointClass, block, "PlasmoVoiceProxy", addon)
        }

        return block
    }

    private fun generateLoadMethod(
        entryPointClass: ClassOrInterfaceDeclaration,
        block: BlockStmt,
        addonLoaderClass: String,
        addon: AddonMeta
    ) = generateLoaderMethod(entryPointClass, block, addonLoaderClass, addon, "load")

    private fun generateUnloadMethod(
        entryPointClass: ClassOrInterfaceDeclaration,
        block: BlockStmt,
        addonLoaderClass: String,
        addon: AddonMeta
    ) = generateLoaderMethod(entryPointClass, block, addonLoaderClass, addon, "unload")

    private fun generateLoaderMethod(
        entryPointClass: ClassOrInterfaceDeclaration,
        block: BlockStmt,
        addonLoaderClass: String,
        addon: AddonMeta,
        method: String
    ) {
        val addonId = addon.id.replace("-[a-z]".toRegex()) { matchResult ->
            matchResult.value.substring(1).capitalize()
        }

        if (!entryPointClass.fields.any { it.getVariable(0).name.identifier == addonId }) {
            entryPointClass.addFieldWithInitializer(
                addon.entryPoint,
                addonId,
                ObjectCreationExpr(
                    null,
                    ClassOrInterfaceType(null, addon.entryPoint),
                    NodeList()
                ),
                Modifier.Keyword.PRIVATE
            )
        }

        val plasmoVoiceServer = NameExpr(addonLoaderClass)
        val getAddonManagerInstance = MethodCallExpr(plasmoVoiceServer, "getAddonsLoader")
        val addonManagerLoad = MethodCallExpr(getAddonManagerInstance, method)

        addonManagerLoad.addArgument(
            FieldAccessExpr(
                ThisExpr(),
                addonId
            )
        )

        block.addStatement(addonManagerLoad)
    }

    abstract fun generate(project: Project, addons: List<AddonMeta>)

    protected abstract fun generateMetaFile(project: Project, packageName: String, addons: List<AddonMeta>)

    protected abstract fun generateJavaFile(project: Project, packageName: String, addons: List<AddonMeta>)

    companion object {

        fun processAddons(project: Project, addons: List<AddonMeta>): List<File> {
            BukkitAddonEntryPoint.generate(project, addons)

            FabricAddonEntryPoint.generate(project, addons)
            ForgeAddonEntryPoint.generate(project, addons)

            VelocityAddonEntryPoint.generate(project, addons)
            BungeeCordAddonEntryPoint.generate(project, addons)

            return emptyList()
        }
    }
}
