package com.tubetoast.tether.ktlint

import com.pinterest.ktlint.cli.ruleset.core.api.RuleSetProviderV3
import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import com.pinterest.ktlint.rule.engine.core.api.RuleSetId

class TetherRuleSetProvider : RuleSetProviderV3(RuleSetId("tether")) {
    override fun getRuleProviders(): Set<RuleProvider> = setOf(
        RuleProvider { NoRunBlockingInTestsRule() },
    )
}
