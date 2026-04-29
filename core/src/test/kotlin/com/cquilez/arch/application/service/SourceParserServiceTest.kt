package com.cquilez.arch.application.service

import com.cquilez.arch.application.port.FilesystemPort
import com.cquilez.arch.application.port.KotlinSourceParserPort
import com.cquilez.arch.application.port.LogPort
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class SourceParserServiceTest {

    // ──────────────────────────────────────────────
    // SP-02: Package Extraction (Kotlin path)
    // ──────────────────────────────────────────────

    @Test
    fun parsesPackageFromKotlinFile() {
        val parsedSource = SourceParserService.ParsedSource(
            packageName = "com.example.domain",
            classNames = listOf("Order"),
            classDeclarationLines = mapOf("Order" to 3),
            imports = emptyList(),
            publicMethodCounts = mapOf("Order" to 0),
            typeRefs = emptyList(),
            totalLines = 5
        )

        val log = object : LogPort {
            override fun debug(msg: String) {}
            override fun info(msg: String) {}
            override fun warn(msg: String) {}
            override fun error(msg: String) {}
        }

        val filesystem = object : FilesystemPort {
            override fun exists(path: Path): Boolean = true
            override fun isDirectory(path: Path): Boolean = false
            override fun isRegularFile(path: Path): Boolean = true
            override fun walk(path: Path): java.util.stream.Stream<Path> = java.util.stream.Stream.empty()
            override fun readAllLines(path: Path): List<String> = listOf("package com.example.domain", "", "class Order")
            override fun readString(path: Path): String = readAllLines(path).joinToString("\n")
        }

        val kotlinParser = object : KotlinSourceParserPort {
            override fun parse(content: String): SourceParserService.ParsedSource = parsedSource
        }

        val service = SourceParserService(log, filesystem, kotlinParser)
        val result = service.parseSource(Path.of("/src/main/java/com/example/domain/Order.kt"))

        assertEquals("com.example.domain", result?.packageName)
    }

    // ──────────────────────────────────────────────
    // SP-03: Import Extraction (Kotlin path)
    // ──────────────────────────────────────────────

    @Test
    fun parsesImportsFromKotlinFile() {
        val parsedSource = SourceParserService.ParsedSource(
            packageName = "com.example",
            classNames = listOf("Foo"),
            classDeclarationLines = mapOf("Foo" to 5),
            imports = listOf(
                SourceParserService.ImportRef("java.util.List", 3),
                SourceParserService.ImportRef("java.io.File", 4)
            ),
            publicMethodCounts = mapOf("Foo" to 0),
            typeRefs = emptyList(),
            totalLines = 6
        )

        val log = object : LogPort {
            override fun debug(msg: String) {}
            override fun info(msg: String) {}
            override fun warn(msg: String) {}
            override fun error(msg: String) {}
        }

        val filesystem = object : FilesystemPort {
            override fun exists(path: Path): Boolean = true
            override fun isDirectory(path: Path): Boolean = false
            override fun isRegularFile(path: Path): Boolean = true
            override fun walk(path: Path): java.util.stream.Stream<Path> = java.util.stream.Stream.empty()
            override fun readAllLines(path: Path): List<String> = listOf(
                "package com.example",
                "",
                "import java.util.List",
                "import java.io.File",
                "",
                "class Foo"
            )
            override fun readString(path: Path): String = readAllLines(path).joinToString("\n")
        }

        val kotlinParser = object : KotlinSourceParserPort {
            override fun parse(content: String): SourceParserService.ParsedSource = parsedSource
        }

        val service = SourceParserService(log, filesystem, kotlinParser)
        val result = service.parseSource(Path.of("/src/main/java/com/example/Foo.kt"))

        assertEquals(2, result?.imports?.size)
        assertEquals("java.util.List", result?.imports?.get(0)?.name)
        assertEquals("java.io.File", result?.imports?.get(1)?.name)
    }

    // ──────────────────────────────────────────────
    // SP-04: Class Detection (Kotlin path)
    // ──────────────────────────────────────────────

    @Test
    fun parsesClassNamesFromKotlinFile() {
        val parsedSource = SourceParserService.ParsedSource(
            packageName = "com.example",
            classNames = listOf("UserService", "Helper"),
            classDeclarationLines = mapOf("UserService" to 3, "Helper" to 4),
            imports = emptyList(),
            publicMethodCounts = mapOf("UserService" to 0, "Helper" to 0),
            typeRefs = emptyList(),
            totalLines = 5
        )

        val log = object : LogPort {
            override fun debug(msg: String) {}
            override fun info(msg: String) {}
            override fun warn(msg: String) {}
            override fun error(msg: String) {}
        }

        val filesystem = object : FilesystemPort {
            override fun exists(path: Path): Boolean = true
            override fun isDirectory(path: Path): Boolean = false
            override fun isRegularFile(path: Path): Boolean = true
            override fun walk(path: Path): java.util.stream.Stream<Path> = java.util.stream.Stream.empty()
            override fun readAllLines(path: Path): List<String> = listOf(
                "package com.example",
                "",
                "class UserService",
                "class Helper"
            )
            override fun readString(path: Path): String = readAllLines(path).joinToString("\n")
        }

        val kotlinParser = object : KotlinSourceParserPort {
            override fun parse(content: String): SourceParserService.ParsedSource = parsedSource
        }

        val service = SourceParserService(log, filesystem, kotlinParser)
        val result = service.parseSource(Path.of("/src/main/java/com/example/UserService.kt"))

        assertTrue(result?.classNames?.contains("UserService") == true)
        assertTrue(result?.classNames?.contains("Helper") == true)
    }

    // ──────────────────────────────────────────────
    // SP-05: Visibility Detection (via public method counts)
    // ──────────────────────────────────────────────

    @Test
    fun countsPublicMethodsInKotlinFile() {
        val parsedSource = SourceParserService.ParsedSource(
            packageName = "com.example",
            classNames = listOf("Service"),
            classDeclarationLines = mapOf("Service" to 3),
            imports = emptyList(),
            publicMethodCounts = mapOf("Service" to 2),
            typeRefs = emptyList(),
            totalLines = 7
        )

        val log = object : LogPort {
            override fun debug(msg: String) {}
            override fun info(msg: String) {}
            override fun warn(msg: String) {}
            override fun error(msg: String) {}
        }

        val filesystem = object : FilesystemPort {
            override fun exists(path: Path): Boolean = true
            override fun isDirectory(path: Path): Boolean = false
            override fun isRegularFile(path: Path): Boolean = true
            override fun walk(path: Path): java.util.stream.Stream<Path> = java.util.stream.Stream.empty()
            override fun readAllLines(path: Path): List<String> = listOf(
                "package com.example",
                "",
                "class Service {",
                "    fun doWork() {}",
                "    fun doMore() {}",
                "    private fun internal() {}",
                "}"
            )
            override fun readString(path: Path): String = readAllLines(path).joinToString("\n")
        }

        val kotlinParser = object : KotlinSourceParserPort {
            override fun parse(content: String): SourceParserService.ParsedSource = parsedSource
        }

        val service = SourceParserService(log, filesystem, kotlinParser)
        val result = service.parseSource(Path.of("/src/main/java/com/example/Service.kt"))

        assertEquals(2, result?.publicMethodCounts?.get("Service"))
    }

    // ──────────────────────────────────────────────
    // SP-06: Public Method Count / Total Lines
    // ──────────────────────────────────────────────

    @Test
    fun returnsCorrectTotalLinesForKotlinFile() {
        val parsedSource = SourceParserService.ParsedSource(
            packageName = "com.example",
            classNames = listOf("Foo"),
            classDeclarationLines = mapOf("Foo" to 3),
            imports = emptyList(),
            publicMethodCounts = mapOf("Foo" to 1),
            typeRefs = emptyList(),
            totalLines = 5
        )

        val log = object : LogPort {
            override fun debug(msg: String) {}
            override fun info(msg: String) {}
            override fun warn(msg: String) {}
            override fun error(msg: String) {}
        }

        val filesystem = object : FilesystemPort {
            override fun exists(path: Path): Boolean = true
            override fun isDirectory(path: Path): Boolean = false
            override fun isRegularFile(path: Path): Boolean = true
            override fun walk(path: Path): java.util.stream.Stream<Path> = java.util.stream.Stream.empty()
            override fun readAllLines(path: Path): List<String> = listOf(
                "package com.example",
                "",
                "class Foo {",
                "    fun bar() {}",
                "}"
            )
            override fun readString(path: Path): String = readAllLines(path).joinToString("\n")
        }

        val kotlinParser = object : KotlinSourceParserPort {
            override fun parse(content: String): SourceParserService.ParsedSource = parsedSource
        }

        val service = SourceParserService(log, filesystem, kotlinParser)
        val result = service.parseSource(Path.of("/src/main/java/com/example/Foo.kt"))

        assertEquals(5, result?.totalLines)
    }

    // ──────────────────────────────────────────────
    // SP-07: Java Parsing (using temp file)
    // ──────────────────────────────────────────────

    @Test
    fun parsesJavaFileWithTempFile(@TempDir tempDir: Path) {
        val log = object : LogPort {
            override fun debug(msg: String) {}
            override fun info(msg: String) {}
            override fun warn(msg: String) {}
            override fun error(msg: String) {}
        }

        val filesystem = object : FilesystemPort {
            override fun exists(path: Path): Boolean = Files.exists(path)
            override fun isDirectory(path: Path): Boolean = Files.isDirectory(path)
            override fun isRegularFile(path: Path): Boolean = Files.isRegularFile(path)
            override fun walk(path: Path): java.util.stream.Stream<Path> = Files.walk(path)
            override fun readAllLines(path: Path): List<String> = Files.readAllLines(path)
            override fun readString(path: Path): String = Files.readString(path)
        }

        val kotlinParser = object : KotlinSourceParserPort {
            override fun parse(content: String): SourceParserService.ParsedSource {
                throw IllegalStateException("Should not be called for Java files")
            }
        }

        val service = SourceParserService(log, filesystem, kotlinParser)

        // Create a real Java file
        val javaFile = tempDir.resolve("Order.java")
        Files.writeString(
            javaFile, """
            package com.example.domain;
            
            public class Order {
            }
        """.trimIndent()
        )

        val result = service.parseSource(javaFile)

        assertEquals("com.example.domain", result?.packageName)
        assertTrue(result?.classNames?.contains("Order") == true)
    }

    // ──────────────────────────────────────────────
    // SP-08: Error Handling
    // ──────────────────────────────────────────────

    @Test
    fun returnsNullWhenFileIsNotJavaOrKotlin() {
        val log = object : LogPort {
            override fun debug(msg: String) {}
            override fun info(msg: String) {}
            override fun warn(msg: String) {}
            override fun error(msg: String) {}
        }

        val filesystem = object : FilesystemPort {
            override fun exists(path: Path): Boolean = true
            override fun isDirectory(path: Path): Boolean = false
            override fun isRegularFile(path: Path): Boolean = true
            override fun walk(path: Path): java.util.stream.Stream<Path> = java.util.stream.Stream.empty()
            override fun readAllLines(path: Path): List<String> = emptyList()
            override fun readString(path: Path): String = ""
        }

        val kotlinParser = object : KotlinSourceParserPort {
            override fun parse(content: String): SourceParserService.ParsedSource {
                throw IllegalStateException("Should not be called")
            }
        }

        val service = SourceParserService(log, filesystem, kotlinParser)
        val result = service.parseSource(Path.of("/src/main/java/com/example/README.txt"))

        assertNull(result)
    }

    @Test
    fun returnsNullAndLogsWarningOnParseError() {
        var warnedMessage: String? = null

        val log = object : LogPort {
            override fun debug(msg: String) {}
            override fun info(msg: String) {}
            override fun warn(msg: String) {
                warnedMessage = msg
            }
            override fun error(msg: String) {}
        }

        val filesystem = object : FilesystemPort {
            override fun exists(path: Path): Boolean = true
            override fun isDirectory(path: Path): Boolean = false
            override fun isRegularFile(path: Path): Boolean = true
            override fun walk(path: Path): java.util.stream.Stream<Path> = java.util.stream.Stream.empty()
            override fun readAllLines(path: Path): List<String> {
                throw RuntimeException("Simulated read error")
            }
            override fun readString(path: Path): String = throw RuntimeException("Simulated read error")
        }

        val kotlinParser = object : KotlinSourceParserPort {
            override fun parse(content: String): SourceParserService.ParsedSource {
                throw IllegalStateException("Should not be called")
            }
        }

        val service = SourceParserService(log, filesystem, kotlinParser)
        val result = service.parseSource(Path.of("/src/main/java/com/example/Broken.kt"))

        assertNull(result)
        assertTrue(warnedMessage?.contains("Failed to parse") == true)
    }

    // ──────────────────────────────────────────────
    // SP-09: Line Numbers (class declaration lines)
    // ──────────────────────────────────────────────

    @Test
    fun capturesClassDeclarationLinesForKotlinFile() {
        val parsedSource = SourceParserService.ParsedSource(
            packageName = "com.example",
            classNames = listOf("MyService"),
            classDeclarationLines = mapOf("MyService" to 3),
            imports = emptyList(),
            publicMethodCounts = mapOf("MyService" to 0),
            typeRefs = emptyList(),
            totalLines = 5
        )

        val log = object : LogPort {
            override fun debug(msg: String) {}
            override fun info(msg: String) {}
            override fun warn(msg: String) {}
            override fun error(msg: String) {}
        }

        val filesystem = object : FilesystemPort {
            override fun exists(path: Path): Boolean = true
            override fun isDirectory(path: Path): Boolean = false
            override fun isRegularFile(path: Path): Boolean = true
            override fun walk(path: Path): java.util.stream.Stream<Path> = java.util.stream.Stream.empty()
            override fun readAllLines(path: Path): List<String> = listOf(
                "package com.example",
                "",
                "class MyService {",
                "}"
            )
            override fun readString(path: Path): String = readAllLines(path).joinToString("\n")
        }

        val kotlinParser = object : KotlinSourceParserPort {
            override fun parse(content: String): SourceParserService.ParsedSource = parsedSource
        }

        val service = SourceParserService(log, filesystem, kotlinParser)
        val result = service.parseSource(Path.of("/src/main/java/com/example/MyService.kt"))

        assertEquals(3, result?.classDeclarationLines?.get("MyService"))
    }
}
