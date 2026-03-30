package com.ubcsc.checkout.ui.util

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import com.ubcsc.checkout.R

/**
 * Maps a craft's fleet/class string to a drawable resource.
 * Use [iconColorFilter] to render black-on-white PNGs as tinted monochrome
 * icons on the dark background: white pixels become transparent, black pixels
 * become the target color.
 */
object CraftImageMapper {

    /** Converts a black-on-white PNG to a solid tinted icon on any background. */
    fun iconColorFilter(r: Int, g: Int, b: Int): ColorFilter =
        ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
            0f, 0f, 0f, 0f, r.toFloat(),   // R = constant
            0f, 0f, 0f, 0f, g.toFloat(),   // G = constant
            0f, 0f, 0f, 0f, b.toFloat(),   // B = constant
            -0.299f, -0.587f, -0.114f, 0f, 255f  // A = 255 - luminance (white→transparent, black→opaque)
        )))

    // TealLight  = 0xFF4DD0E1 → (77, 208, 225)
    // TextMuted  = 0xFF546E7A → (84, 110, 122)
    val filterAvailable   = iconColorFilter(77,  208, 225)
    val filterUnavailable = iconColorFilter(84,  110, 122)

    fun getDrawableRes(craftClass: String): Int {
        val s = craftClass.lowercase().trim()
        return when {
            s.contains("laser") || s.contains("ilca")
                || s.startsWith("lz")                                   -> R.drawable.ic_laser
            s.contains("quest") || s.startsWith("qt") || s.startsWith("qs") -> R.drawable.ic_quest
            s.contains("vanguard") || s.startsWith("vg")               -> R.drawable.ic_vanguard
            s.contains("rs5") || s.contains("rs8") || s.contains("rs4")
                || s.contains("feva") || s == "rs"                     -> R.drawable.ic_rs
            s.contains("hobie") || s.startsWith("hb")                  -> R.drawable.ic_hobie
            s.contains("f18") || s.contains("f-18") || s.contains("nacra")
                || s.contains("catamaran") || s.startsWith("f1")       -> R.drawable.ic_f18
            s.contains("windsurf") || s.startsWith("ws")               -> R.drawable.ic_windsurfer
            s.contains("kayak") || s.startsWith("kd") || s.startsWith("ks") -> R.drawable.ic_kayak
            s.contains("sup") || s.contains("paddle") || s.startsWith("sp") -> R.drawable.ic_sup
            else                                                        -> R.drawable.ic_vanguard
        }
    }
}
