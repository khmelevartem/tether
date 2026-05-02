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

// Typed catalog accessors are only available at root build script scope.
val composeRulesCoordinates = libs.compose.rules.ktlint.get().let {
    "${it.module.group}:${it.module.name}:${it.versionConstraint.requiredVersion}"
}

allprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    dependencies {
        "ktlintRuleset"(composeRulesCoordinates)
    }

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        android.set(true)
        outputToConsole.set(true)
        filter {
            exclude { it.file.path.contains("/build/") }
        }
    }
}
