package com.cquilez.arch.application.usecase

import com.cquilez.arch.application.port.FilesystemPort
import com.cquilez.arch.application.port.KotlinSourceParserPort
import com.cquilez.arch.application.port.LogPort
import com.cquilez.arch.application.port.ParserPort
import com.cquilez.arch.application.service.RuleEvaluatorService
import com.cquilez.arch.application.service.RuleValidatorService
import com.cquilez.arch.application.service.SourceParserService
import com.cquilez.arch.application.service.rule.RuleValidationExecutor
import com.cquilez.arch.domain.AnalysisConfig
import com.cquilez.arch.domain.Project
import com.cquilez.arch.infrastructure.adapter.YamlParserAdapter
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Stream
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.Assertions.assertEquals

class AnalyzeProjectUseCaseIT {

    enum class SampleProject(val path: String, val expectedTypes: Int, val expectedTypeCounts: List<String>) {
        MULTILAYER_AND_MULTITYPE("multilayer-and-multitype-project", 4, listOf("Repository", "UseCase", "Adapter", "Entity")),
        MULTILAYER("multilayer-project", 0, emptyList())
    }

    private val capturedLogs = mutableListOf<String>()
    private val log: LogPort = mockk<LogPort>(relaxed = true).also {
        every { it.debug(any()) } answers { capturedLogs.add("[DEBUG] ${args[0]}") }
        every { it.info(any()) } answers { capturedLogs.add("[INFO] ${args[0]}") }
        every { it.warn(any()) } answers { capturedLogs.add("[WARN] ${args[0]}") }
        every { it.error(any()) } answers { capturedLogs.add("[ERROR] ${args[0]}") }
    }

    private val filesystem = object : FilesystemPort {
        override fun exists(path: Path): Boolean = Files.exists(path)
        override fun isDirectory(path: Path): Boolean = Files.isDirectory(path)
        override fun isRegularFile(path: Path): Boolean = Files.isRegularFile(path)
        override fun walk(path: Path): Stream<Path> = Files.walk(path)
        override fun readAllLines(path: Path): List<String> = Files.readAllLines(path)
        override fun readString(path: Path): String = Files.readString(path)
    }

    private val parser: ParserPort = mockk<ParserPort>(relaxed = true).also {
        val yamlParser = YamlParserAdapter()
        every { it.parse(any()) } answers { yamlParser.parse(args[0] as String) }
    }

    private fun createUseCase(): AnalyzeProjectUseCase {
        val kotlinParser = com.cquilez.arch.infrastructure.adapter.KotlinPsiSourceParser()
        return AnalyzeProjectUseCase(
            log = log,
            filesystem = filesystem,
            parser = parser,
            ruleValidatorService = RuleValidatorService(filesystem),
            ruleEvaluatorService = RuleEvaluatorService(
                log,
                filesystem,
                com.cquilez.arch.application.service.LayerFinderService(),
                com.cquilez.arch.application.service.PatternMatcherService(),
                RuleValidationExecutor(
                    com.cquilez.arch.application.service.PatternMatcherService(),
                    com.cquilez.arch.application.service.LayerFinderService()
                ),
                com.cquilez.arch.application.service.SourceParserService(log, filesystem, kotlinParser)
            )
        )
    }

    @ParameterizedTest
    @EnumSource(SampleProject::class)
    fun `execute validates sample project with no violations`(project: SampleProject) {
        val resourcesDir = Path.of("src/test/resources").resolve(project.path)
        val rulesFile = resourcesDir.resolve("arch-rules.yml")
        val projectDir = resourcesDir.resolve("src/main/java")
        val projectInfo = Project(
            compileSourceRoots = listOf(projectDir.toString()),
            testCompileSourceRoots = emptyList()
        )

        capturedLogs.clear()
        val useCase = createUseCase()
        useCase.execute(projectInfo, rulesFile, AnalysisConfig())

        assertTrue(capturedLogs.isNotEmpty(), "Expected logs to be captured")

        assertTrue(capturedLogs.any { it.contains("[INFO] Layers:") && it.contains("3") },
            "Expected to log layer count")
        assertTrue(capturedLogs.any { it.contains("[INFO] Types:") && it.contains(project.expectedTypes.toString()) },
            "Expected to log type count: ${project.expectedTypes}")
        assertTrue(capturedLogs.any { it.contains("[INFO] Rules:") && it.contains("3") },
            "Expected to log rule count")
        assertTrue(capturedLogs.any { it.contains("[INFO] Evaluating rules...") },
            "Expected to log rule evaluation start")
        assertTrue(capturedLogs.any { it.contains("[INFO] Rules evaluation finished.") },
            "Expected to log rule evaluation finished")
        assertTrue(capturedLogs.any { it.contains("[INFO] === Project analysis ===") },
            "Expected project analysis header")
        assertTrue(capturedLogs.any { it.contains("[INFO] No violations detected") },
            "Expected no violations message")
        assertTrue(capturedLogs.any { it.contains("[INFO] Project analysis successful!") },
            "Expected success message")
        assertTrue(capturedLogs.none { it.contains("[ERROR]") },
            "Expected no error logs")

        val violationsLog = capturedLogs.filter { it.contains("Total VIOLATIONS:") }
        assertTrue(violationsLog.isEmpty() || violationsLog.any { it.contains("Total VIOLATIONS: 0") },
            "Expected violations count to be 0")
    }

