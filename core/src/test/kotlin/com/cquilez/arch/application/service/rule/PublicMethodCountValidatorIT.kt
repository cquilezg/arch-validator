package com.cquilez.arch.application.service.rule

import com.cquilez.arch.application.port.FilesystemPort
import com.cquilez.arch.application.port.LogPort
import com.cquilez.arch.application.service.LayerFinderService
import com.cquilez.arch.application.service.PatternMatcherService
import com.cquilez.arch.application.service.SourceParserService
import com.cquilez.arch.infrastructure.adapter.KotlinPsiSourceParser
import com.cquilez.arch.application.service.rule.RuleValidationExecutor
import com.cquilez.arch.domain.Allowed
import com.cquilez.arch.domain.MethodRestriction
import com.cquilez.arch.domain.Rule
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Stream

class PublicMethodCountValidatorIT {
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
    fun `detects too many public methods`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("MyService.java")
        Files.writeString(file, """
            package com.example;
            public class MyService {
                public void method1() {}
                public void method2() {}
                public void method3() {}
            }
        """.trimIndent())

        val rule = Rule(
            title = "UseCase should have only 1 public method",
            layers = emptyList(),
            types = emptyList(),
            classPatterns = null,
            allowed = Allowed(imports = null, allowedLayers = null, methods = MethodRestriction(publicCount = 1)),
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
        assertTrue(violations[0].cause.contains("Public method count mismatch"))
    }

    @Test
    fun `detects too few public methods`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("MyService.java")
        Files.writeString(file, """
            package com.example;
            public class MyService {
                public void method1() {}
                private void helper() {}
            }
        """.trimIndent())

        val rule = Rule(
            title = "UseCase should have exactly 2 public methods",
            layers = emptyList(),
            types = emptyList(),
            classPatterns = null,
            allowed = Allowed(imports = null, allowedLayers = null, methods = MethodRestriction(publicCount = 2)),
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
    }

    @Test
    fun `no violation when public method count matches`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("MyService.java")
        Files.writeString(file, """
            package com.example;
            public class MyService {
                public void execute() {}
                private void helper() {}
            }
        """.trimIndent())

        val rule = Rule(
            title = "UseCase should have 1 public method",
            layers = emptyList(),
            types = emptyList(),
            classPatterns = null,
            allowed = Allowed(imports = null, allowedLayers = null, methods = MethodRestriction(publicCount = 1)),
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
    fun `returns empty when methods restriction is null`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("MyService.java")
        Files.writeString(file, """
            package com.example;
            public class MyService {
                public void method1() {}
                public void method2() {}
            }
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
            com.cquilez.arch.domain.ProjectRules(emptyList(), emptyList(), emptyList()),
            rule,
            parsed,
            file
        )

        assertTrue(violations.isEmpty())
    }

    @Test
    fun `returns empty when publicCount is null`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("MyService.java")
        Files.writeString(file, """
            package com.example;
            public class MyService {
                public void method1() {}
            }
        """.trimIndent())

        val rule = Rule(
            title = "No public count restriction",
            layers = emptyList(),
            types = emptyList(),
            classPatterns = null,
            allowed = Allowed(imports = null, allowedLayers = null, methods = MethodRestriction(null)),
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
    fun `filters by classPatterns when specified`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("Services.java")
        Files.writeString(file, """
            package com.example;
            public class UseCase1 {
                public void execute() {}
            }
            public class UseCase2 {
                public void run() {}
            }
            public class Helper {
                public void doStuff() {}
                public void doMore() {}
            }
        """.trimIndent())

        val rule = Rule(
            title = "UseCases should have 1 public method",
            layers = emptyList(),
            types = emptyList(),
            classPatterns = listOf("UseCase*"),
            allowed = Allowed(imports = null, allowedLayers = null, methods = MethodRestriction(publicCount = 1)),
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
    fun `reports violation for filtered classes only`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("Services.java")
        Files.writeString(file, """
            package com.example;
            public class UseCase1 {
                public void execute() {}
            }
            public class UseCase2 {
                public void run() {}
                public void help() {}
            }
            public class Helper {
                public void doStuff() {}
                public void doMore() {}
            }
        """.trimIndent())

        val rule = Rule(
            title = "UseCases should have 1 public method",
            layers = emptyList(),
            types = emptyList(),
            classPatterns = listOf("UseCase*"),
            allowed = Allowed(imports = null, allowedLayers = null, methods = MethodRestriction(publicCount = 1)),
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
        assertTrue(violations[0].className!!.contains("UseCase2"))
    }

    @Test
    fun `handles class with zero public methods`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("MyService.java")
        Files.writeString(file, """
            package com.example;
            public class MyService {
                private void method1() {}
                private void method2() {}
            }
        """.trimIndent())

        val rule = Rule(
            title = "Service should have 1 public method",
            layers = emptyList(),
            types = emptyList(),
            classPatterns = null,
            allowed = Allowed(imports = null, allowedLayers = null, methods = MethodRestriction(publicCount = 1)),
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
    }

    @Test
    fun `reports violation with correct class name`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("MyService.java")
        Files.writeString(file, """
            package com.example.domain;
            public class MyService {
                public void method1() {}
                public void method2() {}
            }
        """.trimIndent())

        val rule = Rule(
            title = "UseCase should have 1 public method",
            layers = emptyList(),
            types = emptyList(),
            classPatterns = null,
            allowed = Allowed(imports = null, allowedLayers = null, methods = MethodRestriction(publicCount = 1)),
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
        assertTrue(violations[0].className == "com.example.domain.MyService")
    }

    @Test
    fun `handles class without package`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("NoPackage.java")
        Files.writeString(file, """
            public class NoPackage {
                public void method1() {}
                public void method2() {}
            }
        """.trimIndent())

        val rule = Rule(
            title = "UseCase should have 1 public method",
            layers = emptyList(),
            types = emptyList(),
            classPatterns = null,
            allowed = Allowed(imports = null, allowedLayers = null, methods = MethodRestriction(publicCount = 1)),
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
        assertTrue(violations[0].className == "NoPackage")
    }

    @Test
    fun `no violation when no classes match classPatterns`() {
        val tempDir = Files.createTempDirectory("arch-test")
        val file = tempDir.resolve("Other.java")
        Files.writeString(file, """
            package com.example;
            public class Other {
                public void method1() {}
                public void method2() {}
            }
        """.trimIndent())

        val rule = Rule(
            title = "UseCases should have 1 public method",
            layers = emptyList(),
            types = emptyList(),
            classPatterns = listOf("UseCase*"),
            allowed = Allowed(imports = null, allowedLayers = null, methods = MethodRestriction(publicCount = 1)),
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
