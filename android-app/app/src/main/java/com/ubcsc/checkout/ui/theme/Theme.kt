package com.ubcsc.checkout.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary              = TealMid,
    onPrimary            = White,
    primaryContainer     = TealDark,
    onPrimaryContainer   = TealLight,
    secondary            = TealLight,
    onSecondary          = DeepOcean,
    background           = DeepOcean,
    onBackground         = TextPrimary,
    surface              = OceanSurface,
    onSurface            = TextPrimary,
    surfaceVariant       = CardBlue,
    onSurfaceVariant     = TextSecondary,
    outline              = DividerColor,
    error                = UnavailableRed,
    onError              = White,
)

@Composable
fun DigitalCheckoutTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
