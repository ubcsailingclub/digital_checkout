package com.ubcsc.checkout.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Per-theme color bundle
// ---------------------------------------------------------------------------

data class KioskColors(
    val label:      String,
    val accent:     Color,   // highlight / icons        — was TealLight
    val accentMid:  Color,   // primary actions/buttons  — was TealMid
    val accentDark: Color,   // pressed / container      — was TealDark
    val textWarm:   Color,   // secondary text           — was TextSecondary
)

// ---------------------------------------------------------------------------
// Theme definitions
// ---------------------------------------------------------------------------

val DarkOceanColors = KioskColors(
    label      = "Dark Ocean",
    accent     = Color(0xFF4DD0E1),
    accentMid  = Color(0xFF00ACC1),
    accentDark = Color(0xFF006064),
    textWarm   = Color(0xFF90CAF9),
)

val SlateAmberColors = KioskColors(
    label      = "Slate & Amber",
    accent     = Color(0xFFFFCA28),
    accentMid  = Color(0xFFFF8F00),
    accentDark = Color(0xFFE65100),
    textWarm   = Color(0xFFFFE082),
)

val ForestGreenColors = KioskColors(
    label      = "Forest Green",
    accent     = Color(0xFF81C784),
    accentMid  = Color(0xFF388E3C),
    accentDark = Color(0xFF1B5E20),
    textWarm   = Color(0xFFA5D6A7),
)

val DuskPurpleColors = KioskColors(
    label      = "Dusk Purple",
    accent     = Color(0xFFCE93D8),
    accentMid  = Color(0xFF8E24AA),
    accentDark = Color(0xFF6A1B9A),
    textWarm   = Color(0xFFE1BEE7),
)

val ArcticBlueColors = KioskColors(
    label      = "Arctic Blue",
    accent     = Color(0xFF4FC3F7),
    accentMid  = Color(0xFF0288D1),
    accentDark = Color(0xFF01579B),
    textWarm   = Color(0xFFBBDEFB),
)

val MidnightSteelColors = KioskColors(
    label      = "Midnight Steel",
    accent     = Color(0xFF82AAFF),
    accentMid  = Color(0xFF3D6ECC),
    accentDark = Color(0xFF1A3A8A),
    textWarm   = Color(0xFFB0C4DE),
)

val CopperSlateColors = KioskColors(
    label      = "Copper & Slate",
    accent     = Color(0xFFE8A96A),
    accentMid  = Color(0xFFB87333),
    accentDark = Color(0xFF8B5E2A),
    textWarm   = Color(0xFFF5CFA0),
)

val CrimsonNightColors = KioskColors(
    label      = "Crimson Night",
    accent     = Color(0xFFEF9A9A),
    accentMid  = Color(0xFFC62828),
    accentDark = Color(0xFF8B0000),
    textWarm   = Color(0xFFFFCCBC),
)

val ElectricIndigoColors = KioskColors(
    label      = "Electric Indigo",
    accent     = Color(0xFF7986CB),
    accentMid  = Color(0xFF5C6BC0),
    accentDark = Color(0xFF3949AB),
    textWarm   = Color(0xFFC5CAE9),
)

val RoseGoldColors = KioskColors(
    label      = "Rose Gold",
    accent     = Color(0xFFE8B4B8),
    accentMid  = Color(0xFFC48B8B),
    accentDark = Color(0xFF8B5E60),
    textWarm   = Color(0xFFF5D9D9),
)

val SageFogColors = KioskColors(
    label      = "Sage & Fog",
    accent     = Color(0xFF8FBC8F),
    accentMid  = Color(0xFF5C8A6E),
    accentDark = Color(0xFF3D6B52),
    textWarm   = Color(0xFFB8D4B8),
)

val SolarFlareColors = KioskColors(
    label      = "Solar Flare",
    accent     = Color(0xFFFF7043),
    accentMid  = Color(0xFFE64A19),
    accentDark = Color(0xFFBF360C),
    textWarm   = Color(0xFFFFAB91),
)

val NeonLimeColors = KioskColors(
    label      = "Neon Lime",
    accent     = Color(0xFFCCFF00),   // electric lime
    accentMid  = Color(0xFF76B900),   // darker green
    accentDark = Color(0xFF3A5A00),   // deep forest
    textWarm   = Color(0xFFE8FF99),   // pale lime
)

val BrassNavyColors = KioskColors(
    label      = "Brass & Navy",
    accent     = Color(0xFFF5C842),   // polished brass
    accentMid  = Color(0xFFC8871A),   // aged brass
    accentDark = Color(0xFF1B2A4A),   // deep navy
    textWarm   = Color(0xFFDEC98A),   // worn canvas/rope
)

// ---------------------------------------------------------------------------
// Enum — used for persistence (stored as .name string)
// ---------------------------------------------------------------------------

enum class AppTheme(val colors: KioskColors) {
    DARK_OCEAN(DarkOceanColors),
    SLATE_AMBER(SlateAmberColors),
    FOREST_GREEN(ForestGreenColors),
    DUSK_PURPLE(DuskPurpleColors),
    ARCTIC_BLUE(ArcticBlueColors),
    MIDNIGHT_STEEL(MidnightSteelColors),
    COPPER_SLATE(CopperSlateColors),
    CRIMSON_NIGHT(CrimsonNightColors),
    ELECTRIC_INDIGO(ElectricIndigoColors),
    ROSE_GOLD(RoseGoldColors),
    SAGE_FOG(SageFogColors),
    SOLAR_FLARE(SolarFlareColors),
    BRASS_NAVY(BrassNavyColors),
    NEON_LIME(NeonLimeColors);

    val label: String get() = colors.label

    companion object {
        fun fromName(name: String) =
            entries.find { it.name == name } ?: COPPER_SLATE
    }
}

// ---------------------------------------------------------------------------
// CompositionLocal
// ---------------------------------------------------------------------------

val LocalKioskColors = compositionLocalOf<KioskColors> { CopperSlateColors }
