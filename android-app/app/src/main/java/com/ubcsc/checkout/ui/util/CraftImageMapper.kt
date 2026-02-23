package com.ubcsc.checkout.ui.util

import com.ubcsc.checkout.R

/**
 * Maps a craft's fleet/class string to a drawable resource.
 * Icons are gray silhouettes from the UBC Sailing Logbook project —
 * apply a ColorFilter in the composable to tint them appropriately.
 */
object CraftImageMapper {
    fun getDrawableRes(craftClass: String): Int = when (craftClass.lowercase().trim()) {
        "laser", "laser radial", "ilca", "ilca 6", "ilca 7"   -> R.drawable.ic_laser
        "fj", "flying junior"                                   -> R.drawable.ic_fj
        "quest"                                                 -> R.drawable.ic_quest
        "vanguard", "vanguard 15"                               -> R.drawable.ic_vanguard
        "hobie", "hobie cat", "hobie 16"                        -> R.drawable.ic_hobie
        "f18", "f-18", "catamaran f18", "formula 18"            -> R.drawable.ic_f18
        "rs", "rs500", "rs800", "rs400", "rs feva"              -> R.drawable.ic_rs
        "kayak", "sea kayak", "touring kayak"                   -> R.drawable.ic_kayak
        "windsurfer", "windsurf", "windsurfing"                 -> R.drawable.ic_windsurfer
        "sup", "stand up paddle", "paddleboard"                 -> R.drawable.ic_sup
        else                                                    -> R.drawable.ic_vanguard  // fallback
    }
}
