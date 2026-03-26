package com.cquilez.arch.application.service

import com.cquilez.arch.application.port.FilesystemPort
import com.cquilez.arch.application.port.LogPort
import com.cquilez.arch.application.service.rule.RuleValidationExecutor
import com.cquilez.arch.domain.*
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.isRegularFile


class RuleEvaluatorService(
    private val log: LogPort,
    private val filesystem: FilesystemPort,
    private val layerFinderService: LayerFinderService,
    private val patternMatcherService: PatternMatcherService,
    private val ruleValidationExecutor: RuleValidationExecutor,
    private val sourceParserService: SourceParserService
) {
    typealias ParsedSource = SourceParserService.ParsedSource
    typealias ImportRef = SourceParserService.ImportRef
    typealias TypeRef = SourceParserService.TypeRef

    fun evaluateRules(
        sourceRoots: List<String>,
        projectRules: ProjectRules
    ): ProjectAnalysis {
        log.debug("Source roots found: ${sourceRoots.size}")
        val typeCounts = LinkedHashMap<String, Int>()
        projectRules.types.forEach { typeCounts[it.name] = 0 }
        val projectAnalysis = ProjectAnalysis(0, LinkedHashMap(), typeCounts)

        for (rootStr in sourceRoots) {
            val rootPath = Paths.get(rootStr)
            log.debug("Source root: $rootPath")
            if (!filesystem.exists(rootPath)) continue

            analyzeSourceRootClasses(rootPath, rootStr, projectAnalysis, projectRules)
        }

        return projectAnalysis
    }

    private fun analyzeSourceRootClasses(
        rootPath: Path,
        rootStr: String,
        projectAnalysis: ProjectAnalysis,
        projectRules: ProjectRules
    ) {
        try {
            filesystem.walk(rootPath).use { paths ->
                paths
                    .filter { path ->
                        path.isRegularFile() &&
                                (path.toString().endsWith(".java") || path.toString().endsWith(".kt"))
                    }
                    .forEach { path ->
                        try {
                            projectAnalysis.totalClasses++
                            val parsed = sourceParserService.parseSource(path) ?: return@forEach
                            countTypes(projectRules, parsed, path, projectAnalysis)
                            projectRules.rules.forEach { rule ->
                                testRule(
                                    projectRules,
                                    rule,
                                    path,
                                    parsed,
                                    projectAnalysis
                                )
                            }
                        } catch (e: Exception) {
                            log.warn("Failed to parse ${path.toAbsolutePath()}: ${e.message}")
                        }
                    }
            }
        } catch (e: IOException) {
            log.warn("Failed to read sources under $rootStr: ${e.message}")
        }
    }

    fun testRule(
        projectRules: ProjectRules,
        rule: Rule,
        path: Path,
        parsed: ParsedSource,
        projectAnalysis: ProjectAnalysis
    ): Boolean {
        if (!path.isRegularFile()) return true

        log.debug("Testing rule: $rule against path: $path")

        val applies = ruleApplies(projectRules, rule, path, parsed)
        if (!applies) return true

        val violations = ruleValidationExecutor.validate(projectRules, rule, parsed, path)
        if (violations.isNotEmpty()) {
            violations.forEach { projectAnalysis.addViolation(rule, it) }
            return false
        }

        return true
    }

    private fun ruleApplies(projectRules: ProjectRules, rule: Rule, path: Path, parsed: ParsedSource): Boolean {
        val matchesLayer = if (rule.layers.isNotEmpty()) {
            rule.layers.any { layerName ->
                val layer = layerFinderService.findLayer(projectRules.layers, layerName)
                belongsToLayer(layer.location, parsed.packageName, path)
            }
        } else {
            true
        }
        log.debug("Rule layers: ${rule.layers}")
        log.debug("Class package: ${parsed.packageName}")
        log.debug("Matches rule layers: $matchesLayer")

        if (rule.layers.isNotEmpty()) {
            return matchesLayer
        }

        if (rule.types.isNotEmpty()) {
            return rule.types.any { typeName ->
                matchesType(projectRules, typeName, parsed, path)
            }
        }

        return if (!rule.classPatterns.isNullOrEmpty()) {
            parsed.classNames.any { name ->
                rule.classPatterns.any { pattern -> patternMatcherService.matchesPattern(name, pattern, true) }
            }
        } else {
            true
        }
    }

    private fun belongsToLayer(layerLocation: String, packageName: String?, path: Path): Boolean {
        if (!packageName.isNullOrBlank() && packageName.startsWith(layerLocation)) return true
        val pathFragment = layerLocation.replace('.', '/')
        return path.toString().replace('\\', '/').contains("/$pathFragment/")
    }

    private fun countTypes(
        projectRules: ProjectRules,
        parsed: ParsedSource,
        path: Path,
        projectAnalysis: ProjectAnalysis
    ) {
        if (projectRules.types.isEmpty() || parsed.classNames.isEmpty()) return
        projectRules.types.forEach { type ->
            if (type.patterns.isEmpty()) return@forEach
            val layerLocation = type.layer?.let { layerName ->
                layerFinderService.findLayer(projectRules.layers, layerName).location
            }
            parsed.classNames.forEach { className ->
                if (type.patterns.any { pattern -> patternMatcherService.matchesPattern(className, pattern, true) } &&
                    (layerLocation == null || belongsToLayer(layerLocation, parsed.packageName, path))) {
                    projectAnalysis.addTypeMatch(type.name)
                }
            }
        }
    }

    private fun matchesType(
        projectRules: ProjectRules,
        typeName: String,
        parsed: ParsedSource,
        path: Path
    ): Boolean {
        val type = projectRules.types.firstOrNull { it.name == typeName } ?: return false
        if (type.patterns.isEmpty()) return false
        val layerLocation = type.layer?.let { layerName ->
            layerFinderService.findLayer(projectRules.layers, layerName).location
        }
        return parsed.classNames.any { className ->
            val patternMatch = type.patterns.any { pattern ->
                patternMatcherService.matchesPattern(className, pattern, true)
            }
            if (!patternMatch) {
                false
            } else {
                layerLocation == null || belongsToLayer(layerLocation, parsed.packageName, path)
            }
        }
    }

}
