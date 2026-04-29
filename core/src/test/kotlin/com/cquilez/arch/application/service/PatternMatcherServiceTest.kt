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

    // ──────────────────────────────────────────────
    // Additional glob wildcard scenarios
    // ──────────────────────────────────────────────

    @Test
    fun matchesEmptyPattern() {
        // Empty pattern becomes regex "^$" which only matches empty string
        assertTrue(matcher.matchesPattern("", "", false))
        assertFalse(matcher.matchesPattern("abc", "", false))
    }

    @Test
    fun matchesSingleCharacterWildcard() {
        // Single * should match single characters
        assertTrue(matcher.matchesPattern("a", "*", false))
        assertTrue(matcher.matchesPattern("X", "*", false))
        assertTrue(matcher.matchesPattern("z", "*", false))
    }

    @Test
    fun matchesDoubleStarGlob() {
        // ** should match across segments
        assertTrue(matcher.matchesPattern("com.example.domain", "com.**", false))
        assertTrue(matcher.matchesPattern("a.b.c.d", "a.**", false))
        assertTrue(matcher.matchesPattern("single", "**", false))
    }

    @Test
    fun matchesExactString() {
        // No wildcards - exact match
        assertTrue(matcher.matchesPattern("ExactMatch", "ExactMatch", false))
        assertFalse(matcher.matchesPattern("ExactMatch", "exactmatch", false))
        assertFalse(matcher.matchesPattern("SomethingElse", "ExactMatch", false))
    }

    @Test
    fun matchesCaseSensitiveByDefault() {
        // Case sensitive matching (default)
        assertTrue(matcher.matchesPattern("MyClass", "MyClass", false))
        assertFalse(matcher.matchesPattern("myclass", "MyClass", false))
        assertFalse(matcher.matchesPattern("MYCLASS", "MyClass", false))
    }

    @Test
    fun matchesMultipleWildcards() {
        // Multiple * in pattern
        assertTrue(matcher.matchesPattern("com.example.Service", "com.*.Service", false))
        assertTrue(matcher.matchesPattern("org.apache.Util", "org.*.Util", false))
        assertFalse(matcher.matchesPattern("com.example.Other", "com.*.Service", false))
    }

    @Test
    fun matchesPatternWithWildcardAtStart() {
        assertTrue(matcher.matchesPattern("DataService", "*Service", false))
        assertTrue(matcher.matchesPattern("TestService", "*Service", false))
        assertFalse(matcher.matchesPattern("ServiceTest", "*Service", false))
    }

    @Test
    fun matchesPatternWithWildcardAtEnd() {
        assertTrue(matcher.matchesPattern("com.example", "com.*", false))
        assertTrue(matcher.matchesPattern("org.apache", "org.*", false))
        assertFalse(matcher.matchesPattern("com", "com.*", false))
    }

    @Test
    fun matchesPatternWithWildcardsEverywhere() {
        assertTrue(matcher.matchesPattern("a.b.c", "*.b.*", false))
        assertTrue(matcher.matchesPattern("x.y.z", "*.y.*", false))
        assertFalse(matcher.matchesPattern("a.x.c", "*.b.*", false))
    }
}
