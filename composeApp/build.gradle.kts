import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlinxSerialization)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    // Custom hierarchy template to create jvmMain as an intermediate source set
    // shared by androidMain and desktopMain. Without this, jvm("desktop") and
    // androidTarget() would each connect directly to commonMain with no shared JVM layer.
    //
    // Result:
    //   commonMain
    //   ├── jvmMain          ← shared JVM code (FileServer, FileClientJvm, future shared logic)
    //   │   ├── androidMain  ← Android-specific code
    //   │   └── desktopMain  ← Desktop JVM code (mDNS, Clikt CLI, Compose Desktop)
    //   └── nativeMain
    //       └── appleMain    ← Apple-specific code (NSNetService mDNS)
    //           ├── iosMain
    //           └── macosMain
    applyHierarchyTemplate {
        common {
            group("jvm") {
                withAndroidTarget()
                withJvm()
            }
            group("native") {
                group("apple") {
                    group("macos") {
                        withMacosArm64()
                    }
                    group("ios") {
                        withIosArm64()
                        withIosSimulatorArm64()
                    }
                }
            }
        }
    }

    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    // macosArm64 covers all Apple Silicon Macs (2020+).
    // macosX64 is deprecated in Kotlin 2.3 — add it back if Intel Mac support is needed.
    macosArm64()

    // jvm("desktop") creates desktopMain as the leaf source set for the Desktop JVM target.
    // Combined with the custom hierarchy above, jvmMain becomes the intermediate parent
    // for both androidMain and desktopMain.
    jvm("desktop")

    sourceSets {
        // The custom hierarchy above creates:
        //   appleMain (iosMain + macosMain) → nativeMain → commonMain
        //   jvmMain (androidMain + desktopMain) → commonMain
        // src/appleMain/ and src/jvmMain/ are picked up by their respective source sets.

        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            // Ktor 3.0+ publishes ktor-server-cio for Kotlin/Native (iosArm64, iosSimulatorArm64,
            // macosArm64) in addition to JVM. Keeping the server stack in commonMain lets the
            // Apple FileServer share routing/streaming code with the JVM actual instead of
            // hand-rolling an HTTP listener. See docs/engineering/adr/adr-apple-fileserver-engine.md.
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.cio)
            implementation(libs.ktor.server.content.negotiation)
            implementation(libs.decompose.core)
            implementation(libs.decompose.extensions.compose)
            implementation(libs.essenty.lifecycle.coroutines)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }

        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.lifecycle.service)
            implementation("androidx.security:security-crypto:1.0.0")
        }

        androidUnitTest.dependencies {
            implementation(libs.robolectric)
            implementation(libs.kotlin.testJunit)
        }

        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutinesSwing)
                implementation(libs.jmdns)
                implementation(libs.jna)
                implementation(libs.clikt)
            }
        }

        // jvmTest is the intermediate test source set shared by androidUnitTest and desktopTest.
        // Tests for shared JVM code (e.g., FileServer) live here so they run on both targets.
        sourceSets.named("jvmTest") {
            dependencies {
                implementation(libs.kotlin.testJunit)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
            }
        }

        sourceSets.named("desktopTest") {
            dependencies {
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}

android {
    namespace = "com.tubetoast.tether"
    compileSdk = libs.versions.android.compileSdk
        .get()
        .toInt()

    defaultConfig {
        applicationId = "com.tubetoast.tether"
        minSdk = libs.versions.android.minSdk
            .get()
            .toInt()
        targetSdk = libs.versions.android.targetSdk
            .get()
            .toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    debugImplementation(libs.compose.uiTooling)
}

// Installs tether CLI to ~/.local/bin/tether for system-wide access
tasks.register("installJar") {
    group = "application"
    description = "Installs uber JAR to ~/.local/bin/tether for CLI access"
    dependsOn("packageUberJarForCurrentOS")
    notCompatibleWithConfigurationCache("installJar performs file I/O and system operations")
    doLast {
        val jarDir = layout.buildDirectory
            .dir("compose/jars")
            .get()
            .asFile
        val sourceJar = jarDir.listFiles()?.firstOrNull { it.extension == "jar" }
            ?: error("Uber JAR not found in ${jarDir.absolutePath}")

        val userHome = System.getProperty("user.home")
        val localBinDir = File("$userHome/.local/bin")
        val wrapperScript = File(localBinDir, "tether")

        if (!localBinDir.exists()) {
            localBinDir.mkdirs()
        }

        val bashScript = "#!/bin/bash\nexec java -jar \"$sourceJar\" \"\$@\""
        wrapperScript.writeText(bashScript)
        wrapperScript.setExecutable(true)
        println("✓ Installed to ${wrapperScript.absolutePath}")
        println("  Add to PATH: export PATH=\"\$PATH:$userHome/.local/bin\"")
        println("  Then run: tether --help")
    }
}

compose.desktop {
    application {
        mainClass = "com.tubetoast.tether.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.tubetoast.tether"
            packageVersion = "1.0.0"

            linux {
                iconFile.set(project.file("src/commonMain/composeResources/drawable/icon.png"))
            }
            // For macOS and Windows, you need .icns and .ico formats respectively
            // macOS {
            //    iconFile.set(project.file("src/commonMain/composeResources/drawable/icon.icns"))
            // }
            // windows {
            //    iconFile.set(project.file("src/commonMain/composeResources/drawable/icon.ico"))
            // }
        }
    }
}
