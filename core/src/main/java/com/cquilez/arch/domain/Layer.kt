package com.cquilez.arch.domain

data class Layer(val name: String, val location: String) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(layersMap: Map<String, Any>?): List<Layer> {
            if (layersMap == null) return emptyList()
            return layersMap.map { (name, value) ->
                val loc = (value as? Map<String, Any>)?.get("location")?.toString() ?: ""
                Layer(name, loc)
            }
        }
    }


}