    @ParameterizedTest
    @EnumSource(SampleProject::class)
    fun `execute logs discovered classes and types`(project: SampleProject) {
        val resourcesDir = Path.of("src/test/resources").resolve(project.path)
        val rulesFile = resourcesDir.resolve("arch-rules.yml")
        val projectDir = resourcesDir.resolve("src/main/java")
        val projectInfo = Project(
            compileSourceRoots = listOf(projectDir.toString()),
            testCompileSourceRoots = emptyList()
        )

        capturedLogs.clear()
        val useCase = createUseCase()
        useCase.execute(projectInfo, rulesFile, AnalysisConfig())

        assertTrue(capturedLogs.any { it.contains("Total classes:") },
            "Expected to log total classes count")
        assertTrue(capturedLogs.any { it.contains("=== Types summary ===") },
            "Expected types summary section")

        if (project.expectedTypeCounts.isNotEmpty()) {
            project.expectedTypeCounts.forEach { typeName ->
                assertTrue(capturedLogs.any { it.contains("$typeName:") },
                    "Expected $typeName type count")
            }
        } else {
            assertTrue(capturedLogs.any { it.contains("No types defined") },
                "Expected no types defined message")
        }
    }

    @Test
    fun `execute logs warning for unknown top-level keys`(@TempDir tempDir: Path) {
        val rulesContent = """
            |unknownTopKey: value
            |layers:
            |  domain:
            |    location: com.example.domain
            |rules:
            |  - title: "Test rule"
            |    layers: [domain]
            |    allowed:
            |      imports:
            |        - java.*
            |""".trimMargin()

        val rulesFile = tempDir.resolve("arch-rules.yml")
        Files.writeString(rulesFile, rulesContent)

        val projectDir = tempDir.resolve("src/main/java")
        Files.createDirectories(projectDir.resolve("com/example/domain"))
        val projectInfo = Project(
            compileSourceRoots = listOf(projectDir.toString()),
            testCompileSourceRoots = emptyList()
        )

        capturedLogs.clear()
        val useCase = createUseCase()
        useCase.execute(projectInfo, rulesFile, AnalysisConfig())

        assertEquals(1, capturedLogs.count { it.contains("[WARN] Unrecognized key in rules file: 'unknownTopKey'") },
            "Expected warning for unknown top-level key")
    }

    @Test
    fun `execute logs warning for unknown layer keys`(@TempDir tempDir: Path) {
        val rulesContent = """
            |layers:
            |  domain:
            |    location: com.example.domain
            |    unknownKey: value
            |rules:
            |  - title: "Test rule"
            |    layers: [domain]
            |    allowed:
            |      imports:
            |        - java.*
            |""".trimMargin()

        val rulesFile = tempDir.resolve("arch-rules.yml")
        Files.writeString(rulesFile, rulesContent)

        val projectDir = tempDir.resolve("src/main/java")
        Files.createDirectories(projectDir.resolve("com/example/domain"))
        val projectInfo = Project(
            compileSourceRoots = listOf(projectDir.toString()),
            testCompileSourceRoots = emptyList()
        )

        capturedLogs.clear()
        val useCase = createUseCase()
        useCase.execute(projectInfo, rulesFile, AnalysisConfig())

        assertEquals(1, capturedLogs.count { it.contains("[WARN] Unrecognized key in layer 'domain': 'unknownKey'") },
            "Expected warning for unknown layer key")
    }

