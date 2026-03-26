package com.cquilez.arch.application.service.rule

import com.cquilez.arch.application.service.LayerFinderService
import com.cquilez.arch.application.service.PatternMatcherService
import com.cquilez.arch.application.service.SourceParserService
import com.cquilez.arch.domain.Layer
import com.cquilez.arch.domain.ProjectRules
import com.cquilez.arch.domain.Rule
import com.cquilez.arch.domain.RuleViolation
import java.nio.file.Path

class LayerImportValidator(
    patternMatcherService: PatternMatcherService,
    private val layerFinderService: LayerFinderService
) : ProjectRuleValidator(patternMatcherService) {

    override fun validate(
        projectRules: ProjectRules,
        rule: Rule,
        parsed: SourceParserService.ParsedSource,
        path: Path
    ): List<RuleViolation> {
        val allowedLayersRaw = rule.allowed?.allowedLayers ?: return emptyList()
        val allowedLayerNames = splitLayerNames(allowedLayersRaw)
        if (allowedLayerNames.isEmpty()) return emptyList()

        val layersByLocation = projectRules.layers
            .filter { it.location.isNotBlank() }
            .sortedByDescending { it.location.length }

        val classLayer = resolveClassLayer(parsed, layersByLocation)

        val className = resolveViolationClassName(parsed, path)
        return parsed.imports.mapNotNull { imp ->
            val matchingLayer = layersByLocation.firstOrNull { imp.name.startsWith(it.location) }
            when {
                matchingLayer == null -> null
                classLayer != null && matchingLayer.name == classLayer.name -> null
                allowedLayerNames.contains(matchingLayer.name) -> null
                else -> RuleViolation(path.toString(), "Import from disallowed layer: ${imp.name}", imp.line, className)
            }
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

    private fun splitLayerNames(raw: String): Set<String> {
        val normalized = raw.trim().removePrefix("[").removeSuffix("]")
        if (normalized.isBlank()) return emptySet()
        return normalized.split(',').map { it.trim() }.filter { it.isNotBlank() }.toSet()
    }
}
