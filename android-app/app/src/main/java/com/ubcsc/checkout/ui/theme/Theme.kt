package com.ubcsc.checkout.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = OceanBlue,
    onPrimary = Color.White,
    primaryContainer = WaveBlue,
    onPrimaryContainer = Color.White,
    secondary = NavyBlue,
    onSecondary = Color.White,
    background = SailWhite,
    onBackground = NavyBlue,
    surface = Color.White,
    onSurface = NavyBlue,
    error = CoralRed,
    onError = Color.White,
)

@Composable
fun DigitalCheckoutTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}