    @Test
    fun `execute logs warning for unknown type keys`(@TempDir tempDir: Path) {
        val rulesContent = """
            |layers:
            |  domain:
            |    location: com.example.domain
            |types:
            |  - name: Adapter
            |    patterns: ["*Adapter"]
            |    unknownTypeKey: value
            |rules: []
            |""".trimMargin()

        val rulesFile = tempDir.resolve("arch-rules.yml")
        Files.writeString(rulesFile, rulesContent)

        val projectDir = tempDir.resolve("src/main/java")
        Files.createDirectories(projectDir.resolve("com/example/domain"))
        val projectInfo = Project(
            compileSourceRoots = listOf(projectDir.toString()),
            testCompileSourceRoots = emptyList()
        )

        capturedLogs.clear()
        val useCase = createUseCase()
        useCase.execute(projectInfo, rulesFile, AnalysisConfig())

        assertEquals(1, capturedLogs.count { it.contains("[WARN] Unrecognized key in types[0]: 'unknownTypeKey'") },
            "Expected warning for unknown type key")
    }

    @Test
    fun `execute logs warning for unknown rule keys`(@TempDir tempDir: Path) {
        val rulesContent = """
            |layers:
            |  domain:
            |    location: com.example.domain
            |rules:
            |  - title: "Test rule"
            |    layers: [domain]
            |    unknownRuleKey: value
            |    allowed:
            |      imports:
            |        - java.*
            |""".trimMargin()

        val rulesFile = tempDir.resolve("arch-rules.yml")
        Files.writeString(rulesFile, rulesContent)

        val projectDir = tempDir.resolve("src/main/java")
        Files.createDirectories(projectDir.resolve("com/example/domain"))
        val projectInfo = Project(
            compileSourceRoots = listOf(projectDir.toString()),
            testCompileSourceRoots = emptyList()
        )

        capturedLogs.clear()
        val useCase = createUseCase()
        useCase.execute(projectInfo, rulesFile, AnalysisConfig())

        assertEquals(1, capturedLogs.count { it.contains("[WARN] Unrecognized key in rules[0]: 'unknownRuleKey'") },
            "Expected warning for unknown rule key")
    }

    @Test
    fun `execute logs multiple warnings for multiple unknown keys`(@TempDir tempDir: Path) {
        val rulesContent = """
            |unknownTop: value
            |layers:
            |  domain:
            |    location: com.example.domain
            |    unknownLayer: value
            |types:
            |  - name: Adapter
            |    patterns: ["*Adapter"]
            |    unknownType: value
            |rules:
            |  - title: "Test rule"
            |    layers: [domain]
            |    unknownRule: value
            |    allowed:
            |      imports:
            |        - java.*
            |""".trimMargin()

        val rulesFile = tempDir.resolve("arch-rules.yml")
        Files.writeString(rulesFile, rulesContent)

        val projectDir = tempDir.resolve("src/main/java")
        Files.createDirectories(projectDir.resolve("com/example/domain"))
        val projectInfo = Project(
            compileSourceRoots = listOf(projectDir.toString()),
            testCompileSourceRoots = emptyList()
        )

        capturedLogs.clear()
        val useCase = createUseCase()
        useCase.execute(projectInfo, rulesFile, AnalysisConfig())

        assertEquals(1, capturedLogs.count { it.contains("[WARN] Unrecognized key in rules file: 'unknownTop'") },
            "Expected warning for unknown top-level key")
        assertEquals(1, capturedLogs.count { it.contains("[WARN] Unrecognized key in layer 'domain': 'unknownLayer'") },
            "Expected warning for unknown layer key")
        assertEquals(1, capturedLogs.count { it.contains("[WARN] Unrecognized key in types[0]: 'unknownType'") },
            "Expected warning for unknown type key")
        assertEquals(1, capturedLogs.count { it.contains("[WARN] Unrecognized key in rules[0]: 'unknownRule'") },
            "Expected warning for unknown rule key")
    }

    @Test
    fun `execute does not warn for known keys`(@TempDir tempDir: Path) {
        val rulesContent = """
            |layers:
            |  domain:
            |    location: com.example.domain
            |types:
            |  - name: Adapter
            |    patterns: ["*Adapter"]
            |    layer: domain
            |rules:
            |  - title: "Test rule"
            |    layers: [domain]
            |    classes: ["*Service"]
            |    allowed:
            |      imports:
            |        - java.*
            |""".trimMargin()

        val rulesFile = tempDir.resolve("arch-rules.yml")
        Files.writeString(rulesFile, rulesContent)

        val projectDir = tempDir.resolve("src/main/java")
        Files.createDirectories(projectDir.resolve("com/example/domain"))
        val projectInfo = Project(
            compileSourceRoots = listOf(projectDir.toString()),
            testCompileSourceRoots = emptyList()
        )

        capturedLogs.clear()
        val useCase = createUseCase()
        useCase.execute(projectInfo, rulesFile, AnalysisConfig())

        assertEquals(0, capturedLogs.count { it.contains("Unrecognized key") },
            "Expected no warnings for known keys")
    }

