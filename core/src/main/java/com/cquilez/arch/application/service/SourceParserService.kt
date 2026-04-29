package com.cquilez.arch.application.service

import com.cquilez.arch.application.port.FilesystemPort
import com.cquilez.arch.application.port.KotlinSourceParserPort
import com.cquilez.arch.application.port.LogPort
import com.github.javaparser.ParserConfiguration
import com.github.javaparser.ParserConfiguration.LanguageLevel
import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import java.nio.file.Path

class SourceParserService(
    private val log: LogPort,
    private val filesystem: FilesystemPort,
    private val kotlinParser: KotlinSourceParserPort
) {
    fun parseSource(path: Path): ParsedSource? {
        return try {
            val fileLines = filesystem.readAllLines(path)
            val totalLines = fileLines.size
            if (path.toString().endsWith(".java")) {
                StaticJavaParser.setConfiguration(
                    ParserConfiguration()
                        .setLanguageLevel(LanguageLevel.JAVA_21))
                val cu = StaticJavaParser.parse(path)
                val pkg = cu.packageDeclaration.map { it.nameAsString }.orElse(null)
                val classes = cu.findAll(ClassOrInterfaceDeclaration::class.java).map { it.nameAsString }
                val classLines = mutableMapOf<String, Int>()
                cu.findAll(ClassOrInterfaceDeclaration::class.java).forEach { cls ->
                    val line = cls.name.range.map { it.begin.line }.orElse(null)
                    if (line != null) {
                        classLines[cls.nameAsString] = line
                    }
                }
                val imports = cu.imports.map { imp ->
                    val name = if (imp.isAsterisk) "${imp.nameAsString}.*" else imp.nameAsString
                    ImportRef(name.trim(), imp.range.map { it.begin.line }.orElse(null))
                }
                val methodCounts = mutableMapOf<String, Int>()
                cu.findAll(ClassOrInterfaceDeclaration::class.java).forEach { cls ->
                    val publicMethods =
                        cls.methods.count { method: MethodDeclaration -> method.isPublic }
                    methodCounts[cls.nameAsString] = publicMethods
                }
                val typeRefs = cu.findAll(com.github.javaparser.ast.type.ClassOrInterfaceType::class.java)
                    .map { type ->
                        TypeRef(type.nameAsString, type.range.map { it.begin.line }.orElse(null))
                    }
                ParsedSource(pkg, classes, classLines, imports, methodCounts, typeRefs, totalLines)
            } else if (path.toString().endsWith(".kt")) {
                val content = fileLines.joinToString("\n")
                kotlinParser.parse(content)
            } else {
                null
            }
        } catch (e: Exception) {
            this.log.warn("Failed to parse ${path.toAbsolutePath()}: ${e.message}")
            null
        }
    }

    data class ParsedSource(
        val packageName: String?,
        val classNames: List<String>,
        val classDeclarationLines: Map<String, Int>,
        val imports: List<ImportRef>,
        val publicMethodCounts: Map<String, Int>,
        val typeRefs: List<TypeRef>,
        val totalLines: Int
    )

    data class ImportRef(
        val name: String,
        val line: Int?
    )

    data class TypeRef(
        val name: String,
        val line: Int?
    )
}
