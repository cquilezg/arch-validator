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

class ImportPatternValidatorIT {
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
    fun `detects import outside allowed patterns`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("MyService.java")
        Files.writeString(file, """
            package com.example.domain;
            import com.external.BadImport;
            public class MyService {}
        """.trimIndent())

        val rules = ProjectRules(
            layers = listOf(Layer("domain", "com.example.domain")),
            types = emptyList(),
            rules = emptyList()
        )
        val rule = Rule(
            title = "Domain imports",
            layers = emptyList(),
            types = emptyList(),
            classPatterns = null,
            allowed = Allowed(imports = listOf("java.*", "javax.*"), allowedLayers = null, methods = null),
            forbidden = null
        )
        val parsed = sourceParser.parseSource(file)!!

        val violations = executor.validate(rules, rule, parsed, file)

        assertEquals(1, violations.size)
        assertTrue(violations[0].cause.contains("Import outside allowed patterns"))
    }

    @Test
    fun `allows import matching allowed pattern`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("MyService.java")
        Files.writeString(file, """
            package com.example.domain;
            import java.util.List;
            public class MyService {}
        """.trimIndent())

        val rules = ProjectRules(
            layers = listOf(Layer("domain", "com.example.domain")),
            types = emptyList(),
            rules = emptyList()
        )
        val rule = Rule(
            title = "Domain imports",
            layers = emptyList(),
            types = emptyList(),
            classPatterns = null,
            allowed = Allowed(imports = listOf("java.*", "javax.*"), allowedLayers = null, methods = null),
            forbidden = null
        )
        val parsed = sourceParser.parseSource(file)!!

        val violations = executor.validate(rules, rule, parsed, file)

        assertTrue(violations.isEmpty())
    }

    @Test
    fun `allows same-layer import regardless of pattern`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("MyService.java")
        Files.writeString(file, """
            package com.example.domain;
            import com.example.domain.OtherDomainClass;
            public class MyService {}
        """.trimIndent())

        val rules = ProjectRules(
            layers = listOf(Layer("domain", "com.example.domain")),
            types = emptyList(),
            rules = emptyList()
        )
        val rule = Rule(
            title = "Domain imports",
            layers = emptyList(),
            types = emptyList(),
            classPatterns = null,
            allowed = Allowed(imports = listOf("java.*"), allowedLayers = null, methods = null),
            forbidden = null
        )
        val parsed = sourceParser.parseSource(file)!!

        val violations = executor.validate(rules, rule, parsed, file)

        assertTrue(violations.isEmpty())
    }

    @Test
    fun `returns empty when allowed imports is null`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("MyService.java")
        Files.writeString(file, """
            package com.example.domain;
            import com.external.Anything;
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
    fun `returns empty when allowed imports is empty list`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("MyService.java")
        Files.writeString(file, """
            package com.example.domain;
            import com.external.Anything;
            public class MyService {}
        """.trimIndent())

        val rule = Rule(
            title = "No allowed imports",
            layers = emptyList(),
            types = emptyList(),
            classPatterns = null,
            allowed = Allowed(imports = emptyList(), allowedLayers = null, methods = null),
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
    fun `allows asterisk imports matching pattern`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("MyService.java")
        Files.writeString(file, """
            package com.example.domain;
            import com.google.common.collect.*;
            public class MyService {}
        """.trimIndent())

        val rules = ProjectRules(
            layers = listOf(Layer("domain", "com.example.domain")),
            types = emptyList(),
            rules = emptyList()
        )
        val rule = Rule(
            title = "Domain imports",
            layers = emptyList(),
            types = emptyList(),
            classPatterns = null,
            allowed = Allowed(imports = listOf("com.google.*"), allowedLayers = null, methods = null),
            forbidden = null
        )
        val parsed = sourceParser.parseSource(file)!!

        val violations = executor.validate(rules, rule, parsed, file)

        assertTrue(violations.isEmpty())
    }

    @Test
    fun `reports violation for class without package declaration`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("NoPackage.java")
        Files.writeString(file, """
            import java.util.List;
            public class NoPackage {}
        """.trimIndent())

        val rules = ProjectRules(
            layers = listOf(Layer("domain", "com.example.domain")),
            types = emptyList(),
            rules = emptyList()
        )
        val rule = Rule(
            title = "Restricted imports",
            layers = emptyList(),
            types = emptyList(),
            classPatterns = null,
            allowed = Allowed(imports = listOf("javax.*"), allowedLayers = null, methods = null),
            forbidden = null
        )
        val parsed = sourceParser.parseSource(file)!!

        val violations = executor.validate(rules, rule, parsed, file)

        assertEquals(1, violations.size)
    }

    @Test
    fun `reports violation with correct line number`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("MyService.java")
        val content = """
            package com.example.domain;
            import com.external.BadImport;
            public class MyService {}
        """.trimIndent()
        Files.writeString(file, content)

        val rules = ProjectRules(
            layers = listOf(Layer("domain", "com.example.domain")),
            types = emptyList(),
            rules = emptyList()
        )
        val rule = Rule(
            title = "Domain imports",
            layers = emptyList(),
            types = emptyList(),
            classPatterns = null,
            allowed = Allowed(imports = listOf("java.*"), allowedLayers = null, methods = null),
            forbidden = null
        )
        val parsed = sourceParser.parseSource(file)!!

        val violations = executor.validate(rules, rule, parsed, file)

        assertEquals(1, violations.size)
        assertEquals(2, violations[0].line)
    }

    @Test
    fun `multiple violations for multiple disallowed imports`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("MyService.java")
        Files.writeString(file, """
            package com.example.domain;
            import com.external.Bad1;
            import com.external.Bad2;
            import com.external.Bad3;
            public class MyService {}
        """.trimIndent())

        val rules = ProjectRules(
            layers = listOf(Layer("domain", "com.example.domain")),
            types = emptyList(),
            rules = emptyList()
        )
        val rule = Rule(
            title = "Domain imports",
            layers = emptyList(),
            types = emptyList(),
            classPatterns = null,
            allowed = Allowed(imports = listOf("java.*"), allowedLayers = null, methods = null),
            forbidden = null
        )
        val parsed = sourceParser.parseSource(file)!!

        val violations = executor.validate(rules, rule, parsed, file)

        assertEquals(3, violations.size)
    }

    @Test
    fun `layers sorted by location length for longest match`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("MyService.java")
        Files.writeString(file, """
            package com.example.domain.sub;
            import com.example.domain.sub.SubClass;
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
            title = "Domain imports",
            layers = emptyList(),
            types = emptyList(),
            classPatterns = null,
            allowed = Allowed(imports = listOf("java.*"), allowedLayers = null, methods = null),
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
            layers = listOf(Layer("domain", "com.example.domain")),
            types = emptyList(),
            rules = emptyList()
        )
        val rule = Rule(
            title = "Domain imports",
            layers = emptyList(),
            types = emptyList(),
            classPatterns = null,
            allowed = Allowed(imports = listOf("java.*"), allowedLayers = null, methods = null),
            forbidden = null
        )
        val parsed = sourceParser.parseSource(file)!!

        val violations = executor.validate(rules, rule, parsed, file)

        assertEquals(2, violations.size)
    }
}
