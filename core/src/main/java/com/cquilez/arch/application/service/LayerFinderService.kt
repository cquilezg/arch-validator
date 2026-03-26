package com.cquilez.arch.application.service

import com.cquilez.arch.domain.Layer
import com.cquilez.arch.domain.exception.ResourceNotFoundException

class LayerFinderService {
    fun findLayer(layer: List<Layer>, layerName: String): Layer {
        return layer.find { it.name == layerName }
            ?: throw ResourceNotFoundException("Layer not found: $layerName")
    }
}