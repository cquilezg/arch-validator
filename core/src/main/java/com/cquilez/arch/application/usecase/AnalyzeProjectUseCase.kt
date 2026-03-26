package com.cquilez.arch.application.usecase

import com.cquilez.arch.application.port.FilesystemPort
import com.cquilez.arch.application.port.LogPort
import com.cquilez.arch.application.port.ParserPort
import com.cquilez.arch.application.service.RuleEvaluatorService
import com.cquilez.arch.application.service.RuleValidatorService
import com.cquilez.arch.domain.AnalysisConfig
import com.cquilez.arch.domain.Project
import com.cquilez.arch.domain.ProjectAnalysis
import com.cquilez.arch.domain.ProjectRules
import com.cquilez.arch.domain.RuleViolation
import com.cquilez.arch.domain.exception.EvaluationFailedException
import java.nio.file.Paths
import java.nio.file.Path

open class AnalyzeProjectUseCase(
    private val log: LogPort,
    private val filesystem: FilesystemPort,
    private val parser: ParserPort,
    private val ruleValidatorService: RuleValidatorService,
    private val ruleEvaluatorService: RuleEvaluatorService
) {

    fun execute(project: Project, rulesFile: Path, config: AnalysisConfig = AnalysisConfig()) {
        val projectRules = loadRules(rulesFile, config) ?: return
        log.info("Layers: ${projectRules.layers.size}")
        log.info("Types: ${projectRules.types.size}")
        log.info("Rules: ${projectRules.rules.size}")

        val sourceRoots =
            project.compileSourceRoots + if (config.includeTests) project.testCompileSourceRoots else emptyList()

        if (projectRules.layers.isNotEmpty()) {
            ruleValidatorService.validateLayers(sourceRoots, projectRules)
        }
        if (projectRules.rules.isNotEmpty()) {
            ruleValidatorService.validateRules(projectRules)
            log.info("")
            log.info("Evaluating rules...")
            val projectAnalysis = ruleEvaluatorService.evaluateRules(sourceRoots, projectRules)
            log.info("Rules evaluation finished.")
            log.info("")
            logProjectAnalysis(projectRules, projectAnalysis)
        } else {
            log.info("No rules defined.")
        }
    }

    private fun loadRules(rulesFile: Path, config: AnalysisConfig): ProjectRules? {
        if (!filesystem.exists(rulesFile) || !filesystem.isRegularFile(rulesFile)) {
            check(!(config.failIfNoRules)) { "No rules found. Searched in location/s: ${rulesFile.toAbsolutePath()}." }
            log.warn("No rules found")
            return null
        }

        val yamlContent = try {
            filesystem.readString(rulesFile)
        } catch (e: Exception) {
            throw IllegalStateException("Failed to read ${rulesFile.fileName}", e)
        }

        val raw = try {
            parser.parse(yamlContent)
        } catch (e: Exception) {
            throw IllegalStateException("Failed to parse YAML from ${rulesFile.fileName}", e)
        }

        logUnknownKeys(raw)

        val projectRules = ProjectRules.fromMap(raw)
        validateRuleDefinitions(projectRules)
        return projectRules
    }

    private fun logUnknownKeys(raw: Map<String, Any>) {
        logUnknownTopLevelKeys(raw)
        logUnknownLayerKeys(raw)
        logUnknownTypeKeys(raw)
        logUnknownRuleKeys(raw)
    }

    private fun logUnknownTopLevelKeys(raw: Map<String, Any>) {
        val knownTopLevelKeys = setOf("layers", "types", "rules")
        raw.keys.forEach { key ->
            if (key !in knownTopLevelKeys) {
                log.warn("Unrecognized key in rules file: '$key'")
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun logUnknownLayerKeys(raw: Map<String, Any>) {
        val knownLayerKeys = setOf("location")
        val layersMap = raw["layers"] as? Map<String, Any>
        layersMap?.forEach { (layerName, layerValue) ->
            if (layerValue is Map<*, *>) {
                layerValue.keys.forEach { key ->
                    if (key.toString() !in knownLayerKeys) {
                        log.warn("Unrecognized key in layer '$layerName': '$key'")
                    }
                }
            }
        }
    }

    private fun logUnknownTypeKeys(raw: Map<String, Any>) {
        val knownTypeKeys = setOf("name", "patterns", "layer")
        val typesRaw = raw["types"] as? List<*>
        typesRaw?.forEachIndexed { index, typeRaw ->
            if (typeRaw is Map<*, *>) {
                typeRaw.keys.forEach { key ->
                    if (key.toString() !in knownTypeKeys) {
                        log.warn("Unrecognized key in types[$index]: '$key'")
                    }
                }
            }
        }
    }

    private fun logUnknownRuleKeys(raw: Map<String, Any>) {
        val knownRuleKeys = setOf("title", "layers", "types", "classes", "allowed", "forbidden")
        val rulesRaw = raw["rules"] as? List<*>
        rulesRaw?.forEachIndexed { index, ruleRaw ->
            if (ruleRaw is Map<*, *>) {
                ruleRaw.keys.forEach { key ->
                    if (key.toString() !in knownRuleKeys) {
                        log.warn("Unrecognized key in rules[$index]: '$key'")
                    }
                }
            }
        }
    }

    private fun validateRuleDefinitions(projectRules: ProjectRules) {
        val errors = mutableListOf<String>()

        val duplicateLayers = projectRules.layers
            .groupBy { it.name.lowercase() }
            .values
            .filter { it.size > 1 }
            .flatMap { group -> group.map { it.name } }
            .distinct()
        if (duplicateLayers.isNotEmpty()) {
            errors.add("Duplicate layer names: ${duplicateLayers.joinToString(", ")}")
        }

        val duplicateTypes = projectRules.types
            .groupBy { it.name.lowercase() }
            .values
            .filter { it.size > 1 }
            .flatMap { group -> group.map { it.name } }
            .distinct()
        if (duplicateTypes.isNotEmpty()) {
            errors.add("Duplicate type names: ${duplicateTypes.joinToString(", ")}")
        }

        val duplicateRules = projectRules.rules
            .groupBy { it.title.lowercase() }
            .values
            .filter { it.size > 1 }
            .flatMap { group -> group.map { it.title } }
            .distinct()
        if (duplicateRules.isNotEmpty()) {
            errors.add("Duplicate rule titles: ${duplicateRules.joinToString(", ")}")
        }

        projectRules.rules.forEach { rule ->
            val hasPredicate = rule.allowed != null || rule.forbidden != null
            if (!hasPredicate) {
                errors.add("Rule '${rule.title}' needs a predicate: 'allowed' and/or 'forbidden'")
            }
        }

        check(errors.isEmpty()) { "Invalid rule configuration:\n" + errors.joinToString("\n") }
    }

    private fun logProjectAnalysis(projectRules: ProjectRules, projectAnalysis: ProjectAnalysis) {
        log.info("=== Project analysis ===")
        log.info("Total rules evaluated: ${projectRules.rules.size}")
        log.info("Total classes: ${projectAnalysis.totalClasses}")
        log.info("")

        log.info("=== Violations by rule ===")
        if (projectAnalysis.violations.isEmpty()) {
            log.info("No violations detected")
        } else {
            projectAnalysis.violations.forEach { (rule, violations) ->
                log.info("Rule: ${rule.title}")
                violations.forEach { violation ->
                    log.info("  at ${formatViolationLocation(violation)}")
                    log.info("  Cause: ${violation.cause}")
                    log.info("")
                }
            }
            log.info("")
            log.info("Total VIOLATIONS: ${projectAnalysis.totalViolations()}")
            log.info("")
        }

        log.info("=== Types summary ===")
        if (projectAnalysis.typeCounts.isEmpty()) {
            log.info("No types defined")
        } else {
            projectAnalysis.typeCounts.forEach { (typeName, count) ->
                log.info("$typeName: $count")
            }
        }
        log.info("")

        if (projectAnalysis.violations.isEmpty()) {
            log.info("Project analysis successful!")
        } else {
            log.error("Rules evaluation failed.")
            throw EvaluationFailedException("Rules evaluation failed!")
        }
    }

    private fun formatViolationLocation(violation: RuleViolation): String {
        val fileName = runCatching { Paths.get(violation.path).fileName?.toString() }.getOrNull()
        val line = violation.line
        return if (!violation.className.isNullOrBlank()) {
            if (!fileName.isNullOrBlank() && line != null) {
                "${violation.className}(${fileName}:${line})"
            } else if (!fileName.isNullOrBlank()) {
                "${violation.className}(${fileName})"
            } else {
                violation.className
            }
        } else {
            if (line != null) "${violation.path}:${line}" else violation.path
        }
    }
}
