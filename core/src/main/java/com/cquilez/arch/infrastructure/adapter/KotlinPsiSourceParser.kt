package com.cquilez.arch.infrastructure.adapter

import com.cquilez.arch.application.port.KotlinSourceParserPort
import com.cquilez.arch.application.service.SourceParserService
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.psiUtil.isPrivate
import org.jetbrains.kotlin.psi.psiUtil.isProtected
import org.jetbrains.kotlin.psi.psiUtil.isPublic
import org.jetbrains.kotlin.psi.KtPsiFactory

class KotlinPsiSourceParser : KotlinSourceParserPort {

    override fun parse(content: String): SourceParserService.ParsedSource {
        val disposable = Disposer.newDisposable()
        return try {
            val configuration = CompilerConfiguration().also {
            it.put(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
            }
            val environment = KotlinCoreEnvironment.createForProduction(
                disposable,
                configuration,
                EnvironmentConfigFiles.JVM_CONFIG_FILES
            )
            val factory = KtPsiFactory(environment.project)
            val ktFile: KtFile = factory.createFile(content)
            this.extractParsedSource(ktFile, content)
        } finally {
            Disposer.dispose(disposable)
        }
    }

    private fun extractParsedSource(ktFile: KtFile, content: String): SourceParserService.ParsedSource {
        val packageName = ktFile.packageFqName.asString().takeIf { it.isNotEmpty() }
        val imports = this.extractImports(ktFile)
        val classNames = mutableListOf<String>()
        val classDeclarationLines = mutableMapOf<String, Int>()
        val publicMethodCounts = mutableMapOf<String, Int>()
        val lines = content.lines()

        ktFile.declarations.forEach { declaration ->
            when (declaration) {
                is KtClass -> this.processClassDeclaration(
                    declaration, lines, classNames, classDeclarationLines, publicMethodCounts
                )
                is KtObjectDeclaration -> this.processObjectDeclaration(
                    declaration, lines, classNames, classDeclarationLines, publicMethodCounts
                )
                else -> Unit
            }
        }

        return SourceParserService.ParsedSource(
            packageName = packageName,
            classNames = classNames,
            classDeclarationLines = classDeclarationLines,
            imports = imports,
            publicMethodCounts = publicMethodCounts,
            typeRefs = emptyList(),
            totalLines = lines.size
        )
    }

    private fun extractImports(ktFile: KtFile): List<SourceParserService.ImportRef> {
        return ktFile.importDirectives.map { directive: KtImportDirective ->
            val name = directive.importedFqName?.asString() ?: ""
            val line = directive.textOffset.let { offset ->
                ktFile.text.substring(0, offset).count { it == '\n' } + 1
            }
            SourceParserService.ImportRef(name, line)
        }
    }

    private fun processClassDeclaration(
        klass: KtClass,
        lines: List<String>,
        classNames: MutableList<String>,
        classDeclarationLines: MutableMap<String, Int>,
        publicMethodCounts: MutableMap<String, Int>
    ) {
        val name = klass.name ?: return
        classNames.add(name)
        classDeclarationLines[name] = this.resolveLineNumber(klass, lines)
        publicMethodCounts[name] = this.countPublicMethods(klass.declarations.filterIsInstance<KtFunction>())
    }

    private fun processObjectDeclaration(
        obj: KtObjectDeclaration,
        lines: List<String>,
        classNames: MutableList<String>,
        classDeclarationLines: MutableMap<String, Int>,
        publicMethodCounts: MutableMap<String, Int>
    ) {
        val name = obj.name ?: return
        classNames.add(name)
        classDeclarationLines[name] = this.resolveLineNumber(obj, lines)
        publicMethodCounts[name] = this.countPublicMethods(obj.declarations.filterIsInstance<KtFunction>())
    }

    private fun countPublicMethods(functions: List<KtFunction>): Int {
        return functions.count { fn ->
            !fn.isPrivate() && !fn.isProtected() && !isInternal(fn)
        }
    }

    private fun isInternal(declaration: KtNamedDeclaration): Boolean {
        return declaration.modifierList?.hasModifier(
            org.jetbrains.kotlin.lexer.KtTokens.INTERNAL_KEYWORD
        ) == true
    }

    private fun resolveLineNumber(declaration: KtNamedDeclaration, lines: List<String>): Int {
        val offset = declaration.textOffset
        val textBefore = declaration.containingFile.text.substring(0, offset)
        return textBefore.count { it == '\n' } + 1
    }
}
