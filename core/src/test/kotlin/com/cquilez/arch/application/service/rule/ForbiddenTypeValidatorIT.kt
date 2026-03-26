package com.cquilez.arch.application.service.rule

import com.cquilez.arch.application.port.FilesystemPort
import com.cquilez.arch.application.port.LogPort
import com.cquilez.arch.application.service.LayerFinderService
import com.cquilez.arch.application.service.PatternMatcherService
import com.cquilez.arch.application.service.SourceParserService
import com.cquilez.arch.domain.Forbidden
import com.cquilez.arch.domain.Layer
import com.cquilez.arch.domain.ProjectRules
import com.cquilez.arch.domain.Rule
import com.cquilez.arch.domain.TypeDefinition
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Stream

class ForbiddenTypeValidatorIT {
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
    private val sourceParser = SourceParserService(log, filesystem)
    private val executor = RuleValidationExecutor(patternMatcher, layerFinder)

    @Test
    fun `detects import of forbidden type when imported from the restricted layer`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("MyService.java")
        Files.writeString(file, """
            package com.example.domain;
            import com.example.domain.SomeEntity;
            public class MyService {}
        """.trimIndent())

        val types = listOf(TypeDefinition("Entity", listOf("*Entity"), "domain"))
        val rules = ProjectRules(
            layers = listOf(Layer("domain", "com.example.domain")),
            types = types,
            rules = emptyList()
        )
        val rule = Rule(
            title = "No entities",
            layers = emptyList(),
            types = emptyList(),
            classPatterns = null,
            allowed = null,
            forbidden = Forbidden(classPatterns = emptyList(), layers = null, types = listOf("Entity"))
        )
        val parsed = sourceParser.parseSource(file)!!

        val violations = executor.validate(rules, rule, parsed, file)

