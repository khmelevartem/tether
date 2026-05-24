package com.tubetoast.tether.ui.preview

import androidx.compose.ui.tooling.preview.PreviewParameterProvider

class Themes : PreviewParameterProvider<Boolean> {
    override val values = sequenceOf(false, true)
}
