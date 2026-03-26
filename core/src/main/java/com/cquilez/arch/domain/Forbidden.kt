package com.cquilez.arch.domain

data class Forbidden(
    val classPatterns: List<String>,
    val layers: String?,
    val types: List<String>
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(map: Map<String, Any>?): Forbidden? {
            if (map == null) return null
            val classes = map["classes"]?.let {
                when (it) {
                    is List<*> -> it.mapNotNull { e -> e?.toString() }
                    else -> emptyList()
                }
            } ?: emptyList()
            val layers = map["layers"]?.toString()
            val types = map["types"]?.let {
                when (it) {
                    is List<*> -> it.mapNotNull { e -> e?.toString()?.trim()?.takeIf { s -> s.isNotBlank() } }
                    is String -> listOf(it.trim()).filter { s -> s.isNotBlank() }
                    else -> emptyList()
                }
            } ?: emptyList()
            return if (classes.isEmpty() && layers.isNullOrBlank() && types.isEmpty()) null else Forbidden(classes, layers, types)
        }
    }
}
