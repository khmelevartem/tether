plugins {
    kotlin("multiplatform") version "2.3.20"
}

kotlin {
    jvm {
        mainRun {
            mainClass = "com.tubetoast.poc.JvmPocKt"
        }
    }
    macosArm64 {
        binaries {
            executable {
                entryPoint = "com.tubetoast.poc.main"
                baseName = "macos-poc"
            }
        }
    }
    iosSimulatorArm64()

    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation("io.ktor:ktor-network-tls-certificates:3.1.3")
                implementation("io.ktor:ktor-network:3.1.3")
                implementation("io.ktor:ktor-network-tls:3.1.3")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
            }
        }
        val macosArm64Main by getting
        val iosSimulatorArm64Test by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

tasks.register<Jar>("fatJar") {
    archiveBaseName.set("poc-fat")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "com.tubetoast.poc.JvmPocKt"
    }
    from(configurations.named("jvmRuntimeClasspath").get().map { if (it.isDirectory) it else zipTree(it) })
    from(layout.buildDirectory.dir("classes/kotlin/jvm/main"))
    from(layout.buildDirectory.dir("processedResources/jvm/main"))
    dependsOn(tasks.named("jvmMainClasses"))
}

// Disable ktlint for this POC module
configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
    filter {
        exclude { true }
    }
}
