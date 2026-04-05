package com.ubcsc.checkout.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun DigitalCheckoutTheme(
    theme:   AppTheme = AppTheme.COPPER_SLATE,
    content: @Composable () -> Unit
) {
    val c = theme.colors
    val colorScheme = darkColorScheme(
        primary              = c.accentMid,
        onPrimary            = White,
        primaryContainer     = c.accentDark,
        onPrimaryContainer   = c.accent,
        secondary            = c.accent,
        onSecondary          = DeepOcean,
        background           = DeepOcean,
        onBackground         = TextPrimary,
        surface              = OceanSurface,
        onSurface            = TextPrimary,
        surfaceVariant       = CardBlue,
        onSurfaceVariant     = c.textWarm,
        outline              = DividerColor,
        error                = UnavailableRed,
        onError              = White,
    )
    CompositionLocalProvider(LocalKioskColors provides c) {
        MaterialTheme(
            colorScheme = colorScheme,
            content     = content
        )
    }
}
