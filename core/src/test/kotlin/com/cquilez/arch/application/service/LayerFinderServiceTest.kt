package com.cquilez.arch.application.service

import com.cquilez.arch.domain.Layer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LayerFinderServiceTest {

    // ──────────────────────────────────────────────
    // Layer resolution from config
    // ──────────────────────────────────────────────

    @Test
    fun findsLayerByName() {
        val service = LayerFinderService()

        val layers = listOf(
            Layer("domain", "com.example.domain"),
            Layer("application", "com.example.application"),
            Layer("infrastructure", "com.example.infrastructure")
        )

        val result = service.findLayer(layers, "application")

        assertEquals("application", result.name)
        assertEquals("com.example.application", result.location)
    }

    @Test
    fun findsFirstLayerWhenDuplicateNames() {
        val service = LayerFinderService()

        val layers = listOf(
            Layer("domain", "com.example.domain"),
            Layer("application", "com.example.application"),
            Layer("application", "com.example.application2") // Duplicate name
        )

        val result = service.findLayer(layers, "application")

        // Should return the first match
        assertEquals("application", result.name)
        assertEquals("com.example.application", result.location)
    }

    @Test
    fun throwsExceptionWhenLayerNotFound() {
        val service = LayerFinderService()

        val layers = listOf(
            Layer("domain", "com.example.domain"),
            Layer("application", "com.example.application")
        )

        try {
            service.findLayer(layers, "unknown")
            assert(false) { "Expected ResourceNotFoundException to be thrown" }
        } catch (e: com.cquilez.arch.domain.exception.ResourceNotFoundException) {
            assertEquals("Layer not found: unknown", e.message)
        }
    }

    @Test
    fun returnsNullWhenLayersListIsEmpty() {
        val service = LayerFinderService()

        val layers = emptyList<Layer>()

        try {
            service.findLayer(layers, "domain")
            assert(false) { "Expected ResourceNotFoundException to be thrown" }
        } catch (e: com.cquilez.arch.domain.exception.ResourceNotFoundException) {
            assertEquals("Layer not found: domain", e.message)
        }
    }

    @Test
    fun findsLayerWithEmptyLocation() {
        val service = LayerFinderService()

        val layers = listOf(
            Layer("domain", ""),
            Layer("application", "com.example.application")
        )

        val result = service.findLayer(layers, "domain")

        assertEquals("domain", result.name)
        assertEquals("", result.location)
    }

    @Test
    fun findsLayerCaseSensitive() {
        val service = LayerFinderService()

        val layers = listOf(
            Layer("Domain", "com.example.domain"),
            Layer("domain", "com.example.domain2")
        )

        val result = service.findLayer(layers, "domain")

        assertEquals("domain", result.name)
        assertEquals("com.example.domain2", result.location)
    }

    @Test
    fun throwsExceptionForDifferentCase() {
        val service = LayerFinderService()

        val layers = listOf(
            Layer("Domain", "com.example.domain")
        )

        try {
            service.findLayer(layers, "domain")
            assert(false) { "Expected ResourceNotFoundException to be thrown" }
        } catch (e: com.cquilez.arch.domain.exception.ResourceNotFoundException) {
            assertEquals("Layer not found: domain", e.message)
        }
    }
}
