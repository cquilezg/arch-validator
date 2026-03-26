package com.cquilez.arch.application.service.rule

import com.cquilez.arch.application.service.PatternMatcherService
import com.cquilez.arch.application.service.SourceParserService
import com.cquilez.arch.domain.Rule
import com.cquilez.arch.domain.RuleViolation
import java.nio.file.Path

class ForbiddenClassValidator(
    patternMatcherService: PatternMatcherService
) : AbstractRuleValidator(patternMatcherService) {

    override fun validate(
        rule: Rule,
        parsed: SourceParserService.ParsedSource,
        path: Path
    ): List<RuleViolation> {
        val forbidden = rule.forbidden?.classPatterns ?: return emptyList()
        val classNameMatches = parsed.classNames.filter { name ->
            forbidden.any { pattern -> patternMatcherService.matchesPattern(name, pattern, true) }
        }
        if (classNameMatches.isEmpty()) return emptyList()
        return classNameMatches.map { name ->
            val fqcn = resolveQualifiedClassName(parsed, name)
            RuleViolation(
                path.toString(),
                "Forbidden class pattern matched",
                parsed.classDeclarationLines[name],
                fqcn
            )
        }
    }

    private fun resolveQualifiedClassName(parsed: SourceParserService.ParsedSource, className: String): String {
        return if (parsed.packageName.isNullOrBlank()) className else "${parsed.packageName}.$className"
    }
}
