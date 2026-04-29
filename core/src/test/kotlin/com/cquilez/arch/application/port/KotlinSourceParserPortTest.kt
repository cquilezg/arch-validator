package com.cquilez.arch.application.port

import com.cquilez.arch.application.service.SourceParserService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class KotlinSourceParserPortTest {

    @Test
    fun portCanBeImplementedByAnonymousObject() {
        val source = "package com.example\nclass Foo"
        val expectedPkg = "com.example"
        val stub = KotlinSourceParserPort { content ->
            SourceParserService.ParsedSource(
                packageName = expectedPkg,
                classNames = listOf("Foo"),
                classDeclarationLines = mapOf("Foo" to 2),
                imports = emptyList(),
                publicMethodCounts = mapOf("Foo" to 0),
                typeRefs = emptyList(),
                totalLines = content.lines().size
            )
        }

        val result = stub.parse(source)

        assertNotNull(result)
        assertEquals(expectedPkg, result.packageName)
        assertEquals(listOf("Foo"), result.classNames)
        assertEquals(2, result.totalLines)
    }

    @Test
    fun portReturnsCorrectLineCount() {
        val source = "package com.test\n\nclass Bar {\n    fun hello() {}\n}\n"
        val stub = KotlinSourceParserPort { content ->
            SourceParserService.ParsedSource(
                packageName = "com.test",
                classNames = listOf("Bar"),
                classDeclarationLines = mapOf("Bar" to 3),
                imports = emptyList(),
                publicMethodCounts = mapOf("Bar" to 1),
                typeRefs = emptyList(),
                totalLines = content.lines().size
            )
        }

        val result = stub.parse(source)

        assertEquals(6, result.totalLines)
        assertEquals(1, result.publicMethodCounts["Bar"])
    }
}