    @Test
    fun `execute detects violations when layer rules are broken`() {
        val resourcesDir = Path.of("src/test/resources").resolve("violation-project")
        val rulesFile = resourcesDir.resolve("arch-rules.yml")
        val projectDir = resourcesDir.resolve("src/main/java")
        val projectInfo = Project(
            compileSourceRoots = listOf(projectDir.toString()),
            testCompileSourceRoots = emptyList()
        )

        capturedLogs.clear()
        val useCase = createUseCase()

        try {
            useCase.execute(projectInfo, rulesFile, AnalysisConfig())
        } catch (e: Exception) {
            // Expected if violations cause failure
        }

        // Check that violations were detected
        val violationsLog = capturedLogs.filter { it.contains("Total VIOLATIONS:") }
        assertTrue(violationsLog.isNotEmpty(), "Expected violations to be detected")
        assertTrue(capturedLogs.any { it.contains("Rules evaluation failed") },
            "Expected 'Rules evaluation failed' message")

        // Verify that specific violation is detected
        assertTrue(capturedLogs.any { it.contains("domain") && it.contains("Order") },
            "Expected violation for Order class in domain layer")
    }

    @Test
    fun `execute handles malformed YAML`(@TempDir tempDir: Path) {
        val rulesContent = """
            |layers:
            |  domain:
            |    location: com.example.domain
            |  invalid yaml here: {
            |    nested: [
            |rules:
            |  - title: "Test rule"
            |    layers: [domain]
            |    allowed:
            |      imports:
            |        - java.*
            |""".trimMargin()

        val rulesFile = tempDir.resolve("arch-rules.yml")
        Files.writeString(rulesFile, rulesContent)

        val projectDir = tempDir.resolve("src/main/java")
        Files.createDirectories(projectDir.resolve("com/example/domain"))
        val projectInfo = Project(
            compileSourceRoots = listOf(projectDir.toString()),
            testCompileSourceRoots = emptyList()
        )

        capturedLogs.clear()
        val useCase = createUseCase()

        try {
            useCase.execute(projectInfo, rulesFile, AnalysisConfig())
        } catch (e: IllegalStateException) {
            // Expected - malformed YAML should cause exception
        }

        // Should not have successfully analyzed
        assertTrue(capturedLogs.none { it.contains("Project analysis successful!") },
            "Should not succeed with malformed YAML")
    }

    @Test
    fun `execute fails when failIfNoRules is true and no rules file`(@TempDir tempDir: Path) {
        val projectDir = tempDir.resolve("src/main/java")
        Files.createDirectories(projectDir.resolve("com/example/domain"))

        // Don't create arch-rules.yml file
        val rulesFile = tempDir.resolve("arch-rules.yml")
        val projectInfo = Project(
            compileSourceRoots = listOf(projectDir.toString()),
            testCompileSourceRoots = emptyList()
        )

        capturedLogs.clear()
        val useCase = createUseCase()

        var exceptionThrown = false
        try {
            useCase.execute(projectInfo, rulesFile, AnalysisConfig(failIfNoRules = true))
        } catch (e: IllegalStateException) {
            exceptionThrown = true
            assertTrue(e.message?.contains("No rules found") == true,
                "Expected 'No rules found' message, got: ${e.message}")
        }

        assertTrue(exceptionThrown, "Expected IllegalStateException when failIfNoRules=true and no rules file")
    }

    @Test
    fun `execute handles duplicate rule titles`(@TempDir tempDir: Path) {
        val rulesContent = """
            |layers:
            |  domain:
            |    location: com.example.domain
            |rules:
            |  - title: "Duplicate Rule"
            |    layers: [domain]
            |    allowed:
            |      imports:
            |        - java.*
            |  - title: "Duplicate Rule"
            |    layers: [domain]
            |    allowed:
            |      imports:
            |        - java.*
            |""".trimMargin()

        val rulesFile = tempDir.resolve("arch-rules.yml")
        Files.writeString(rulesFile, rulesContent)

        val projectDir = tempDir.resolve("src/main/java")
        Files.createDirectories(projectDir.resolve("com/example/domain"))
        val projectInfo = Project(
            compileSourceRoots = listOf(projectDir.toString()),
            testCompileSourceRoots = emptyList()
        )

        capturedLogs.clear()
        val useCase = createUseCase()

        var exceptionThrown = false
        try {
            useCase.execute(projectInfo, rulesFile, AnalysisConfig())
        } catch (e: IllegalStateException) {
            exceptionThrown = true
            assertTrue(e.message?.contains("Duplicate rule titles") == true,
                "Expected 'Duplicate rule titles' message, got: ${e.message}")
        }

        assertTrue(exceptionThrown, "Expected IllegalStateException for duplicate rule titles")
    }
}
