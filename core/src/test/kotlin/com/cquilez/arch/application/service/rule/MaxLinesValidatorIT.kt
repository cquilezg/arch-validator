package com.cquilez.arch.application.service.rule

import com.cquilez.arch.application.port.FilesystemPort
import com.cquilez.arch.application.port.LogPort
import com.cquilez.arch.application.service.LayerFinderService
import com.cquilez.arch.application.service.PatternMatcherService
import com.cquilez.arch.application.service.SourceParserService
import com.cquilez.arch.infrastructure.adapter.KotlinPsiSourceParser
import com.cquilez.arch.application.service.rule.RuleValidationExecutor
import com.cquilez.arch.domain.Allowed
import com.cquilez.arch.domain.Rule
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Stream

class MaxLinesValidatorIT {
    private val log: LogPort = mockk(relaxed = true)
    private val filesystem = object : FilesystemPort {
        override fun exists(path: Path) = Files.exists(path)
        override fun isDirectory(path: Path) = Files.isDirectory(path)
        override fun isRegularFile(path: Path) = Files.isRegularFile(path)
        override fun walk(path: Path): Stream<Path> = Files.walk(path)
        override fun readAllLines(path: Path) = Files.readAllLines(path)
        override fun readString(path: Path) = Files.readString(path)
    }
    private val patternMatcher = PatternMatcherService()
    private val layerFinder = LayerFinderService()
    private val kotlinParser = KotlinPsiSourceParser()
    private val sourceParser = SourceParserService(log, filesystem, kotlinParser)
    private val executor = RuleValidationExecutor(patternMatcher, layerFinder)

    @Test
    fun `detects file exceeding max lines`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("LargeFile.java")
        val lines = (1..350).joinToString("\n") { "// line $it" }
        Files.writeString(file, lines)

        val rule = Rule(
            title = "Max 300 lines",
            layers = emptyList(),
            types = emptyList(),
            classPatterns = null,
            allowed = Allowed(imports = null, allowedLayers = null, methods = null, maxLines = 300),
            forbidden = null
        )
        val parsed = sourceParser.parseSource(file)!!

        val violations = executor.validate(
            com.cquilez.arch.domain.ProjectRules(emptyList(), emptyList(), emptyList()),
            rule,
            parsed,
            file
        )

