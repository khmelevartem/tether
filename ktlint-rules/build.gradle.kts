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
    compileOnly("com.pinterest.ktlint:ktlint-rule-engine-core:1.3.1")
    compileOnly("com.pinterest.ktlint:ktlint-cli-ruleset-core:1.3.1")
    testImplementation("com.pinterest.ktlint:ktlint-rule-engine:1.3.1")
    testImplementation("com.pinterest.ktlint:ktlint-rule-engine-core:1.3.1")
    testImplementation(kotlin("test"))
    testRuntimeOnly("org.slf4j:slf4j-simple:1.7.36")
}
