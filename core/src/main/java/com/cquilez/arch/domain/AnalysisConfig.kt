package com.cquilez.arch.domain

/**
 * Configuration for architecture analysis.
 *
 * @param failIfNoRules when true, the build fails if no rules file is found; when false, a warning is logged
 * @param includeTests when true, test source roots are included in validation; when false, only main sources are used
 */
data class AnalysisConfig(
    val failIfNoRules: Boolean = false,
    val includeTests: Boolean = false
)
