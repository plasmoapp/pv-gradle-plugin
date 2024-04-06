package su.plo.voice.plugin.parser

import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import java.io.File
import kotlin.jvm.optionals.getOrNull

fun parseStringField(sourceFiles: Collection<File>, className: String, fieldName: String): String {
    sourceFiles.forEach { file ->
        val fileName = file.name

        if (fileName.endsWith(".java")) {
            val parsedFile = StaticJavaParser.parse(file)

            val foundClass = parsedFile.childNodes
                .filterIsInstance<ClassOrInterfaceDeclaration>()
                .firstOrNull { it.nameAsString == className }
                ?: return@forEach

            val field = foundClass.getFieldByName(fieldName).getOrNull() ?: return@forEach

            return field.getVariable(0).initializer.get().asStringLiteralExpr().asString()
        }
//        else if (fileName.endsWith(".kt")) {
//
//        }
    }

    throw IllegalStateException("Field $fieldName in $className not found")
}
