package com.cquilez.arch.domain

data class Rule(
    val title: String,
    val layers: List<String>,
    val types: List<String>,
    val classPatterns: List<String>?,
    val allowed: Allowed?,
    val forbidden: Forbidden?
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(map: Map<String, Any>): Rule {
            val title = map["title"]?.toString() ?: ""
            val layers = when (val layerRaw = map["layers"]) {
                is String -> listOf(layerRaw.trim())
                is List<*> -> layerRaw.mapNotNull { it?.toString()?.trim()?.takeIf { s -> s.isNotBlank() } }
                else -> emptyList()
            }
            val types = when (val typeRaw = map["types"]) {
                is String -> listOf(typeRaw.trim())
                is List<*> -> typeRaw.mapNotNull { it?.toString()?.trim()?.takeIf { s -> s.isNotBlank() } }
                else -> emptyList()
            }
            val classPatterns = when (val classRaw = map["classes"]) {
                is List<*> -> classRaw.mapNotNull { it?.toString()?.trim()?.takeIf { s -> s.isNotBlank() } }
                else -> null
            }
            val allowed = Allowed.fromMap(map["allowed"] as? Map<String, Any>)
            val forbidden = Forbidden.fromMap(map["forbidden"] as? Map<String, Any>)
            return Rule(title, layers, types, classPatterns, allowed, forbidden)
        }
    }


}
