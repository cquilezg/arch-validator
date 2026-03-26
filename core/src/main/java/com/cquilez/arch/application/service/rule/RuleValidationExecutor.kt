package com.cquilez.arch.application.service.rule

import com.cquilez.arch.application.service.LayerFinderService
import com.cquilez.arch.application.service.PatternMatcherService
import com.cquilez.arch.application.service.SourceParserService
import com.cquilez.arch.domain.ProjectRules
import com.cquilez.arch.domain.Rule
import com.cquilez.arch.domain.RuleViolation
import java.nio.file.Path

class RuleValidationExecutor(
    private val patternMatcherService: PatternMatcherService,
    private val layerFinderService: LayerFinderService
) {
    private val forbiddenClassValidator = ForbiddenClassValidator(patternMatcherService)
    private val forbiddenTypeValidator = ForbiddenTypeValidator(patternMatcherService, layerFinderService)
    private val importPatternValidator = ImportPatternValidator(patternMatcherService, layerFinderService)
    private val layerImportValidator = LayerImportValidator(patternMatcherService, layerFinderService)
    private val publicMethodCountValidator = PublicMethodCountValidator(patternMatcherService)
    private val maxLinesValidator = MaxLinesValidator(patternMatcherService)

    fun validate(
        projectRules: ProjectRules,
        rule: Rule,
        parsed: SourceParserService.ParsedSource,
        path: Path
    ): List<RuleViolation> {
        val violations = mutableListOf<RuleViolation>()

        violations.addAll(forbiddenClassValidator.validate(rule, parsed, path))
        violations.addAll(forbiddenTypeValidator.validate(projectRules, rule, parsed, path))
        violations.addAll(importPatternValidator.validate(projectRules, rule, parsed, path))
        violations.addAll(layerImportValidator.validate(projectRules, rule, parsed, path))
        violations.addAll(publicMethodCountValidator.validate(rule, parsed, path))
        violations.addAll(maxLinesValidator.validate(rule, parsed, path))

        return violations
    }
}
