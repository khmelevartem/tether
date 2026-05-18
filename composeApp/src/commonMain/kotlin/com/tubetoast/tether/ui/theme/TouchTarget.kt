package com.tubetoast.tether.ui.theme

import androidx.compose.foundation.layout.sizeIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** WCAG / Material accessibility minimum: 48 × 48 dp. */
fun Modifier.tetherMinTouchTarget(): Modifier = sizeIn(minWidth = 48.dp, minHeight = 48.dp)
