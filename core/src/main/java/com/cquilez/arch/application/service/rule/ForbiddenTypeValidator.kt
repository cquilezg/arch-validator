package com.cquilez.arch.application.service.rule

import com.cquilez.arch.application.service.LayerFinderService
import com.cquilez.arch.application.service.PatternMatcherService
import com.cquilez.arch.application.service.SourceParserService
import com.cquilez.arch.domain.ProjectRules
import com.cquilez.arch.domain.Rule
import com.cquilez.arch.domain.RuleViolation
import com.cquilez.arch.domain.TypeDefinition
import java.nio.file.Path

class ForbiddenTypeValidator(
    patternMatcherService: PatternMatcherService,
    private val layerFinderService: LayerFinderService
) : ProjectRuleValidator(patternMatcherService) {

    override fun validate(
        projectRules: ProjectRules,
        rule: Rule,
        parsed: SourceParserService.ParsedSource,
        path: Path
    ): List<RuleViolation> {
        val forbiddenTypes = rule.forbidden?.types ?: return emptyList()
        if (forbiddenTypes.isEmpty()) return emptyList()

        val typeDefinitions = forbiddenTypes.mapNotNull { typeName ->
            projectRules.types.firstOrNull { it.name == typeName }
        }
        if (typeDefinitions.isEmpty()) return emptyList()

        val className = resolveViolationClassName(parsed, path)
        val importsBySimpleName = buildImportsBySimpleName(parsed.imports)

        val violations = mutableListOf<RuleViolation>()

        violations.addAll(findImportViolations(parsed.imports, typeDefinitions, path, className, projectRules.layers))
        violations.addAll(
            findTypeRefViolations(
                parsed.typeRefs,
                typeDefinitions,
                importsBySimpleName,
                parsed.packageName,
                path,
                className,
                projectRules.layers
            )
        )

        return violations
    }

    private fun buildImportsBySimpleName(imports: List<SourceParserService.ImportRef>): Map<String, List<SourceParserService.ImportRef>> {
        return imports
            .mapNotNull { imp ->
                val simpleName = imp.name.substringAfterLast('.')
                simpleName.takeIf { it.isNotBlank() }?.let { it to imp }
            }
            .groupBy({ it.first }, { it.second })
    }

    private fun findImportViolations(
        imports: List<SourceParserService.ImportRef>,
        typeDefinitions: List<TypeDefinition>,
        path: Path,
        className: String?,
        layers: List<com.cquilez.arch.domain.Layer>
    ): List<RuleViolation> {
        return imports.mapNotNull { imp ->
            val matches = typeDefinitions.any { type -> matchesImport(imp, type, layers) }
            if (matches) {
                RuleViolation(path.toString(), "Import from forbidden type: ${imp.name}", imp.line, className)
            } else null
        }
    }

    private fun findTypeRefViolations(
        typeRefs: List<SourceParserService.TypeRef>,
        typeDefinitions: List<TypeDefinition>,
        importsBySimpleName: Map<String, List<SourceParserService.ImportRef>>,
        packageName: String?,
        path: Path,
        className: String?,
        layers: List<com.cquilez.arch.domain.Layer>
    ): List<RuleViolation> {
        return typeRefs.mapNotNull { ref ->
            val matches =
                typeDefinitions.any { type -> matchesTypeRef(ref, type, importsBySimpleName, packageName, layers) }
            if (matches) {
                RuleViolation(path.toString(), "Usage of forbidden type: ${ref.name}", ref.line, className)
            } else null
        }
    }

    private fun matchesTypeRef(
        typeRef: SourceParserService.TypeRef,
        type: TypeDefinition,
        importsBySimpleName: Map<String, List<SourceParserService.ImportRef>>,
        packageName: String?,
        layers: List<com.cquilez.arch.domain.Layer>
    ): Boolean {
        if (type.patterns.isEmpty()) return false
        if (!type.patterns.any { pattern ->
                patternMatcherService.matchesPattern(
                    typeRef.name,
                    pattern,
                    true
                )
            }) return false
        val layerLocation = type.layer?.let { layerName ->
            layerFinderService.findLayer(layers, layerName).location
        }
        return layerLocation == null || checkLayerMatch(typeRef.name, layerLocation, importsBySimpleName, packageName)
    }

    private fun matchesImport(
        imp: SourceParserService.ImportRef,
        type: TypeDefinition,
        layers: List<com.cquilez.arch.domain.Layer>
    ): Boolean {
        if (type.patterns.isEmpty()) return false
        if (!type.patterns.any { pattern ->
                patternMatcherService.matchesPattern(
                    imp.name,
                    pattern,
                    true
                )
            }) return false
        val layerLocation = type.layer?.let { layerName ->
            layerFinderService.findLayer(layers, layerName).location
        }
        return layerLocation == null || imp.name.startsWith(layerLocation)
    }

    private fun checkLayerMatch(
        typeRefName: String,
        layerLocation: String,
        importsBySimpleName: Map<String, List<SourceParserService.ImportRef>>,
        packageName: String?
    ): Boolean {
        val importMatches = importsBySimpleName[typeRefName].orEmpty()
            .any { it.name.startsWith(layerLocation) }
        return importMatches || (packageName?.startsWith(layerLocation) == true)
    }

    private fun resolveViolationClassName(parsed: SourceParserService.ParsedSource, path: Path): String? {
        val className = parsed.classNames.firstOrNull()
            ?: path.fileName?.toString()?.substringBeforeLast('.')
        if (className.isNullOrBlank()) return null
        return if (parsed.packageName.isNullOrBlank()) className else "${parsed.packageName}.$className"
    }
}
