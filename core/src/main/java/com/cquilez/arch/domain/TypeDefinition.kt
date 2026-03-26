package com.cquilez.arch.domain

data class TypeDefinition(
    val name: String,
    val patterns: List<String>,
    val layer: String?
) {
    companion object {
        fun fromMap(map: Map<String, Any>): TypeDefinition? {
            val name = map["name"]?.toString()?.trim().orEmpty()
            if (name.isBlank()) return null
            val patterns = when (val raw = map["patterns"]) {
                is List<*> -> raw.mapNotNull { it?.toString()?.trim()?.takeIf { s -> s.isNotBlank() } }
                is String -> listOf(raw.trim()).filter { it.isNotBlank() }
                else -> emptyList()
            }
            val layer = map["layer"]?.toString()?.trim()?.takeIf { it.isNotBlank() }
            return TypeDefinition(name, patterns, layer)
        }
    }
}
