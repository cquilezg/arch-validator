package com.cquilez.arch.application.service

class PatternMatcherService {
    fun matchesPattern(value: String, pattern: String, ignoreCase: Boolean): Boolean {
    val regexBody = pattern
        .split("*")
        .joinToString(".*") { Regex.escape(it) }
    val regex = Regex(
        "^$regexBody$",
        if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
    )
    return regex.matches(value)
}
}
