package com.cquilez.arch.application.service

import com.cquilez.arch.application.port.FilesystemPort
import com.cquilez.arch.domain.Layer
import com.cquilez.arch.domain.ProjectRules
import com.cquilez.arch.domain.Rule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.util.stream.Stream

class RuleValidatorServiceTest {

    // ──────────────────────────────────────────────
    // AV-01: Layer Import Validation
    // Validates that layer packages exist in source roots
    // ──────────────────────────────────────────────

    @Test
    fun validatesLayerWithExistingPackage() {
        val filesystem = object : FilesystemPort {
            override fun exists(path: Path): Boolean = true
            override fun isDirectory(path: Path): Boolean = true
            override fun isRegularFile(path: Path): Boolean = false
            override fun walk(path: Path): Stream<Path> = Stream.empty()
            override fun readAllLines(path: Path): List<String> = emptyList()
            override fun readString(path: Path): String = ""
        }

        val service = RuleValidatorService(filesystem)

        val layers = listOf(Layer("domain", "com.example.domain"))
        val projectRules = ProjectRules(layers, emptyList(), emptyList())

        // Should not throw - layer package exists
        service.validateLayers(listOf("/src/main/java"), projectRules)
    }

    @Test
    fun throwsExceptionWhenLayerPackageDoesNotExist() {
        val filesystem = object : FilesystemPort {
            override fun exists(path: Path): Boolean = false
            override fun isDirectory(path: Path): Boolean = false
            override fun isRegularFile(path: Path): Boolean = false
            override fun walk(path: Path): Stream<Path> = Stream.empty()
            override fun readAllLines(path: Path): List<String> = emptyList()
            override fun readString(path: Path): String = ""
        }

        val service = RuleValidatorService(filesystem)

        val layers = listOf(Layer("domain", "com.example.domain"))
        val projectRules = ProjectRules(layers, emptyList(), emptyList())

        try {
            service.validateLayers(listOf("/src/main/java"), projectRules)
            assertTrue(false, "Expected an exception to be thrown")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("Invalid layer configuration") == true)
            assertTrue(e.message?.contains("com.example.domain") == true)
        }
    }

    @Test
    fun validatesLayerWithBlankLocation() {
        val filesystem = object : FilesystemPort {
            override fun exists(path: Path): Boolean = true
            override fun isDirectory(path: Path): Boolean = true
            override fun isRegularFile(path: Path): Boolean = false
            override fun walk(path: Path): Stream<Path> = Stream.empty()
            override fun readAllLines(path: Path): List<String> = emptyList()
            override fun readString(path: Path): String = ""
        }

        val service = RuleValidatorService(filesystem)

        val layers = listOf(Layer("domain", ""))
        val projectRules = ProjectRules(layers, emptyList(), emptyList())

        // Should not throw - blank location is skipped
        service.validateLayers(listOf("/src/main/java"), projectRules)
    }

    // ──────────────────────────────────────────────
    // AV-02: Adapter/Port/UseCase Placement
    // Validates that rules reference known layers and types
    // ──────────────────────────────────────────────

    @Test
    fun validatesRulesWithKnownLayers() {
        val filesystem = object : FilesystemPort {
            override fun exists(path: Path): Boolean = true
            override fun isDirectory(path: Path): Boolean = true
            override fun isRegularFile(path: Path): Boolean = false
            override fun walk(path: Path): Stream<Path> = Stream.empty()
            override fun readAllLines(path: Path): List<String> = emptyList()
            override fun readString(path: Path): String = ""
        }

        val service = RuleValidatorService(filesystem)

        val layers = listOf(Layer("domain", "com.example.domain"))
        val rules = listOf(
            Rule(
                title = "Domain rule",
                layers = listOf("domain"),
                types = emptyList(),
                classPatterns = null,
                allowed = null,
                forbidden = null
            )
        )
        val projectRules = ProjectRules(layers, emptyList(), rules)

        // Should not throw - layer "domain" is known
        service.validateRules(projectRules)
    }

    @Test
    fun throwsExceptionWhenRuleReferencesUnknownLayer() {
        val filesystem = object : FilesystemPort {
            override fun exists(path: Path): Boolean = true
            override fun isDirectory(path: Path): Boolean = true
            override fun isRegularFile(path: Path): Boolean = false
            override fun walk(path: Path): Stream<Path> = Stream.empty()
            override fun readAllLines(path: Path): List<String> = emptyList()
            override fun readString(path: Path): String = ""
        }

        val service = RuleValidatorService(filesystem)

        val layers = listOf(Layer("domain", "com.example.domain"))
        val rules = listOf(
            Rule(
                title = "Bad rule",
                layers = listOf("unknownLayer"),
                types = emptyList(),
                classPatterns = null,
                allowed = null,
                forbidden = null
            )
        )
        val projectRules = ProjectRules(layers, emptyList(), rules)

        try {
            service.validateRules(projectRules)
            assertTrue(false, "Expected an exception to be thrown")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("Invalid rule configuration") == true)
            assertTrue(e.message?.contains("unknownLayer") == true)
        }
    }

    @Test
    fun validatesRulesWithKnownTypes() {
        val filesystem = object : FilesystemPort {
            override fun exists(path: Path): Boolean = true
            override fun isDirectory(path: Path): Boolean = true
            override fun isRegularFile(path: Path): Boolean = false
            override fun walk(path: Path): Stream<Path> = Stream.empty()
            override fun readAllLines(path: Path): List<String> = emptyList()
            override fun readString(path: Path): String = ""
        }

        val service = RuleValidatorService(filesystem)

        val types = listOf(com.cquilez.arch.domain.TypeDefinition("Service", listOf("*Service"), "application"))
        val rules = listOf(
            Rule(
                title = "Service rule",
                layers = emptyList(),
                types = listOf("Service"),
                classPatterns = null,
                allowed = null,
                forbidden = null
            )
        )
        val projectRules = ProjectRules(emptyList(), types, rules)

        // Should not throw - type "Service" is known
        service.validateRules(projectRules)
    }

    @Test
    fun throwsExceptionWhenRuleReferencesUnknownType() {
        val filesystem = object : FilesystemPort {
            override fun exists(path: Path): Boolean = true
            override fun isDirectory(path: Path): Boolean = true
            override fun isRegularFile(path: Path): Boolean = false
            override fun walk(path: Path): Stream<Path> = Stream.empty()
            override fun readAllLines(path: Path): List<String> = emptyList()
            override fun readString(path: Path): String = ""
        }

        val service = RuleValidatorService(filesystem)

        val rules = listOf(
            Rule(
                title = "Bad rule",
                layers = emptyList(),
                types = listOf("UnknownType"),
                classPatterns = null,
                allowed = null,
                forbidden = null
            )
        )
        val projectRules = ProjectRules(emptyList(), emptyList(), rules)

        try {
            service.validateRules(projectRules)
            assertTrue(false, "Expected an exception to be thrown")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("Invalid rule configuration") == true)
            assertTrue(e.message?.contains("UnknownType") == true)
        }
    }

    @Test
    fun validatesAllowedLayersInRule() {
        val filesystem = object : FilesystemPort {
            override fun exists(path: Path): Boolean = true
            override fun isDirectory(path: Path): Boolean = true
            override fun isRegularFile(path: Path): Boolean = false
            override fun walk(path: Path): Stream<Path> = Stream.empty()
            override fun readAllLines(path: Path): List<String> = emptyList()
            override fun readString(path: Path): String = ""
        }

        val service = RuleValidatorService(filesystem)

        val layers = listOf(Layer("domain", "com.example.domain"))
        val rules = listOf(
            Rule(
                title = "Domain rule",
                layers = emptyList(),
                types = emptyList(),
                classPatterns = null,
                allowed = com.cquilez.arch.domain.Allowed(null, "[domain]", null),
                forbidden = null
            )
        )
        val projectRules = ProjectRules(layers, emptyList(), rules)

        // Should not throw - allowed layer "domain" is known
        service.validateRules(projectRules)
    }

    @Test
    fun throwsExceptionWhenAllowedLayerIsUnknown() {
        val filesystem = object : FilesystemPort {
            override fun exists(path: Path): Boolean = true
            override fun isDirectory(path: Path): Boolean = true
            override fun isRegularFile(path: Path): Boolean = false
            override fun walk(path: Path): Stream<Path> = Stream.empty()
            override fun readAllLines(path: Path): List<String> = emptyList()
            override fun readString(path: Path): String = ""
        }

        val service = RuleValidatorService(filesystem)

        val layers = listOf(Layer("domain", "com.example.domain"))
        val rules = listOf(
            Rule(
                title = "Bad rule",
                layers = emptyList(),
                types = emptyList(),
                classPatterns = null,
                allowed = com.cquilez.arch.domain.Allowed(null, "[unknownLayer]", null),
                forbidden = null
            )
        )
        val projectRules = ProjectRules(layers, emptyList(), rules)

        try {
            service.validateRules(projectRules)
            assertTrue(false, "Expected an exception to be thrown")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("Invalid rule configuration") == true)
            assertTrue(e.message?.contains("unknownLayer") == true)
        }
    }

    @Test
    fun validatesForbiddenLayersInRule() {
        val filesystem = object : FilesystemPort {
            override fun exists(path: Path): Boolean = true
            override fun isDirectory(path: Path): Boolean = true
            override fun isRegularFile(path: Path): Boolean = false
            override fun walk(path: Path): Stream<Path> = Stream.empty()
            override fun readAllLines(path: Path): List<String> = emptyList()
            override fun readString(path: Path): String = ""
        }

        val service = RuleValidatorService(filesystem)

        val layers = listOf(Layer("domain", "com.example.domain"))
        val rules = listOf(
            Rule(
                title = "Domain rule",
                layers = emptyList(),
                types = emptyList(),
                classPatterns = null,
                allowed = null,
                forbidden = com.cquilez.arch.domain.Forbidden(emptyList(), "[domain]", emptyList())
            )
        )
        val projectRules = ProjectRules(layers, emptyList(), rules)

        // Should not throw - forbidden layer "domain" is known
        service.validateRules(projectRules)
    }

    @Test
    fun throwsExceptionWhenForbiddenLayerIsUnknown() {
        val filesystem = object : FilesystemPort {
            override fun exists(path: Path): Boolean = true
            override fun isDirectory(path: Path): Boolean = true
            override fun isRegularFile(path: Path): Boolean = false
            override fun walk(path: Path): Stream<Path> = Stream.empty()
            override fun readAllLines(path: Path): List<String> = emptyList()
            override fun readString(path: Path): String = ""
        }

        val service = RuleValidatorService(filesystem)

        val layers = listOf(Layer("domain", "com.example.domain"))
        val rules = listOf(
            Rule(
                title = "Bad rule",
                layers = emptyList(),
                types = emptyList(),
                classPatterns = null,
                allowed = null,
                forbidden = com.cquilez.arch.domain.Forbidden(emptyList(), "[unknownLayer]", emptyList())
            )
        )
        val projectRules = ProjectRules(layers, emptyList(), rules)

        try {
            service.validateRules(projectRules)
            assertTrue(false, "Expected an exception to be thrown")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("Invalid rule configuration") == true)
            assertTrue(e.message?.contains("unknownLayer") == true)
        }
    }

    @Test
    fun validatesForbiddenTypesInRule() {
        val filesystem = object : FilesystemPort {
            override fun exists(path: Path): Boolean = true
            override fun isDirectory(path: Path): Boolean = true
            override fun isRegularFile(path: Path): Boolean = false
            override fun walk(path: Path): Stream<Path> = Stream.empty()
            override fun readAllLines(path: Path): List<String> = emptyList()
            override fun readString(path: Path): String = ""
        }

        val service = RuleValidatorService(filesystem)

        val types = listOf(com.cquilez.arch.domain.TypeDefinition("Service", listOf("*Service"), "application"))
        val rules = listOf(
            Rule(
                title = "Service rule",
                layers = emptyList(),
                types = emptyList(),
                classPatterns = null,
                allowed = null,
                forbidden = com.cquilez.arch.domain.Forbidden(emptyList(), null, listOf("Service"))
            )
        )
        val projectRules = ProjectRules(emptyList(), types, rules)

        // Should not throw - forbidden type "Service" is known
        service.validateRules(projectRules)
    }

    @Test
    fun throwsExceptionWhenForbiddenTypeIsUnknown() {
        val filesystem = object : FilesystemPort {
            override fun exists(path: Path): Boolean = true
            override fun isDirectory(path: Path): Boolean = true
            override fun isRegularFile(path: Path): Boolean = false
            override fun walk(path: Path): Stream<Path> = Stream.empty()
            override fun readAllLines(path: Path): List<String> = emptyList()
            override fun readString(path: Path): String = ""
        }

        val service = RuleValidatorService(filesystem)

        val rules = listOf(
            Rule(
                title = "Bad rule",
                layers = emptyList(),
                types = emptyList(),
                classPatterns = null,
                allowed = null,
                forbidden = com.cquilez.arch.domain.Forbidden(emptyList(), null, listOf("UnknownType"))
            )
        )
        val projectRules = ProjectRules(emptyList(), emptyList(), rules)

        try {
            service.validateRules(projectRules)
            assertTrue(false, "Expected an exception to be thrown")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("Invalid rule configuration") == true)
            assertTrue(e.message?.contains("UnknownType") == true)
        }
    }
}
