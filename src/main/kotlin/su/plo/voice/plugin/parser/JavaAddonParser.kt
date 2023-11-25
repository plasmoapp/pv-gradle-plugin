package su.plo.voice.plugin.parser

import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.expr.*
import su.plo.voice.plugin.AddonMeta
import su.plo.voice.api.addon.AddonDependency
import su.plo.voice.api.addon.AddonLoaderScope
import java.io.File

object JavaAddonParser : AddonParser {

    override fun parseAddon(file: File): AddonMeta? {
        val parsedFile = try {
            StaticJavaParser.parse(file)
            // todo: "Text Block Literals are not supported." Just skip for now, but it can be a problem with modern java code
        } catch (e: Exception) {
            return null
        }
        parsedFile.childNodes.forEach { node ->
            if (node !is ClassOrInterfaceDeclaration) return@forEach

            val addonAnnotation = node.annotations
                .filter { it.nameAsString == "Addon" }
                .getOrNull(0) ?: return@forEach

            return parseAddon(
                addonAnnotation,
                parsedFile.packageDeclaration.get().nameAsString + "." + node.nameAsString
            )
        }

        return null
    }
}

private fun parseAddon(addonAnnotation: AnnotationExpr, entryPoint: String): AddonMeta {
    var id: String? = null
    var name: String? = null
    var loaderScope: AddonLoaderScope? = null
    var version: String? = null
    var license: String? = null
    val authors: MutableList<String> = ArrayList()
    val dependencies: MutableList<AddonDependency> = ArrayList()

    addonAnnotation.childNodes.forEach { annotationNode ->
        if (annotationNode !is MemberValuePair) return@forEach

        when (annotationNode.nameAsString) {

            "id" -> {
                id = (annotationNode.value as StringLiteralExpr).asString()
            }

            "name" -> {
                name = (annotationNode.value as StringLiteralExpr).asString()
            }

            "scope" -> {
                val fieldAccess = annotationNode.value as FieldAccessExpr
                loaderScope = AddonLoaderScope.valueOf(fieldAccess.nameAsString)
            }

            "version" -> {
                version = (annotationNode.value as StringLiteralExpr).asString()
            }

            "license" -> {
                license = (annotationNode.value as StringLiteralExpr).asString()
            }

            "authors" -> {
                val array = annotationNode.value as ArrayInitializerExpr
                array.values.forEach { author ->
                    authors.add((author as StringLiteralExpr).asString())
                }
            }

            "dependencies" -> {
                val array = annotationNode.value as ArrayInitializerExpr
                array.values.forEach {
                    dependencies.add(parseDependency(it as AnnotationExpr))
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

private fun parseDependency(dependencyAnnotation: AnnotationExpr): AddonDependency {
    var id: String? = null
    var optional = false

    dependencyAnnotation.childNodes.forEach { annotationNode ->
        if (annotationNode !is MemberValuePair) return@forEach

        when (annotationNode.nameAsString) {

            "id" -> {
                id = (annotationNode.value as StringLiteralExpr).asString()
            }

            "optional" -> {
                optional = (annotationNode.value as BooleanLiteralExpr).value
            }
        }
    }

    return AddonDependency(requireNotNull(id), optional)
}

