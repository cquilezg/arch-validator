package com.cquilez.arch.application.service

import com.cquilez.arch.application.port.FilesystemPort
import com.cquilez.arch.domain.ProjectRules
import java.nio.file.Paths
class RuleValidatorService(
    private val filesystem: FilesystemPort
) {

    fun validateLayers(sourceRoots: List<String>, projectRules: ProjectRules) {
        val missingLayers = mutableListOf<String>()

        projectRules.layers.forEach { layer ->
            if (layer.location.isNotBlank()) {
                // Convert package path (e.g., "com.example.domain") to directory path (e.g., "com/example/domain")
                val packageDirPath = layer.location.replace('.', '/')

                val packageExists = sourceRoots.any { rootStr ->
                    val rootPath = Paths.get(rootStr)
                    if (!filesystem.exists(rootPath)) return@any false

                    val packagePath = rootPath.resolve(packageDirPath)
                    filesystem.exists(packagePath) && filesystem.isDirectory(packagePath)
                }

                if (!packageExists) {
                    missingLayers.add("Layer '${layer.name}' references package '${layer.location}' but no directory found in source roots")
                }
            }
        }

        check(missingLayers.isEmpty()) {
            "Invalid layer configuration. The following layers reference packages that do not exist:\n" +
                    missingLayers.joinToString("\n")
        }
    }

    fun validateRules(projectRules: ProjectRules) {
        val knownLayers = projectRules.layers.map { it.name }.toSet()
        val knownTypes = projectRules.types.map { it.name }.toSet()
        val missing = mutableListOf<String>()

        projectRules.rules.forEach { rule ->
            if (rule.layers.isNotEmpty()) {
                rule.layers.filter { it !in knownLayers }.forEach { layerName ->
                    missing.add("Rule '${rule.title}' references unknown layer '$layerName' in 'layer'")
                }
            }

            val allowedLayersRaw = rule.allowed?.allowedLayers
            if (!allowedLayersRaw.isNullOrBlank()) {
                splitLayerNames(allowedLayersRaw).filter { it !in knownLayers }.forEach { layerName ->
                    missing.add("Rule '${rule.title}' references unknown layer '$layerName' in 'allowed.layers'")
                }
            }

            val forbiddenLayersRaw = rule.forbidden?.layers
            if (!forbiddenLayersRaw.isNullOrBlank()) {
                splitLayerNames(forbiddenLayersRaw).filter { it !in knownLayers }.forEach { layerName ->
                    missing.add("Rule '${rule.title}' references unknown layer '$layerName' in 'forbidden.layers'")
                }
            }

            if (rule.types.isNotEmpty()) {
                rule.types.filter { it !in knownTypes }.forEach { typeName ->
                    missing.add("Rule '${rule.title}' references unknown type '$typeName' in 'types'")
                }
            }

            val forbiddenTypes = rule.forbidden?.types ?: emptyList()
            if (forbiddenTypes.isNotEmpty()) {
                forbiddenTypes.filter { it !in knownTypes }.forEach { typeName ->
                    missing.add("Rule '${rule.title}' references unknown type '$typeName' in 'forbidden.types'")
                }
            }
        }

        check(missing.isEmpty()) {
            "Invalid rule configuration. The following rules reference layers that do not exist:\n" +
                    missing.joinToString("\n")
        }
    }

    private fun splitLayerNames(raw: String): Set<String> {
        val normalized = raw.trim().removePrefix("[").removeSuffix("]")
        if (normalized.isBlank()) return emptySet()
        return normalized.split(',').map { it.trim() }.filter { it.isNotBlank() }.toSet()
    }
}
