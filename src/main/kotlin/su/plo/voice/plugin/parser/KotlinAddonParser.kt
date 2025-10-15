package su.plo.voice.plugin.parser

import kotlinx.ast.common.AstSource
import kotlinx.ast.common.ast.Ast
import kotlinx.ast.common.ast.AstNode
import kotlinx.ast.common.ast.DefaultAstNode
import kotlinx.ast.common.ast.DefaultAstTerminal
import kotlinx.ast.common.klass.*
import kotlinx.ast.grammar.kotlin.common.summary
import kotlinx.ast.grammar.kotlin.common.summary.PackageHeader
import kotlinx.ast.grammar.kotlin.target.antlr.kotlin.KotlinGrammarAntlrKotlinParser
import su.plo.voice.plugin.AddonMeta
import su.plo.voice.api.addon.AddonDependency
import su.plo.voice.api.addon.AddonLoaderScope
import java.io.File

object KotlinAddonParser : AddonParser {

    override fun parseAddon(sourceFiles: Collection<File>, file: File): AddonMeta? {
        val source = AstSource.File(file.absolutePath.toString())

        val ast = KotlinGrammarAntlrKotlinParser.parseKotlinFile(source)

        if (!file.name.contains("addon", true)) return null

        val astList = ast.summary(attachRawAst = false).get()
        val packageName = astList
            .filterIsInstance<PackageHeader>()
            .getOrNull(0)
            ?.let { header ->
                header.identifier.joinToString(".") { it.identifier }
            }


        astList.forEach { astFile ->
            if (astFile !is KlassDeclaration) return@forEach

            val addonAnnotation = astFile.annotations
                .filter { (it.identifier.getOrNull(0)?.identifier == "Addon") }
                .getOrNull(0) ?: return@forEach

            return parseAddon(
                sourceFiles,
                addonAnnotation,
                packageName!! + "." + astFile.identifier!!.identifier
            )
        }

        return null
    }

    private fun parseAddon(sourceFiles: Collection<File>, addonAnnotation: KlassAnnotation, entryPoint: String): AddonMeta? {
        var id: String? = null
        var name: String? = null
        var loaderScope: AddonLoaderScope? = null
        var version: String? = null
        var license: String? = null
        val authors: MutableList<String> = ArrayList()
        val dependencies: MutableList<AddonDependency> = ArrayList()

        addonAnnotation.arguments.forEach { annotationNode ->
            val expression = annotationNode.expressions.getOrNull(0) ?: return@forEach

            when (annotationNode.identifier!!.identifier) {

                "id" -> {
                    id = expression.getString(sourceFiles)
                }

                "name" -> {
                    name = expression.getString(sourceFiles)
                }

                "scope" -> {
                    val loaderKlass = expression as KlassIdentifier
                    val terminals = findChild(loaderKlass.children[0] as DefaultAstNode, DefaultAstTerminal::class.java)

                    loaderScope = AddonLoaderScope.valueOf(terminals.last().text)
                }

                "version" -> {
                    version = expression.getString(sourceFiles)
                }

                "license" -> {
                    license = expression.getString(sourceFiles)
                }

                "authors" -> {
                    val terminals = findChild(expression as DefaultAstNode, DefaultAstTerminal::class.java)
                    terminals.filter { it.description == "LineStrText" }.forEach {
                        authors.add(it.text)
                    }
                }

                "dependencies" -> {
                    val terminals = findChild(expression as DefaultAstNode, DefaultAstTerminal::class.java)

                    var lastDependencyName: String? = null
                    var lastDependencyOptional: Boolean? = null

                    terminals
                        .filter { it.description == "LineStrText" || it.description == "BooleanLiteral" }
                        .forEach { terminal ->
                            if (terminal.description == "LineStrText") {
                                lastDependencyName?.let {
                                    dependencies.add(AddonDependency(
                                        it,
                                        lastDependencyOptional ?: false
                                    ))
                                }
                                lastDependencyName = terminal.text
                            } else {
                                lastDependencyName?.let {
                                    lastDependencyOptional?.let { optional ->
                                        dependencies.add(AddonDependency(it, optional))
                                        lastDependencyOptional = null
                                        lastDependencyName = null
                                    } ?: run {
                                        lastDependencyOptional = terminal.text.toBoolean()
                                    }
                                }
                            }
                        }

                    lastDependencyName?.let {
                        dependencies.add(AddonDependency(it, lastDependencyOptional ?: false))
                    }
                }
            }
        }

        return AddonMeta(
            id!!,
            name ?: id!!,
            loaderScope!!,
            version!!,
            license ?: "MIT",
            authors,
            dependencies,
            entryPoint
        )
    }

    private fun <T> findChild(astNode: AstNode, klass: Class<T>): List<T> {
        return astNode.children.mapNotNull { child ->
            try {
                val terminal = klass.cast(child)
                if (terminal is AstNode) {
                    return@mapNotNull listOf(terminal).plus(
                        findChild(terminal, klass)
                    )
                }

                return@mapNotNull listOf(terminal)
            } catch (e: ClassCastException) {
                if (child !is AstNode) return@mapNotNull null

                return@mapNotNull findChild(child, klass)
            }
        }.flatten()
    }

    private fun Ast.getString(sourceFiles: Collection<File>): String {
        if (this is KlassIdentifier) {
            val terminals = findChild(this, DefaultAstTerminal::class.java)

            return parseStringField(sourceFiles, this.identifier, terminals[1].text)
        } else {
            return ((this as KlassString).children[0] as StringComponentRaw).string
        }
    }
}

