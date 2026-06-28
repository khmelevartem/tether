plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeHotReload) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinxSerialization) apply false
    alias(libs.plugins.ktlintGradle) apply false
}

subprojects {
    // :ktlint-rules provides the ruleset consumed by ktlintRuleset below — applying ktlint
    // to it would create a circular dependency where the rule-engine tries to lint the rules
    // before they are compiled.
    if (name == "ktlint-rules") return@subprojects

    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    val composeRulesCoordinates = rootProject.libs.compose.rules.ktlint.get().let {
        "${it.module.group}:${it.module.name}:${it.versionConstraint.requiredVersion}"
    }
    dependencies {
        "ktlintRuleset"(composeRulesCoordinates)
        "ktlintRuleset"(project(":ktlint-rules"))
    }

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        android.set(true)
        outputToConsole.set(true)
        filter {
            // invariantSeparatorsPath so the match holds on Windows, where File.path uses backslashes.
            exclude { it.file.invariantSeparatorsPath.contains("/build/") }
        }
    }
}

val skillTests by tasks.registering(Exec::class) {
    group = "verification"
    description = "Run every .claude/skills/**/*.test.sh shell test."
    workingDir = rootDir
    commandLine("bash", ".claude/scripts/run-skill-tests.sh")
}

tasks.register("allTests") {
    group = "verification"
    description = "Run every project test."
    dependsOn(skillTests)
    subprojects {
        dependsOn(tasks.matching { it.name == "allTests" })
    }
}
