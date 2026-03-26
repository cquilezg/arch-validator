package com.cquilez.arch.domain.exception

class EvaluationFailedException(message: String?) : RuntimeException(message) {
    companion object {
        private const val serialVersionUID = 338490361918732921L
    }
}
