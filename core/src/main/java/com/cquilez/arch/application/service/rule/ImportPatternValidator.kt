package com.cquilez.arch.application.service.rule

import com.cquilez.arch.application.service.LayerFinderService
import com.cquilez.arch.application.service.PatternMatcherService
import com.cquilez.arch.application.service.SourceParserService
import com.cquilez.arch.domain.Layer
import com.cquilez.arch.domain.ProjectRules
import com.cquilez.arch.domain.Rule
import com.cquilez.arch.domain.RuleViolation
import java.nio.file.Path

class ImportPatternValidator(
    patternMatcherService: PatternMatcherService,
    private val layerFinderService: LayerFinderService
) : ProjectRuleValidator(patternMatcherService) {

    override fun validate(
        projectRules: ProjectRules,
        rule: Rule,
        parsed: SourceParserService.ParsedSource,
        path: Path
    ): List<RuleViolation> {
        val allowedImports = rule.allowed?.imports ?: return emptyList()
        if (allowedImports.isEmpty()) return emptyList()
        val className = resolveViolationClassName(parsed, path)
        val layersByLocation = projectRules.layers
            .filter { it.location.isNotBlank() }
            .sortedByDescending { it.location.length }
        val classLayer = resolveClassLayer(parsed, layersByLocation)
        return parsed.imports.filter { imp ->
            val matchingLayer = layersByLocation.firstOrNull { imp.name.startsWith(it.location) }
            if (classLayer != null && matchingLayer != null && matchingLayer.name == classLayer.name) {
                false
            } else {
                allowedImports.none { pattern -> patternMatcherService.matchesPattern(imp.name, pattern, false) }
            }
        }.map { imp ->
            RuleViolation(path.toString(), "Import outside allowed patterns: ${imp.name}", imp.line, className)
        }
    }

    private fun resolveViolationClassName(parsed: SourceParserService.ParsedSource, path: Path): String? {
        val className = parsed.classNames.firstOrNull()
            ?: path.fileName?.toString()?.substringBeforeLast('.')
        if (className.isNullOrBlank()) return null
        return if (parsed.packageName.isNullOrBlank()) className else "${parsed.packageName}.$className"
    }

    private fun resolveClassLayer(
        parsed: SourceParserService.ParsedSource,
        layersByLocation: List<Layer>
    ): Layer? {
        return if (parsed.packageName.isNullOrBlank()) {
            null
        } else {
            layersByLocation.firstOrNull { parsed.packageName.startsWith(it.location) }
        }
    }
}
