package com.cquilez.arch.domain

data class ProjectRules(
    val layers: List<Layer>,
    val types: List<TypeDefinition>,
    val rules: List<Rule>
) {
    constructor(layers: List<Layer>, rules: List<Rule>) : this(layers, emptyList(), rules)

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(map: Map<String, Any>?): ProjectRules {
            if (map == null) return ProjectRules(emptyList(), emptyList(), emptyList())
            val layersMap = map["layers"] as? Map<String, Any>
            val layers = Layer.fromMap(layersMap)
            val typesRaw = map["types"] as? List<*>
            val types = typesRaw
                ?.mapNotNull { it as? Map<String, Any> }
                ?.mapNotNull { TypeDefinition.fromMap(it) }
                ?: emptyList()
            val rulesRaw = map["rules"] as? List<*>
            val rules = rulesRaw?.mapNotNull { it as? Map<String, Any> }?.map { Rule.fromMap(it) } ?: emptyList()
            return ProjectRules(layers, types, rules)
        }
    }
}
