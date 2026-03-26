package com.cquilez.arch.application.service.rule

import com.cquilez.arch.application.port.FilesystemPort
import com.cquilez.arch.application.port.LogPort
import com.cquilez.arch.application.service.LayerFinderService
import com.cquilez.arch.application.service.PatternMatcherService
import com.cquilez.arch.application.service.SourceParserService
import com.cquilez.arch.domain.Allowed
import com.cquilez.arch.domain.Layer
import com.cquilez.arch.domain.ProjectRules
import com.cquilez.arch.domain.Rule
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Stream

class LayerImportValidatorIT {
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
    fun `detects import from disallowed layer`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("MyService.java")
        Files.writeString(file, """
            package com.example.domain;
            import com.example.infrastructure.InfrastructureClass;
            public class MyService {}
        """.trimIndent())

        val rules = ProjectRules(
            layers = listOf(
                Layer("domain", "com.example.domain"),
                Layer("infrastructure", "com.example.infrastructure")
            ),
            types = emptyList(),
            rules = emptyList()
        )
        val rule = Rule(
            title = "Domain can only use domain",
            layers = emptyList(),
            types = emptyList(),
            classPatterns = null,
            allowed = Allowed(imports = null, allowedLayers = "[domain]", methods = null),
            forbidden = null
        )
        val parsed = sourceParser.parseSource(file)!!

        val violations = executor.validate(rules, rule, parsed, file)

