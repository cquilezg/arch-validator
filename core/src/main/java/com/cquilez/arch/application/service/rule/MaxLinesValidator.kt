package com.cquilez.arch.application.service.rule

import com.cquilez.arch.application.service.PatternMatcherService
import com.cquilez.arch.application.service.SourceParserService
import com.cquilez.arch.domain.Rule
import com.cquilez.arch.domain.RuleViolation
import java.nio.file.Path

class MaxLinesValidator(
    patternMatcherService: PatternMatcherService
) : AbstractRuleValidator(patternMatcherService) {

    override fun validate(
        rule: Rule,
        parsed: SourceParserService.ParsedSource,
        path: Path
    ): List<RuleViolation> {
        val maxLines = rule.allowed?.maxLines ?: return emptyList()
        if (parsed.totalLines <= maxLines) return emptyList()

        val className = resolveViolationClassName(parsed, path)
        return listOf(
            RuleViolation(
                path.toString(),
                "File has too many lines: ${parsed.totalLines} (max: $maxLines)",
                null,
                className
            )
        )
    }

    private fun resolveViolationClassName(parsed: SourceParserService.ParsedSource, path: Path): String? {
        val className = parsed.classNames.firstOrNull()
            ?: path.fileName?.toString()?.substringBeforeLast('.')
        if (className.isNullOrBlank()) return null
        return if (parsed.packageName.isNullOrBlank()) className else "${parsed.packageName}.$className"
    }
}
