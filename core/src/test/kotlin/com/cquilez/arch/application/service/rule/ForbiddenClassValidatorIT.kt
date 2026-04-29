package com.cquilez.arch.application.service.rule

import com.cquilez.arch.application.port.FilesystemPort
import com.cquilez.arch.application.port.LogPort
import com.cquilez.arch.application.service.LayerFinderService
import com.cquilez.arch.application.service.PatternMatcherService
import com.cquilez.arch.application.service.SourceParserService
import com.cquilez.arch.infrastructure.adapter.KotlinPsiSourceParser
import com.cquilez.arch.application.service.rule.RuleValidationExecutor
import com.cquilez.arch.domain.Forbidden
import com.cquilez.arch.domain.Rule
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Stream

class ForbiddenClassValidatorIT {
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
    fun `detects class matching forbidden pattern`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("OrderRepository.java")
        Files.writeString(file, "package com.example.domain;\npublic class OrderRepository {}")

        val rule = Rule(
            title = "No repositories",
            layers = emptyList(),
            types = emptyList(),
            classPatterns = null,
            allowed = null,
            forbidden = Forbidden(classPatterns = listOf("*Repository"), layers = null, types = emptyList())
        )
        val parsed = sourceParser.parseSource(file)!!

        val violations = executor.validate(
            com.cquilez.arch.domain.ProjectRules(emptyList(), emptyList(), emptyList()),
            rule,
            parsed,
            file
        )

        assertEquals(1, violations.size)
        assertTrue(violations[0].cause.contains("Forbidden class pattern matched"))
        assertTrue(violations[0].className!!.contains("OrderRepository"))
    }

    @Test
    fun `no violation when class does not match forbidden pattern`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("OrderService.java")
        Files.writeString(file, "package com.example.domain;\npublic class OrderService {}")

        val rule = Rule(
            title = "No repositories",
            layers = emptyList(),
            types = emptyList(),
            classPatterns = null,
            allowed = null,
            forbidden = Forbidden(classPatterns = listOf("*Repository"), layers = null, types = emptyList())
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
    fun `detects multiple classes matching forbidden patterns`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("MyClass.java")
        Files.writeString(file, """
            package com.example;
            public class MyClass {
                class InnerRepository {}
                interface InnerDAO {}
            }
        """.trimIndent())

        val rule = Rule(
            title = "No repositories or DAOs",
            layers = emptyList(),
            types = emptyList(),
            classPatterns = null,
            allowed = null,
            forbidden = Forbidden(classPatterns = listOf("*Repository", "*DAO"), layers = null, types = emptyList())
        )
        val parsed = sourceParser.parseSource(file)!!

        val violations = executor.validate(
            com.cquilez.arch.domain.ProjectRules(emptyList(), emptyList(), emptyList()),
            rule,
            parsed,
            file
        )

        assertEquals(2, violations.size)
    }

    @Test
    fun `returns empty when forbidden classPatterns is null`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("SomeClass.java")
        Files.writeString(file, "package com.example;\npublic class SomeClass {}")

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
    fun `qualifies class name with package when present`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("UserRepository.java")
        Files.writeString(file, "package com.example.domain;\npublic class UserRepository {}")

        val rule = Rule(
            title = "No repositories",
            layers = emptyList(),
            types = emptyList(),
            classPatterns = null,
            allowed = null,
            forbidden = Forbidden(classPatterns = listOf("*Repository"), layers = null, types = emptyList())
        )
        val parsed = sourceParser.parseSource(file)!!

        val violations = executor.validate(
            com.cquilez.arch.domain.ProjectRules(emptyList(), emptyList(), emptyList()),
            rule,
            parsed,
            file
        )

        assertEquals(1, violations.size)
        assertTrue(violations[0].className == "com.example.domain.UserRepository")
    }

    @Test
    fun `handles class without package declaration`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("NoPackageClass.java")
        Files.writeString(file, "public class NoPackageClass {}")

        val rule = Rule(
            title = "No repositories",
            layers = emptyList(),
            types = emptyList(),
            classPatterns = null,
            allowed = null,
            forbidden = Forbidden(classPatterns = listOf("*Repository"), layers = null, types = emptyList())
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
    fun `case insensitive pattern matching`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("Test.java")
        Files.writeString(file, "package com.example;\npublic class myREPOSITORY {}")

        val rule = Rule(
            title = "No repositories",
            layers = emptyList(),
            types = emptyList(),
            classPatterns = null,
            allowed = null,
            forbidden = Forbidden(classPatterns = listOf("*REPOSITORY"), layers = null, types = emptyList())
        )
        val parsed = sourceParser.parseSource(file)!!

        val violations = executor.validate(
            com.cquilez.arch.domain.ProjectRules(emptyList(), emptyList(), emptyList()),
            rule,
            parsed,
            file
        )

        assertEquals(1, violations.size)
    }
}
