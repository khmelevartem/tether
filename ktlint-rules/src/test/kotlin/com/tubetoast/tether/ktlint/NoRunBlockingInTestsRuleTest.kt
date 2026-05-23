package com.tubetoast.tether.ktlint

import com.pinterest.ktlint.rule.engine.api.Code
import com.pinterest.ktlint.rule.engine.api.KtLintRuleEngine
import com.pinterest.ktlint.rule.engine.api.LintError
import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NoRunBlockingInTestsRuleTest {
    private val engine = KtLintRuleEngine(
        ruleProviders = setOf(RuleProvider { NoRunBlockingInTestsRule() }),
    )

    private fun lint(snippet: String, filePath: Path): List<LintError> {
        val errors = mutableListOf<LintError>()
        engine.lint(Code.fromSnippetWithPath(snippet, filePath)) { errors.add(it) }
        return errors
    }

    @Test
    fun `runBlocking in test file produces error`() {
        val errors = lint(
            snippet = "fun test() { runBlocking { } }",
            filePath = Path.of("composeApp/src/desktopTest/kotlin/Foo.kt"),
        )
        assertEquals(1, errors.size)
        assertTrue(errors.first().detail.contains("runTest"))
    }

    @Test
    fun `runBlocking in main source file produces no error`() {
        val errors = lint(
            snippet = "fun main() { runBlocking { } }",
            filePath = Path.of("composeApp/src/desktopMain/kotlin/Foo.kt"),
        )
        assertEquals(0, errors.size)
    }

    @Test
    fun `runBlocking in middle of function body produces error`() {
        val errors = lint(
            snippet = """
                fun test() {
                    val x = 1
                    runBlocking { }
                }
            """.trimIndent(),
            filePath = Path.of("composeApp/src/desktopTest/kotlin/Foo.kt"),
        )
        assertEquals(1, errors.size)
    }

    @Test
    fun `two runBlocking calls in one function produce two errors`() {
        val errors = lint(
            snippet = """
                fun test() {
                    runBlocking { }
                    runBlocking { }
                }
            """.trimIndent(),
            filePath = Path.of("composeApp/src/desktopTest/kotlin/Foo.kt"),
        )
        assertEquals(2, errors.size)
    }

    @Test
    fun `suppressed runBlocking in test file produces no error`() {
        val errors = lint(
            snippet = """
                @Suppress("ktlint:tether:no-run-blocking-in-tests")
                fun test() { runBlocking { } }
            """.trimIndent(),
            filePath = Path.of("composeApp/src/desktopTest/kotlin/Foo.kt"),
        )
        assertEquals(0, errors.size)
    }
}
