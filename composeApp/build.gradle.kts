import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
        }

        // Access via name to avoid "Source Set used with custom target name" KGP warning.
        // jvmMain is intentionally created as an intermediate source set by applyHierarchyTemplate
        // (not as the leaf for jvm("desktop")), hence the mismatch the warning detects.
        sourceSets.named("jvmMain") {
            dependencies {
                // Ktor server is JVM-only; no Kotlin/Native publication exists for ktor-server-*
                // Shared between Android and Desktop JVM targets
                implementation(libs.ktor.server.core)
                implementation(libs.ktor.server.cio)
                implementation(libs.ktor.server.content.negotiation)
            }
        }

        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutinesSwing)
                implementation(libs.jmdns)
                implementation(libs.clikt)
            }
        }

        val desktopTest by getting {
            dependencies {
                implementation(libs.kotlin.testJunit)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
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
}

dependencies {
    debugImplementation(libs.compose.uiTooling)
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
