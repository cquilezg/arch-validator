package com.cquilez.arch.application.service.rule

import com.cquilez.arch.application.service.PatternMatcherService
import com.cquilez.arch.application.service.SourceParserService
import com.cquilez.arch.domain.Rule
import com.cquilez.arch.domain.RuleViolation
import java.nio.file.Path

class PublicMethodCountValidator(
    patternMatcherService: PatternMatcherService
) : AbstractRuleValidator(patternMatcherService) {

    override fun validate(
        rule: Rule,
        parsed: SourceParserService.ParsedSource,
        path: Path
    ): List<RuleViolation> {
        val expected = rule.allowed?.methods?.publicCount ?: return emptyList()
        val classPatterns = rule.classPatterns
        val targetClasses = if (classPatterns.isNullOrEmpty()) {
            parsed.classNames
        } else {
            parsed.classNames.filter { name ->
                classPatterns.any { pattern -> patternMatcherService.matchesPattern(name, pattern, true) }
            }
        }
        if (targetClasses.isEmpty()) return emptyList()
        val violating = targetClasses.filter { cls ->
            val count = parsed.publicMethodCounts[cls] ?: 0
            count != expected
        }
        if (violating.isEmpty()) return emptyList()
        return violating.map { cls ->
            val fqcn = resolveQualifiedClassName(parsed, cls)
            RuleViolation(
                path.toString(),
                "Public method count mismatch",
                parsed.classDeclarationLines[cls],
                fqcn
            )
        }
    }

    private fun resolveQualifiedClassName(parsed: SourceParserService.ParsedSource, className: String): String {
        return if (parsed.packageName.isNullOrBlank()) className else "${parsed.packageName}.$className"
    }
}
