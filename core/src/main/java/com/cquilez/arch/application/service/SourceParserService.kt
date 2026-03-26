package com.cquilez.arch.application.service

import com.cquilez.arch.application.port.FilesystemPort
import com.cquilez.arch.application.port.LogPort
import com.github.javaparser.ParserConfiguration
import com.github.javaparser.ParserConfiguration.LanguageLevel
import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import java.nio.file.Path

class SourceParserService(
    private val log: LogPort,
    private val filesystem: FilesystemPort
) {
    private data class ClassCtx(val name: String, val depth: Int)

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
                parseKotlinSource(fileLines)
            } else {
                null
            }
        } catch (e: Exception) {
            log.warn("Failed to parse ${path.toAbsolutePath()}: ${e.message}")
            null
        }
    }

    private fun parseKotlinSource(lines: List<String>): ParsedSource {
        var pkg: String? = null
        val imports = mutableListOf<ImportRef>()
        val classNames = mutableListOf<String>()
        val classLines = mutableMapOf<String, Int>()
        val publicMethodCounts = mutableMapOf<String, Int>()

        val classStack = mutableListOf<ClassCtx>()
        var braceDepth = 0

        lines.forEachIndexed { index, raw ->
            val line = raw.substringBefore("//")
            pkg = extractPackage(line, pkg)
            processImport(line, index, imports)
            braceDepth = updateBraceDepth(line, braceDepth)
            processClassDeclarations(line, index, classNames, classLines, classStack, braceDepth)
            processPublicMethods(line, classStack, braceDepth, publicMethodCounts)
            cleanupClassStack(classStack, braceDepth)
        }

        return ParsedSource(pkg, classNames, classLines, imports, publicMethodCounts, emptyList(), lines.size)
    }

    private fun extractPackage(line: String, currentPkg: String?): String? {
        if (currentPkg != null) return currentPkg
        val packageRegex = Regex("""^\s*package\s+([A-Za-z_][A-Za-z0-9_.]*)""")
        return packageRegex.find(line)?.groupValues?.get(1)
    }

    private fun processImport(line: String, index: Int, imports: MutableList<ImportRef>) {
        val importRegex = Regex("""^\s*import\s+([A-Za-z_][A-Za-z0-9_.*]+)""")
        importRegex.find(line)?.let {
            imports.add(ImportRef(it.groupValues[1].trim(), index + 1))
        }
    }

    private fun updateBraceDepth(line: String, currentDepth: Int): Int {
        val openCount = line.count { it == '{' }
        val closeCount = line.count { it == '}' }
        return currentDepth + (openCount - closeCount)
    }

    private fun processClassDeclarations(
        line: String,
        index: Int,
        classNames: MutableList<String>,
        classLines: MutableMap<String, Int>,
        classStack: MutableList<ClassCtx>,
        braceDepth: Int
    ) {
        val classRegex = Regex("""\b(class|interface|object)\s+([A-Za-z_][A-Za-z0-9_]*)""")
        classRegex.findAll(line).forEach { m ->
            val name = m.groupValues[2]
            classNames.add(name)
            if (!classLines.containsKey(name)) {
                classLines[name] = index + 1
            }
            classStack.add(ClassCtx(name, braceDepth))
        }
    }

    private fun processPublicMethods(
        line: String,
        classStack: List<ClassCtx>,
        braceDepth: Int,
        publicMethodCounts: MutableMap<String, Int>
    ) {
        if (classStack.isEmpty() || braceDepth <= classStack.last().depth) return
        val visibilityRegex = Regex("""\b(private|protected|internal)\b""")
        val funRegex = Regex("""\bfun\s+[A-Za-z_][A-Za-z0-9_]*""")
        if (!visibilityRegex.containsMatchIn(line) && funRegex.containsMatchIn(line)) {
            val className = classStack.last().name
            publicMethodCounts[className] = (publicMethodCounts[className] ?: 0) + 1
        }
    }

    private fun cleanupClassStack(classStack: MutableList<ClassCtx>, braceDepth: Int) {
        while (classStack.isNotEmpty() && braceDepth < classStack.last().depth) {
            classStack.removeLast()
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
