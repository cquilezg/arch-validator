package com.cquilez.arch.application.service

import com.cquilez.arch.application.port.FilesystemPort
import com.cquilez.arch.application.port.LogPort
import com.cquilez.arch.application.service.rule.RuleValidationExecutor
import com.cquilez.arch.domain.Allowed
import com.cquilez.arch.domain.Layer
import com.cquilez.arch.domain.ProjectRules
import com.cquilez.arch.domain.Rule
import com.cquilez.arch.infrastructure.adapter.KotlinPsiSourceParser
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Stream

class RuleEvaluatorServiceIT {
    private val log: LogPort = mockk<LogPort>(relaxed = true)

    private val filesystem = object : FilesystemPort {
        override fun exists(path: Path): Boolean = Files.exists(path)
        override fun isDirectory(path: Path): Boolean = Files.isDirectory(path)
        override fun isRegularFile(path: Path): Boolean = Files.isRegularFile(path)
        override fun walk(path: Path): Stream<Path> = Files.walk(path)
        override fun readAllLines(path: Path): List<String> = Files.readAllLines(path)
        override fun readString(path: Path): String = Files.readString(path)
    }

    @Test
    fun allowsLombokAndSameLayerImports() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("Order.java")
        Files.writeString(
            file, """
            package es.nter.store.domain;

            import es.nter.store.domain.model.Article;
            import lombok.Data;

            public class Order {}
            """.trimIndent()
        )

        val rule = Rule(
            title = "Domain should only use Java and Lombok classes",
            layers = listOf("domain"),
            types = emptyList(),
            classPatterns = null,
            allowed = Allowed(listOf("java.*", "lombok.*"), null, null),
            forbidden = null
        )
        val projectRules = ProjectRules(
            layers = listOf(Layer("domain", "es.nter.store.domain")),
            types = emptyList(),
            rules = listOf(rule)
        )

        val kotlinParser = KotlinPsiSourceParser()
        val evaluator = RuleEvaluatorService(
            log,
            filesystem,
            LayerFinderService(),
            PatternMatcherService(),
            RuleValidationExecutor(PatternMatcherService(), LayerFinderService()),
            SourceParserService(log, filesystem, kotlinParser)
        )
        val analysis = evaluator.evaluateRules(
            sourceRoots = listOf(tempDir.toString()),
            projectRules = projectRules
        )

        assertEquals(0, analysis.totalViolations())
    }

    @Disabled
    @Test
    fun detectsMaxLinesViolation() {
        val tempDir = Files.createTempDirectory("arch-test-maxlines")
        val file = tempDir.resolve("LargeFile.java")
        val content = (1..350).joinToString("\n") { "line $it" }
        Files.writeString(file, content)

        val rule = Rule(
            title = "Classes should have less than 300 lines",
            layers = emptyList(),
            types = emptyList(),
            classPatterns = null,
            allowed = Allowed(null, null, null, 300),
            forbidden = null
        )
        val projectRules = ProjectRules(
            layers = emptyList(),
            types = emptyList(),
            rules = listOf(rule)
        )

        val kotlinParser = KotlinPsiSourceParser()
        val evaluator = RuleEvaluatorService(
            log,
            filesystem,
            LayerFinderService(),
            PatternMatcherService(),
            RuleValidationExecutor(PatternMatcherService(), LayerFinderService()),
            SourceParserService(log, filesystem, kotlinParser)
        )
        val analysis = evaluator.evaluateRules(
            sourceRoots = listOf(tempDir.toString()),
            projectRules = projectRules
        )

        assertEquals(1, analysis.totalViolations())
        assertEquals("File has too many lines: 350 (max: 300)", analysis.violations[rule]!![0].cause)
    }
}
