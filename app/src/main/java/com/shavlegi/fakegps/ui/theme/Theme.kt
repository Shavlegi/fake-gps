package com.shavlegi.fakegps.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Shao's style: black / dark blue / white.
private val DarkColors = darkColorScheme(
    primary = Color(0xFF4C8DFF),
    onPrimary = Color.White,
    secondary = Color(0xFF7FB0FF),
    onSecondary = Color(0xFF0B0F19),
    background = Color(0xFF0B0F19),
    surface = Color(0xFF121A2B),
    onBackground = Color.White,
    onSurface = Color.White,
    error = Color(0xFFFF6B6B),
    onError = Color.White
)

@Composable
fun FakeGpsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content
    )
}
