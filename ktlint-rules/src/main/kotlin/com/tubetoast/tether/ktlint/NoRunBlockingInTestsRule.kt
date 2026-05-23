package com.tubetoast.tether.ktlint

import com.pinterest.ktlint.rule.engine.core.api.ElementType.CALL_EXPRESSION
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import org.jetbrains.kotlin.com.intellij.lang.ASTNode

class NoRunBlockingInTestsRule : Rule(RuleId("tether:no-run-blocking-in-tests"), About()) {
    private val testPathRegex = Regex(".*/src/[^/]*[Tt]est[^/]*/.*\\.kt$")

    override fun beforeVisitChildNodes(
        node: ASTNode,
        autoCorrect: Boolean,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> Unit,
    ) {
        if (node.elementType != CALL_EXPRESSION) return
        val filePath = node.psi.containingFile?.virtualFile?.path ?: return
        if (!testPathRegex.containsMatchIn(filePath)) return
        val calleeName = node.firstChildNode?.text ?: return
        if (calleeName == "runBlocking") {
            emit(
                node.startOffset,
                "runBlocking запрещён в тестах (testing.md§Стиль) — используй runTest + TestDispatcher. " +
                    "Suppress с обоснованием для integration-тестов на реальных потоках.",
                false,
            )
        }
    }
}
