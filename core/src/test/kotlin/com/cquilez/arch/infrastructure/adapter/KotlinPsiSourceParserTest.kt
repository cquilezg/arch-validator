package com.cquilez.arch.infrastructure.adapter

import com.cquilez.arch.application.service.SourceParserService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KotlinPsiSourceParserTest {

    private val parser = KotlinPsiSourceParser()

    // ──────────────────────────────────────────────
    // Happy path: basic class with one public method
    // ──────────────────────────────────────────────

    @Test
    fun extractsPackageName() {
        val source = """
            package com.example.domain
            
            class Order
        """.trimIndent()

        val result: SourceParserService.ParsedSource = parser.parse(source)

        assertEquals("com.example.domain", result.packageName)
    }

    @Test
    fun extractsImports() {
        val source = """
            package com.example
            
            import java.util.List
            import java.io.File
            
            class Foo
        """.trimIndent()

        val result = parser.parse(source)

        assertEquals(2, result.imports.size)
        assertEquals("java.util.List", result.imports[0].name)
        assertEquals("java.io.File", result.imports[1].name)
    }

    @Test
    fun extractsClassName() {
        val source = """
            package com.example
            
            class MyService
        """.trimIndent()

        val result = parser.parse(source)

        assertEquals(listOf("MyService"), result.classNames)
    }

    @Test
    fun countsPublicMethodsCorrectly() {
        val source = """
            package com.example
            
            class OrderService {
                fun create() {}
                fun update() {}
                private fun doInternal() {}
            }
        """.trimIndent()

        val result = parser.parse(source)

        assertEquals(2, result.publicMethodCounts["OrderService"])
    }

    @Test
    fun countsTotalLines() {
        val source = "package com.example\n\nclass Foo {\n    fun bar() {}\n}"

        val result = parser.parse(source)

        assertEquals(5, result.totalLines)
    }

    // ──────────────────────────────────────────────
    // Triangulation: different visibility modifiers,
    // nested class, interface, no package
    // ──────────────────────────────────────────────

    @Test
    fun overrideMethodCountsAsPublic() {
        val source = """
            package com.example
            
            open class Base {
                open fun doSomething() {}
            }
            
            class Child : Base() {
                override fun doSomething() {}
            }
        """.trimIndent()

        val result = parser.parse(source)

        assertEquals(1, result.publicMethodCounts["Child"])
        assertEquals(1, result.publicMethodCounts["Base"])
    }

    @Test
    fun internalAndProtectedMethodsAreNotPublic() {
        val source = """
            package com.example
            
            class MyClass {
                internal fun internalFun() {}
                protected fun protectedFun() {}
                fun publicFun() {}
            }
        """.trimIndent()

        val result = parser.parse(source)

        assertEquals(1, result.publicMethodCounts["MyClass"])
    }

    @Test
    fun handlesInterfaceDeclaration() {
        val source = """
            package com.example
            
            interface MyPort {
                fun execute(): String
            }
        """.trimIndent()

        val result = parser.parse(source)

        assertEquals(listOf("MyPort"), result.classNames)
    }

    @Test
    fun returnsNullPackageWhenMissing() {
        val source = "class Standalone"

        val result = parser.parse(source)

        assertNull(result.packageName)
        assertEquals(listOf("Standalone"), result.classNames)
    }

    @Test
    fun extractsImportLineNumbers() {
        val source = "package com.example\n\nimport java.util.List\n\nclass Foo"

        val result = parser.parse(source)

        assertEquals(1, result.imports.size)
        assertEquals(3, result.imports[0].line)
    }

    // ──────────────────────────────────────────────
    // SP-08: Error Handling - malformed Kotlin input
    // ──────────────────────────────────────────────

    @Test
    fun handlesMalformedKotlinInputGracefully() {
        val source = "package com.example\n\nclass Foo { broken syntax here"

        val result = parser.parse(source)

        // Should not throw an exception
        // PSI parser may still extract what it can
        assertEquals("com.example", result.packageName)
        assertTrue(result.classNames.isNotEmpty())
    }

    // ──────────────────────────────────────────────
    // Additional triangulation: object declarations
    // ──────────────────────────────────────────────

    @Test
    fun handlesObjectDeclarations() {
        val source = """
            package com.example

            object MySingleton {
                fun doWork() {}
            }
        """.trimIndent()

        val result = parser.parse(source)

        assertEquals(listOf("MySingleton"), result.classNames)
        assertEquals(1, result.publicMethodCounts["MySingleton"])
    }
}
