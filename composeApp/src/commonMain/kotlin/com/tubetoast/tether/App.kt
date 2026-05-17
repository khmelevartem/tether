package com.tubetoast.tether

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tubetoast.tether.presentation.RootComponent
import com.tubetoast.tether.presentation.RootContent

@Composable
fun App(root: RootComponent, modifier: Modifier = Modifier) {
    RootContent(root, modifier)
}
