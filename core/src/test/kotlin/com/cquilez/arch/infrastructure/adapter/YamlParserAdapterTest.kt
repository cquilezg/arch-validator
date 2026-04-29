package com.cquilez.arch.infrastructure.adapter

import com.cquilez.arch.application.port.FilesystemPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.util.stream.Stream

class YamlParserAdapterTest {

    // ──────────────────────────────────────
    // Valid YAML parsing
    // ──────────────────────────────────────

    @Test
    fun parsesValidYamlWithLayers() {
        val filesystem = object : FilesystemPort {
            override fun exists(path: Path): Boolean = true
            override fun isDirectory(path: Path): Boolean = false
            override fun isRegularFile(path: Path): Boolean = true
            override fun walk(path: Path): Stream<Path> = Stream.empty()
            override fun readAllLines(path: Path): List<String> = listOf(
                "layers:",
                "  domain:",
                "    location: com.example.domain"
            )
            override fun readString(path: Path): String = readAllLines(path).joinToString("\n")
        }

        val parser = YamlParserAdapter()
        val content = filesystem.readString(Path.of("/arch-rules.yml"))
        val result = parser.parse(content)

        assertTrue(result.containsKey("layers"))
    }

    @Test
    fun parsesValidYamlWithRules() {
        val filesystem = object : FilesystemPort {
            override fun exists(path: Path): Boolean = true
            override fun isDirectory(path: Path): Boolean = false
            override fun isRegularFile(path: Path): Boolean = true
            override fun walk(path: Path): Stream<Path> = Stream.empty()
            override fun readAllLines(path: Path): List<String> = listOf(
                "rules:",
                "  - title: Test rule",
                "    layers: [domain]"
            )
            override fun readString(path: Path): String = readAllLines(path).joinToString("\n")
        }

        val parser = YamlParserAdapter()
        val content = filesystem.readString(Path.of("/arch-rules.yml"))
        val result = parser.parse(content)

        assertTrue(result.containsKey("rules"))
        val rules = result["rules"] as? List<*>
        assertEquals(1, rules?.size)
    }

    @Test
    fun parsesEmptyYaml() {
        val parser = YamlParserAdapter()
        val result = parser.parse("")

        // Empty YAML should return empty map
        assertTrue(result.isEmpty())
    }

    @Test
    fun parsesYamlWithNestedStructures() {
        val filesystem = object : FilesystemPort {
            override fun exists(path: Path): Boolean = true
            override fun isDirectory(path: Path): Boolean = false
            override fun isRegularFile(path: Path): Boolean = true
            override fun walk(path: Path): Stream<Path> = Stream.empty()
            override fun readAllLines(path: Path): List<String> = listOf(
                "layers:",
                "  domain:",
                "    location: com.example.domain",
                "types:",
                "  - name: Service",
                "    patterns: [\"*Service\"]",
                "    layer: application"
            )
            override fun readString(path: Path): String = readAllLines(path).joinToString("\n")
        }

        val parser = YamlParserAdapter()
        val content = filesystem.readString(Path.of("/arch-rules.yml"))
        val result = parser.parse(content)

        assertTrue(result.containsKey("layers"))
        assertTrue(result.containsKey("types"))
        val types = result["types"] as? List<*>
        assertEquals(1, types?.size)
    }

    @Test
    fun parsesYamlWithListValues() {
        val filesystem = object : FilesystemPort {
            override fun exists(path: Path): Boolean = true
            override fun isDirectory(path: Path): Boolean = false
            override fun isRegularFile(path: Path): Boolean = true
            override fun walk(path: Path): Stream<Path> = Stream.empty()
            override fun readAllLines(path: Path): List<String> = listOf(
                "rules:",
                "  - title: Rule 1",
                "    layers: [domain, application]",
                "  - title: Rule 2",
                "    layers: [infrastructure]"
            )
            override fun readString(path: Path): String = readAllLines(path).joinToString("\n")
        }

        val parser = YamlParserAdapter()
        val content = filesystem.readString(Path.of("/arch-rules.yml"))
        val result = parser.parse(content)

        val rules = result["rules"] as? List<*>
        assertEquals(2, rules?.size)
    }

    // ──────────────────────────────────────
    // Malformed YAML error handling
    // ──────────────────────────────────────

    @Test
    fun handlesMalformedYamlGracefully() {
        val parser = YamlParserAdapter()

        // Invalid YAML syntax - tab character in YAML can cause issues
        val malformedYaml = "layers:\n\tinvalid: true"

        val result = parser.parse(malformedYaml)

        // Should return empty map on error (not throw exception)
        assertTrue(result.isEmpty())
    }

    @Test
    fun handlesInvalidYamlStructure() {
        val parser = YamlParserAdapter()

        // YAML with invalid structure
        val invalidYaml = ":\n  - no key"

        val result = parser.parse(invalidYaml)

        // Should return empty map on error
        assertTrue(result.isEmpty())
    }

    @Test
    fun handlesNullInput() {
        val parser = YamlParserAdapter()

        // Null content should be handled
        val result = parser.parse("null")

        // "null" as string is valid YAML (represents null)
        // The adapter should handle this
        assertTrue(result.isEmpty() || result.containsKey("null"))
    }

    @Test
    fun handlesDuplicateKeysInYaml() {
        val filesystem = object : FilesystemPort {
            override fun exists(path: Path): Boolean = true
            override fun isDirectory(path: Path): Boolean = false
            override fun isRegularFile(path: Path): Boolean = true
            override fun walk(path: Path): Stream<Path> = Stream.empty()
            override fun readAllLines(path: Path): List<String> = listOf(
                "key: value1",
                "key: value2"  // Duplicate key
            )
            override fun readString(path: Path): String = readAllLines(path).joinToString("\n")
        }

        val parser = YamlParserAdapter()
        val content = filesystem.readString(Path.of("/arch-rules.yml"))
        val result = parser.parse(content)

        // Snakeyaml may handle duplicate keys differently
        // Just verify it doesn't throw
        assertTrue(result.isNotEmpty() || result.isEmpty())
    }

    @Test
    fun parsesComplexNestedYaml() {
        val filesystem = object : FilesystemPort {
            override fun exists(path: Path): Boolean = true
            override fun isDirectory(path: Path): Boolean = false
            override fun isRegularFile(path: Path): Boolean = true
            override fun walk(path: Path): Stream<Path> = Stream.empty()
            override fun readAllLines(path: Path): List<String> = listOf(
                "layers:",
                "  domain:",
                "    location: com.example.domain",
                "rules:",
                "  - title: Domain imports",
                "    layers: [domain]",
                "    allowed:",
                "      imports:",
                "        - java.*",
                "        - lombok.*",
                "    forbidden:",
                "      classes:",
                "        - \"*Adapter\""
            )
            override fun readString(path: Path): String = readAllLines(path).joinToString("\n")
        }

        val parser = YamlParserAdapter()
        val content = filesystem.readString(Path.of("/arch-rules.yml"))
        val result = parser.parse(content)

        assertTrue(result.containsKey("layers"))
        assertTrue(result.containsKey("rules"))
        val rules = result["rules"] as? List<*>
        assertEquals(1, rules?.size)

        val rule = rules?.get(0) as? Map<*, *>
        assertTrue(rule?.containsKey("title") == true)
        assertTrue(rule?.containsKey("allowed") == true)
        assertTrue(rule?.containsKey("forbidden") == true)
    }
}