        assertEquals(1, violations.size)
        assertTrue(violations[0].cause.contains("File has too many lines"))
        assertTrue(violations[0].cause.contains("350"))
        assertTrue(violations[0].cause.contains("300"))
    }

    @Test
    fun `no violation when under max lines`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("SmallFile.java")
        val lines = (1..100).joinToString("\n") { "// line $it" }
        Files.writeString(file, lines)

        val rule = Rule(
            title = "Max 300 lines",
            layers = emptyList(),
            types = emptyList(),
            classPatterns = null,
            allowed = Allowed(imports = null, allowedLayers = null, methods = null, maxLines = 300),
            forbidden = null
        )
        val parsed = sourceParser.parseSource(file)!!

        val violations = executor.validate(
            com.cquilez.arch.domain.ProjectRules(emptyList(), emptyList(), emptyList()),
            rule,
            parsed,
            file
        )

        assertTrue(violations.isEmpty())
    }

    @Test
    fun `no violation when exactly at max lines`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("ExactFile.java")
        val lines = (1..300).joinToString("\n") { "// line $it" }
        Files.writeString(file, lines)

        val rule = Rule(
            title = "Max 300 lines",
            layers = emptyList(),
            types = emptyList(),
            classPatterns = null,
            allowed = Allowed(imports = null, allowedLayers = null, methods = null, maxLines = 300),
            forbidden = null
        )
        val parsed = sourceParser.parseSource(file)!!

        val violations = executor.validate(
            com.cquilez.arch.domain.ProjectRules(emptyList(), emptyList(), emptyList()),
            rule,
            parsed,
            file
        )

        assertTrue(violations.isEmpty())
    }

    @Test
    fun `returns empty when allowed is null`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("AnyFile.java")
        val lines = (1..500).joinToString("\n") { "// line $it" }
        Files.writeString(file, lines)

        val rule = Rule(
            title = "No restriction",
            layers = emptyList(),
            types = emptyList(),
            classPatterns = null,
            allowed = null,
            forbidden = null
        )
        val parsed = sourceParser.parseSource(file)!!

        val violations = executor.validate(
            com.cquilez.arch.domain.ProjectRules(emptyList(), emptyList(), emptyList()),
            rule,
            parsed,
            file
        )

        assertTrue(violations.isEmpty())
    }

    @Test
    fun `returns empty when maxLines is null`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("AnyFile.java")
        val lines = (1..500).joinToString("\n") { "// line $it" }
        Files.writeString(file, lines)

        val rule = Rule(
            title = "No max lines restriction",
            layers = emptyList(),
            types = emptyList(),
            classPatterns = null,
            allowed = Allowed(imports = null, allowedLayers = null, methods = null, maxLines = null),
            forbidden = null
        )
        val parsed = sourceParser.parseSource(file)!!

        val violations = executor.validate(
            com.cquilez.arch.domain.ProjectRules(emptyList(), emptyList(), emptyList()),
            rule,
            parsed,
            file
        )

        assertTrue(violations.isEmpty())
    }

    @Test
    fun `violation line is null since it applies to entire file`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("LargeFile.java")
        val lines = (1..350).joinToString("\n") { "// line $it" }
        Files.writeString(file, lines)

        val rule = Rule(
            title = "Max 300 lines",
            layers = emptyList(),
            types = emptyList(),
            classPatterns = null,
            allowed = Allowed(imports = null, allowedLayers = null, methods = null, maxLines = 300),
            forbidden = null
        )
        val parsed = sourceParser.parseSource(file)!!

        val violations = executor.validate(
            com.cquilez.arch.domain.ProjectRules(emptyList(), emptyList(), emptyList()),
            rule,
            parsed,
            file
        )

        assertEquals(1, violations.size)
        assertTrue(violations[0].line == null)
    }

    @Test
    fun `reports class name when available`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("LargeFile.java")
        Files.writeString(file, """
            package com.example.domain;
            public class LargeFile {
                ${(1..350).joinToString("\n") { "    // line ${it - 2}" }}
            }
        """.trimIndent())

        val rule = Rule(
            title = "Max 300 lines",
            layers = emptyList(),
            types = emptyList(),
            classPatterns = null,
            allowed = Allowed(imports = null, allowedLayers = null, methods = null, maxLines = 300),
            forbidden = null
        )
        val parsed = sourceParser.parseSource(file)!!

        val violations = executor.validate(
            com.cquilez.arch.domain.ProjectRules(emptyList(), emptyList(), emptyList()),
            rule,
            parsed,
            file
        )

        assertEquals(1, violations.size)
        assertTrue(violations[0].className!!.contains("LargeFile"))
    }

    @Test
    fun `handles Kotlin files`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("LargeFile.kt")
        val lines = (1..150).joinToString("\n") { "// line $it" }
        Files.writeString(file, lines)

        val rule = Rule(
            title = "Max 100 lines",
            layers = emptyList(),
            types = emptyList(),
            classPatterns = null,
            allowed = Allowed(imports = null, allowedLayers = null, methods = null, maxLines = 100),
            forbidden = null
        )
        val parsed = sourceParser.parseSource(file)!!

        val violations = executor.validate(
            com.cquilez.arch.domain.ProjectRules(emptyList(), emptyList(), emptyList()),
            rule,
            parsed,
            file
        )

        assertEquals(1, violations.size)
        assertTrue(violations[0].cause.contains("150"))
    }

    @Test
    fun `handles file with single line`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("SingleLine.java")
        Files.writeString(file, "public class SingleLine {}")

        val rule = Rule(
            title = "Max 1 line",
            layers = emptyList(),
            types = emptyList(),
            classPatterns = null,
            allowed = Allowed(imports = null, allowedLayers = null, methods = null, maxLines = 1),
            forbidden = null
        )
        val parsed = sourceParser.parseSource(file)!!

        val violations = executor.validate(
            com.cquilez.arch.domain.ProjectRules(emptyList(), emptyList(), emptyList()),
            rule,
            parsed,
            file
        )

        assertTrue(violations.isEmpty())
    }

    @Test
    fun `handles empty file`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("EmptyFile.java")
        Files.writeString(file, "")

        val rule = Rule(
            title = "Max 0 lines",
            layers = emptyList(),
            types = emptyList(),
            classPatterns = null,
            allowed = Allowed(imports = null, allowedLayers = null, methods = null, maxLines = 0),
            forbidden = null
        )
        val parsed = sourceParser.parseSource(file)!!

        val violations = executor.validate(
            com.cquilez.arch.domain.ProjectRules(emptyList(), emptyList(), emptyList()),
            rule,
            parsed,
            file
        )

        assertTrue(violations.isEmpty())
    }
}
