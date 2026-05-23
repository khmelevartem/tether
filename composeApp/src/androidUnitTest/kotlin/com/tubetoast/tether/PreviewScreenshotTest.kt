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
