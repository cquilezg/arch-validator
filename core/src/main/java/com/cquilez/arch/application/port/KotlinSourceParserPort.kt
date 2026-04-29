package com.cquilez.arch.application.port

import com.cquilez.arch.application.service.SourceParserService

fun interface KotlinSourceParserPort {
    fun parse(content: String): SourceParserService.ParsedSource
}
