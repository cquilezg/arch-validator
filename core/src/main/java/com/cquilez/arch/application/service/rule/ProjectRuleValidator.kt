package com.cquilez.arch.application.service.rule

import com.cquilez.arch.application.service.PatternMatcherService
import com.cquilez.arch.application.service.SourceParserService
import com.cquilez.arch.domain.ProjectRules
import com.cquilez.arch.domain.Rule
import com.cquilez.arch.domain.RuleViolation
import java.nio.file.Path

abstract class ProjectRuleValidator(
    protected val patternMatcherService: PatternMatcherService
) {
    abstract fun validate(
        projectRules: ProjectRules,
        rule: Rule,
        parsed: SourceParserService.ParsedSource,
        path: Path
    ): List<RuleViolation>
}
