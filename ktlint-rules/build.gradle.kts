import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
}

java {
    targetCompatibility = JavaVersion.VERSION_17
    sourceCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    compileOnly(libs.ktlint.rule.engine.core)
    compileOnly(libs.ktlint.cli.ruleset.core)
    testImplementation(libs.ktlint.rule.engine)
    testImplementation(libs.ktlint.rule.engine.core)
    testImplementation(kotlin("test"))
    testRuntimeOnly(libs.slf4j.simple)
}