        assertEquals(1, violations.size)
        assertTrue(violations[0].cause.contains("Import from forbidden type"))
    }

    @Test
    fun `no violation when import from external package and type has layer restriction`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("MyService.java")
        Files.writeString(file, """
            package com.example.domain;
            import com.external.SomeEntity;
            public class MyService {}
        """.trimIndent())

        val types = listOf(TypeDefinition("Entity", listOf("*Entity"), "domain"))
        val rules = ProjectRules(
            layers = listOf(Layer("domain", "com.example.domain")),
            types = types,
            rules = emptyList()
        )
        val rule = Rule(
            title = "No entities",
            layers = emptyList(),
            types = emptyList(),
            classPatterns = null,
            allowed = null,
            forbidden = Forbidden(classPatterns = emptyList(), layers = null, types = listOf("Entity"))
        )
        val parsed = sourceParser.parseSource(file)!!

        val violations = executor.validate(rules, rule, parsed, file)

        assertTrue(violations.isEmpty())
    }

    @Test
    fun `detects usage of forbidden type via type reference`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("MyService.java")
        Files.writeString(file, """
            package com.example.domain;
            import com.example.domain.Entity;
            public class MyService {
                private Entity entity;
            }
        """.trimIndent())

        val types = listOf(TypeDefinition("Entity", listOf("*Entity"), "domain"))
        val rules = ProjectRules(
            layers = listOf(Layer("domain", "com.example.domain")),
            types = types,
            rules = emptyList()
        )
        val rule = Rule(
            title = "No entities",
            layers = emptyList(),
            types = emptyList(),
            classPatterns = null,
            allowed = null,
            forbidden = Forbidden(classPatterns = emptyList(), layers = null, types = listOf("Entity"))
        )
        val parsed = sourceParser.parseSource(file)!!

        val violations = executor.validate(rules, rule, parsed, file)

        assertEquals(2, violations.size)
    }

    @Test
    fun `no violation when type not matching pattern`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("MyService.java")
        Files.writeString(file, """
            package com.example.domain;
            import com.external.GoodModel;
            public class MyService {}
        """.trimIndent())

        val types = listOf(TypeDefinition("Entity", listOf("*Entity"), "domain"))
        val rules = ProjectRules(
            layers = listOf(Layer("domain", "com.example.domain")),
            types = types,
            rules = emptyList()
        )
        val rule = Rule(
            title = "No entities",
            layers = emptyList(),
            types = emptyList(),
            classPatterns = null,
            allowed = null,
            forbidden = Forbidden(classPatterns = emptyList(), layers = null, types = listOf("Entity"))
        )
        val parsed = sourceParser.parseSource(file)!!

        val violations = executor.validate(rules, rule, parsed, file)

        assertTrue(violations.isEmpty())
    }

    @Test
    fun `no violation when forbidden types list is empty`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("MyService.java")
        Files.writeString(file, """
            package com.example.domain;
            import com.external.SomeEntity;
            public class MyService {}
        """.trimIndent())

        val rule = Rule(
            title = "No restriction",
            layers = emptyList(),
            types = emptyList(),
            classPatterns = null,
            allowed = null,
            forbidden = Forbidden(classPatterns = emptyList(), layers = null, types = emptyList())
        )
        val parsed = sourceParser.parseSource(file)!!

        val violations = executor.validate(
            ProjectRules(emptyList(), emptyList(), emptyList()),
            rule,
            parsed,
            file
        )

        assertTrue(violations.isEmpty())
    }

    @Test
    fun `returns empty when forbidden is null`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("MyService.java")
        Files.writeString(file, """
            package com.example.domain;
            import com.external.SomeEntity;
            public class MyService {}
        """.trimIndent())

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
            ProjectRules(emptyList(), emptyList(), emptyList()),
            rule,
            parsed,
            file
        )

        assertTrue(violations.isEmpty())
    }

    @Test
    fun `type without layer restriction matches regardless of import location`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("MyService.java")
        Files.writeString(file, """
            package com.example.domain;
            import com.external.SomeModel;
            public class MyService {}
        """.trimIndent())

        val types = listOf(TypeDefinition("Model", listOf("*Model"), null))
        val rules = ProjectRules(
            layers = listOf(Layer("domain", "com.example.domain")),
            types = types,
            rules = emptyList()
        )
        val rule = Rule(
            title = "No models",
            layers = emptyList(),
            types = emptyList(),
            classPatterns = null,
            allowed = null,
            forbidden = Forbidden(classPatterns = emptyList(), layers = null, types = listOf("Model"))
        )
        val parsed = sourceParser.parseSource(file)!!

        val violations = executor.validate(rules, rule, parsed, file)

        assertEquals(1, violations.size)
    }

    @Test
    fun `type without layer restriction matches any import`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("MyService.java")
        Files.writeString(file, """
            package com.example.domain;
            import com.external.SomeModel;
            public class MyService {}
        """.trimIndent())

        val types = listOf(TypeDefinition("Model", listOf("*Model"), null))
        val rules = ProjectRules(
            layers = listOf(Layer("domain", "com.example.domain")),
            types = types,
            rules = emptyList()
        )
        val rule = Rule(
            title = "No models",
            layers = emptyList(),
            types = emptyList(),
            classPatterns = null,
            allowed = null,
            forbidden = Forbidden(classPatterns = emptyList(), layers = null, types = listOf("Model"))
        )
        val parsed = sourceParser.parseSource(file)!!

        val violations = executor.validate(rules, rule, parsed, file)

        assertEquals(1, violations.size)
    }

    @Test
    fun `type reference violation when imported from restricted layer`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("MyService.java")
        Files.writeString(file, """
            package com.example.application;
            import com.example.infrastructure.InfrastructureModel;
            public class MyService {
                private InfrastructureModel model;
            }
        """.trimIndent())

        val types = listOf(TypeDefinition("Model", listOf("*Model"), "infrastructure"))
        val rules = ProjectRules(
            layers = listOf(
                Layer("application", "com.example.application"),
                Layer("infrastructure", "com.example.infrastructure")
            ),
            types = types,
            rules = emptyList()
        )
        val rule = Rule(
            title = "No infrastructure models",
            layers = emptyList(),
            types = emptyList(),
            classPatterns = null,
            allowed = null,
            forbidden = Forbidden(classPatterns = emptyList(), layers = null, types = listOf("Model"))
        )
        val parsed = sourceParser.parseSource(file)!!

        val violations = executor.validate(rules, rule, parsed, file)

        assertTrue(violations.isNotEmpty())
    }

    @Test
    fun `type reference violation when class in restricted layer package`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("MyService.java")
        Files.writeString(file, """
            package com.example.infrastructure;
            public class MyService {}
        """.trimIndent())

        val types = listOf(TypeDefinition("Model", listOf("*Model"), "infrastructure"))
        val rules = ProjectRules(
            layers = listOf(
                Layer("infrastructure", "com.example.infrastructure")
            ),
            types = types,
            rules = emptyList()
        )
        val rule = Rule(
            title = "No infrastructure models",
            layers = emptyList(),
            types = emptyList(),
            classPatterns = null,
            allowed = null,
            forbidden = Forbidden(classPatterns = emptyList(), layers = null, types = listOf("Model"))
        )
        val parsed = sourceParser.parseSource(file)!!

        val violations = executor.validate(rules, rule, parsed, file)

        assertTrue(violations.isEmpty())
    }

    @Test
    fun `multiple forbidden types in same file`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("MyService.java")
        Files.writeString(file, """
            package com.example.domain;
            import com.external.Entity;
            import com.other.Model;
            public class MyService {}
        """.trimIndent())

        val types = listOf(
            TypeDefinition("Entity", listOf("*Entity"), null),
            TypeDefinition("Model", listOf("*Model"), null)
        )
        val rules = ProjectRules(
            layers = listOf(Layer("domain", "com.example.domain")),
            types = types,
            rules = emptyList()
        )
        val rule = Rule(
            title = "No entities or models",
            layers = emptyList(),
            types = emptyList(),
            classPatterns = null,
            allowed = null,
            forbidden = Forbidden(classPatterns = emptyList(), layers = null, types = listOf("Entity", "Model"))
        )
        val parsed = sourceParser.parseSource(file)!!

        val violations = executor.validate(rules, rule, parsed, file)

        assertEquals(2, violations.size)
    }

    @Test
    fun `no violation when type not found in projectRules types`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("MyService.java")
        Files.writeString(file, """
            package com.example.domain;
            import com.external.SomeClass;
            public class MyService {}
        """.trimIndent())

        val rules = ProjectRules(
            layers = listOf(Layer("domain", "com.example.domain")),
            types = emptyList(),
            rules = emptyList()
        )
        val rule = Rule(
            title = "No unknown types",
            layers = emptyList(),
            types = emptyList(),
            classPatterns = null,
            allowed = null,
            forbidden = Forbidden(classPatterns = emptyList(), layers = null, types = listOf("NonExistent"))
        )
        val parsed = sourceParser.parseSource(file)!!

        val violations = executor.validate(rules, rule, parsed, file)

        assertTrue(violations.isEmpty())
    }
}
