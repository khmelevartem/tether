package com.tubetoast.tether.ui.theme

import androidx.compose.ui.graphics.Color

data class TetherColors(
    val surface: Color,
    val surfaceRaised: Color,
    val border: Color,
    val textPrimary: Color,
    val textMuted: Color,
    val accent: Color,
    val error: Color,
    val peerIdentity: Color,
)

fun lightTetherColors() = TetherColors(
    surface = Color(0xFFFBFAF7),
    surfaceRaised = Color(0xFFFFFFFF),
    border = Color(0xFFE8E5DE),
    textPrimary = Color(0xFF1A1A1F),
    textMuted = Color(0xFF6B6B73),
    accent = Color(0xFF2F7D6B),
    error = Color(0xFFB4423A),
    peerIdentity = Color(0xFFC77E47),
)

fun darkTetherColors() = TetherColors(
    surface = Color(0xFF15171A),
    surfaceRaised = Color(0xFF1E2125),
    border = Color(0xFF2A2E33),
    textPrimary = Color(0xFFECECEE),
    textMuted = Color(0xFF9A9DA3),
    accent = Color(0xFF3FA08A),
    error = Color(0xFFE26A60),
    peerIdentity = Color(0xFFD89968),
)
