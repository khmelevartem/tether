package com.tubetoast.tether.ui.feature

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tubetoast.tether.ui.designsystem.EllipsizedText

@Composable
fun CurrentFileLabel(
    fileName: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    EllipsizedText(
        text = fileName,
        modifier = modifier,
        contentDescription = contentDescription,
    )
}
