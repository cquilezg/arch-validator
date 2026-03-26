package com.cquilez.arch.domain

data class Allowed(
    val imports: List<String>?,
    val allowedLayers: String?,
    val methods: MethodRestriction?,
    val maxLines: Int? = null
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(map: Map<String, Any>?): Allowed? {
            if (map == null) return null
            val imports = map["imports"]?.let {
                when (it) {
                    is List<*> -> it.mapNotNull { e ->
                        e?.toString()?.trim()?.takeIf { s -> s.isNotBlank() }
                    }
                    else -> null
                }
            }
            val layers = map["layers"]?.toString()
            val methodsMap = map["methods"] as? Map<String, Any>
            val methods = MethodRestriction.fromMap(methodsMap)
            val maxLines = map["max-lines"]?.toString()?.toIntOrNull()
            return Allowed(imports, layers, methods, maxLines)
        }
    }
}
