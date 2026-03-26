package com.cquilez.arch.domain

data class MethodRestriction(val publicCount: Int?) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(map: Map<String, Any>?): MethodRestriction? {
            if (map == null) return null
            val public = map["public"]?.let {
                when (it) {
                    is Number -> it.toInt()
                    is String -> it.toIntOrNull()
                    else -> null
                }
            }
            return MethodRestriction(public)
        }
    }
}
