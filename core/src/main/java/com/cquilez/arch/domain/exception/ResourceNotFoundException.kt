package com.cquilez.arch.domain.exception

class ResourceNotFoundException(message: String) : RuntimeException(message) {
    companion object {
        private const val serialVersionUID: Long = -4479782311941752598L
    }
}