package com.cquilez.arch.domain

data class RuleViolation(
    val path: String,
    val cause: String,
    val line: Int? = null,
    val className: String? = null
)
