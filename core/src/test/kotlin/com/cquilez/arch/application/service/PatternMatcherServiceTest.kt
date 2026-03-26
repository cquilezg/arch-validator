package com.cquilez.arch.application.service

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PatternMatcherServiceTest {
    private val matcher = PatternMatcherService()

    @Test
    fun matchesWildcardPatterns() {
        assertTrue(matcher.matchesPattern("lombok.Data", "lombok.*", false))
        assertTrue(matcher.matchesPattern("MyClass", "*Class", false))
    }

    @Test
    fun matchesCaseInsensitiveWhenRequested() {
        assertTrue(matcher.matchesPattern("abc", "ABC", true))
        assertFalse(matcher.matchesPattern("abc", "ABC", false))
    }
}
