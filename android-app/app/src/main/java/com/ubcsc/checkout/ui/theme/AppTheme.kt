package com.ubcsc.checkout.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Per-theme color bundle (accent family + warm secondary text)
// ---------------------------------------------------------------------------

data class KioskColors(
    val label:      String,
    val accent:     Color,   // highlight / icons        — was TealLight
    val accentMid:  Color,   // primary actions/buttons  — was TealMid
    val accentDark: Color,   // pressed / container      — was TealDark
    val textWarm:   Color,   // secondary text           — was TextSecondary
)

// ---------------------------------------------------------------------------
// Built-in themes
// ---------------------------------------------------------------------------

val CopperSlateColors = KioskColors(
    label      = "Copper & Slate",
    accent     = Color(0xFFE8A96A),
    accentMid  = Color(0xFFB87333),
    accentDark = Color(0xFF8B5E2A),
    textWarm   = Color(0xFFF5CFA0),
)

val OceanTealColors = KioskColors(
    label      = "Ocean Teal",
    accent     = Color(0xFF4DD0C4),
    accentMid  = Color(0xFF00897B),
    accentDark = Color(0xFF00695C),
    textWarm   = Color(0xFFB2DFDB),
)

val RoyalBlueColors = KioskColors(
    label      = "Royal Blue",
    accent     = Color(0xFF82B1FF),
    accentMid  = Color(0xFF2979FF),
    accentDark = Color(0xFF1651C5),
    textWarm   = Color(0xFFBBDEFB),
)

val ForestGreenColors = KioskColors(
    label      = "Forest",
    accent     = Color(0xFF69F0AE),
    accentMid  = Color(0xFF00C853),
    accentDark = Color(0xFF009624),
    textWarm   = Color(0xFFCCFF90),
)

// ---------------------------------------------------------------------------
// Enum — used for persistence (stored as .name string)
// ---------------------------------------------------------------------------

enum class AppTheme(val colors: KioskColors) {
    COPPER_SLATE(CopperSlateColors),
    OCEAN_TEAL(OceanTealColors),
    ROYAL_BLUE(RoyalBlueColors),
    FOREST_GREEN(ForestGreenColors);

    val label: String get() = colors.label

    companion object {
        fun fromName(name: String) =
            entries.find { it.name == name } ?: COPPER_SLATE
    }
}

// ---------------------------------------------------------------------------
// CompositionLocal — screens read colors via LocalKioskColors.current
// ---------------------------------------------------------------------------

val LocalKioskColors = compositionLocalOf<KioskColors> { CopperSlateColors }
