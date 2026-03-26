package com.cquilez.arch.domain

data class ProjectAnalysis(
    var totalClasses: Int,
    val violations: LinkedHashMap<Rule, MutableList<RuleViolation>>,
    val typeCounts: LinkedHashMap<String, Int>
) {
    fun addViolation(rule: Rule, violation: RuleViolation) {
        val bucket = violations.getOrPut(rule) { mutableListOf() }
        bucket.add(violation)
    }

    fun addTypeMatch(typeName: String) {
        typeCounts[typeName] = (typeCounts[typeName] ?: 0) + 1
    }

    fun totalViolations(): Int = violations.values.sumOf { it.size }
}
