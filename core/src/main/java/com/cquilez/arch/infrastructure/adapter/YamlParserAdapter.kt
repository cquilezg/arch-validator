package com.cquilez.arch.infrastructure.adapter

import com.cquilez.arch.application.port.ParserPort
import org.yaml.snakeyaml.Yaml

class YamlParserAdapter : ParserPort {
    @Suppress("UNCHECKED_CAST")
    override fun parse(content: String): Map<String, Any> {
        return try {
            (Yaml().load(content) as? Map<String, Any>) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
