package com.cquilez.arch.application.port

fun interface ParserPort {
    fun parse(content: String): Map<String, Any>
}