        assertEquals(1, violations.size)
        assertTrue(violations[0].cause.contains("Import from disallowed layer"))
    }

    @Test
    fun `allows import from allowed layer`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("MyService.java")
        Files.writeString(file, """
            package com.example.infrastructure;
            import com.example.domain.DomainEntity;
            public class MyService {}
        """.trimIndent())

        val rules = ProjectRules(
            layers = listOf(
                Layer("domain", "com.example.domain"),
                Layer("infrastructure", "com.example.infrastructure")
            ),
            types = emptyList(),
            rules = emptyList()
        )
        val rule = Rule(
            title = "Infrastructure can use domain",
            layers = emptyList(),
            types = emptyList(),
            classPatterns = null,
            allowed = Allowed(imports = null, allowedLayers = "[domain]", methods = null),
            forbidden = null
        )
        val parsed = sourceParser.parseSource(file)!!

        val violations = executor.validate(rules, rule, parsed, file)

        assertTrue(violations.isEmpty())
    }

    @Test
    fun `allows same-layer import`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("MyService.java")
        Files.writeString(file, """
            package com.example.domain;
            import com.example.domain.OtherClass;
            public class MyService {}
        """.trimIndent())

        val rules = ProjectRules(
            layers = listOf(
                Layer("domain", "com.example.domain"),
                Layer("infrastructure", "com.example.infrastructure")
            ),
            types = emptyList(),
            rules = emptyList()
        )
        val rule = Rule(
            title = "Domain can only use domain",
            layers = emptyList(),
            types = emptyList(),
            classPatterns = null,
            allowed = Allowed(imports = null, allowedLayers = "[domain]", methods = null),
            forbidden = null
        )
        val parsed = sourceParser.parseSource(file)!!

        val violations = executor.validate(rules, rule, parsed, file)

        assertTrue(violations.isEmpty())
    }

    @Test
    fun `allows multiple allowed layers`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("MyService.java")
        Files.writeString(file, """
            package com.example.application;
            import com.example.domain.DomainClass;
            import com.example.shared.SharedUtil;
            public class MyService {}
        """.trimIndent())

        val rules = ProjectRules(
            layers = listOf(
                Layer("domain", "com.example.domain"),
                Layer("application", "com.example.application"),
                Layer("shared", "com.example.shared")
            ),
            types = emptyList(),
            rules = emptyList()
        )
        val rule = Rule(
            title = "Application can use domain and shared",
            layers = emptyList(),
            types = emptyList(),
            classPatterns = null,
            allowed = Allowed(imports = null, allowedLayers = "[domain, shared]", methods = null),
            forbidden = null
        )
        val parsed = sourceParser.parseSource(file)!!

        val violations = executor.validate(rules, rule, parsed, file)

        assertTrue(violations.isEmpty())
    }

    @Test
    fun `returns empty when allowedLayers is null`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("MyService.java")
        Files.writeString(file, """
            package com.example.domain;
            import com.example.infrastructure.Anything;
            public class MyService {}
        """.trimIndent())

        val rules = ProjectRules(
            layers = listOf(
                Layer("domain", "com.example.domain"),
                Layer("infrastructure", "com.example.infrastructure")
            ),
            types = emptyList(),
            rules = emptyList()
        )
        val rule = Rule(
            title = "No restriction",
            layers = emptyList(),
            types = emptyList(),
            classPatterns = null,
            allowed = null,
            forbidden = null
        )
        val parsed = sourceParser.parseSource(file)!!

        val violations = executor.validate(rules, rule, parsed, file)

        assertTrue(violations.isEmpty())
    }

    @Test
    fun `returns empty when allowedLayers is blank`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("MyService.java")
        Files.writeString(file, """
            package com.example.domain;
            import com.external.Anything;
            public class MyService {}
        """.trimIndent())

        val rules = ProjectRules(
            layers = listOf(Layer("domain", "com.example.domain")),
            types = emptyList(),
            rules = emptyList()
        )
        val rule = Rule(
            title = "No restriction",
            layers = emptyList(),
            types = emptyList(),
            classPatterns = null,
            allowed = Allowed(imports = null, allowedLayers = "", methods = null),
            forbidden = null
        )
        val parsed = sourceParser.parseSource(file)!!

        val violations = executor.validate(rules, rule, parsed, file)

        assertTrue(violations.isEmpty())
    }

    @Test
    fun `ignores imports from non-layered packages`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("MyService.java")
        Files.writeString(file, """
            package com.example.domain;
            import org.apache.SomeClass;
            import com.external.Utils;
            public class MyService {}
        """.trimIndent())

        val rules = ProjectRules(
            layers = listOf(
                Layer("domain", "com.example.domain"),
                Layer("infrastructure", "com.example.infrastructure")
            ),
            types = emptyList(),
            rules = emptyList()
        )
        val rule = Rule(
            title = "Domain can only use domain",
            layers = emptyList(),
            types = emptyList(),
            classPatterns = null,
            allowed = Allowed(imports = null, allowedLayers = "[domain]", methods = null),
            forbidden = null
        )
        val parsed = sourceParser.parseSource(file)!!

        val violations = executor.validate(rules, rule, parsed, file)

        assertTrue(violations.isEmpty())
    }

    @Test
    fun `handles array syntax for allowedLayers`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("MyService.java")
        Files.writeString(file, """
            package com.example.application;
            import com.example.domain.DomainClass;
            public class MyService {}
        """.trimIndent())

        val rules = ProjectRules(
            layers = listOf(
                Layer("domain", "com.example.domain"),
                Layer("application", "com.example.application")
            ),
            types = emptyList(),
            rules = emptyList()
        )
        val rule = Rule(
            title = "Application can use domain",
            layers = emptyList(),
            types = emptyList(),
            classPatterns = null,
            allowed = Allowed(imports = null, allowedLayers = "[domain]", methods = null),
            forbidden = null
        )
        val parsed = sourceParser.parseSource(file)!!

        val violations = executor.validate(rules, rule, parsed, file)

        assertTrue(violations.isEmpty())
    }

    @Test
    fun `handles space-separated layer names`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("MyService.java")
        Files.writeString(file, """
            package com.example.application;
            import com.example.infrastructure.AdapterClass;
            public class MyService {}
        """.trimIndent())

        val rules = ProjectRules(
            layers = listOf(
                Layer("infrastructure", "com.example.infrastructure"),
                Layer("application", "com.example.application")
            ),
            types = emptyList(),
            rules = emptyList()
        )
        val rule = Rule(
            title = "Application can use infrastructure",
            layers = emptyList(),
            types = emptyList(),
            classPatterns = null,
            allowed = Allowed(imports = null, allowedLayers = "[infrastructure]", methods = null),
            forbidden = null
        )
        val parsed = sourceParser.parseSource(file)!!

        val violations = executor.validate(rules, rule, parsed, file)

        assertTrue(violations.isEmpty())
    }

    @Test
    fun `multiple violations for multiple disallowed layer imports`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("MyService.java")
        Files.writeString(file, """
            package com.example.domain;
            import com.example.infrastructure.Adapter1;
            import com.example.infrastructure.Adapter2;
            import com.example.application.UseCase1;
            public class MyService {}
        """.trimIndent())

        val rules = ProjectRules(
            layers = listOf(
                Layer("domain", "com.example.domain"),
                Layer("application", "com.example.application"),
                Layer("infrastructure", "com.example.infrastructure")
            ),
            types = emptyList(),
            rules = emptyList()
        )
        val rule = Rule(
            title = "Domain can only use domain",
            layers = emptyList(),
            types = emptyList(),
            classPatterns = null,
            allowed = Allowed(imports = null, allowedLayers = "[domain]", methods = null),
            forbidden = null
        )
        val parsed = sourceParser.parseSource(file)!!

        val violations = executor.validate(rules, rule, parsed, file)

        assertEquals(3, violations.size)
    }

    @Test
    fun `class without package declaration is allowed`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("NoPackage.java")
        Files.writeString(file, """
            import com.example.domain.DomainClass;
            public class NoPackage {}
        """.trimIndent())

        val rules = ProjectRules(
            layers = listOf(
                Layer("domain", "com.example.domain"),
                Layer("infrastructure", "com.example.infrastructure")
            ),
            types = emptyList(),
            rules = emptyList()
        )
        val rule = Rule(
            title = "Domain can only use domain",
            layers = emptyList(),
            types = emptyList(),
            classPatterns = null,
            allowed = Allowed(imports = null, allowedLayers = "[domain]", methods = null),
            forbidden = null
        )
        val parsed = sourceParser.parseSource(file)!!

        val violations = executor.validate(rules, rule, parsed, file)

        assertTrue(violations.isEmpty())
    }

    @Test
    fun `longest matching layer takes precedence`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("MyService.java")
        Files.writeString(file, """
            package com.example.domain.sub;
            import com.example.domain.sub.SpecialClass;
            public class MyService {}
        """.trimIndent())

        val rules = ProjectRules(
            layers = listOf(
                Layer("domain", "com.example.domain"),
                Layer("domainSub", "com.example.domain.sub")
            ),
            types = emptyList(),
            rules = emptyList()
        )
        val rule = Rule(
            title = "Domain sub can only use domain sub",
            layers = emptyList(),
            types = emptyList(),
            classPatterns = null,
            allowed = Allowed(imports = null, allowedLayers = "[domainSub]", methods = null),
            forbidden = null
        )
        val parsed = sourceParser.parseSource(file)!!

        val violations = executor.validate(rules, rule, parsed, file)

        assertTrue(violations.isEmpty())
    }
}
