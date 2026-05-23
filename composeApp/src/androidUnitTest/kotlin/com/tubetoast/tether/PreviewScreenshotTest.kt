package com.tubetoast.tether

import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import sergio.sastre.composable.preview.scanner.android.AndroidComposablePreviewScanner
import sergio.sastre.composable.preview.scanner.android.AndroidPreviewInfo
import sergio.sastre.composable.preview.scanner.android.screenshotid.AndroidPreviewScreenshotIdBuilder
import sergio.sastre.composable.preview.scanner.core.preview.ComposablePreview

/**
 * Renders every `@Preview` composable under `com.tubetoast.tether` to a PNG.
 *
 * Flow:
 * 1. `previews()` is a `@Parameters` provider — JUnit calls it once before the test class
 *    is instantiated. `ComposablePreviewScanner` walks the Android-target classpath via
 *    ClassGraph, finds every function annotated with `@Preview` (including `private` ones,
 *    used by stateless `*Content` composables under `presentation/`), and returns one
 *    `ComposablePreview` descriptor per preview.
 * 2. `ParameterizedRobolectricTestRunner` instantiates this class once per descriptor,
 *    binding it to `preview`, and runs `snapshot()`. So N previews → N test instances → N PNGs.
 * 3. `snapshot()` derives a stable filename from the preview's FQN + name, then asks Roborazzi
 *    to capture the composable via `captureRoboImage`. Roborazzi runs Compose under Robolectric
 *    with native graphics, no emulator needed.
 *
 * Empty discovery is guarded — if the scanner finds zero previews, JUnit would generate zero
 * tests and the task would pass green; the `check` makes that scenario fail loudly.
 *
 * Output: `composeApp/build/outputs/roborazzi/<FQN>_<PreviewName>.png`, consumed by `review-visual`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "normal-port-notnight-mdpi")
class PreviewScreenshotTest(
    private val preview: ComposablePreview<AndroidPreviewInfo>,
) {
    @Test
    fun snapshot() {
        val fileName = AndroidPreviewScreenshotIdBuilder(preview)
            .encodeUnsafeCharacters()
            .build()
        captureRoboImage(
            filePath = "build/outputs/roborazzi/$fileName.png",
        ) {
            preview()
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun previews(): List<ComposablePreview<AndroidPreviewInfo>> =
            AndroidComposablePreviewScanner()
                .scanPackageTrees("com.tubetoast.tether")
                .includePrivatePreviews()
                .getPreviews()
                .also {
                    check(it.isNotEmpty()) {
                        "No @Preview composables found via ComposablePreviewScanner — scanner misconfigured or all previews removed"
                    }
                }
    }
}
