package com.enigma.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.enigma.tv.ui.theme.BgDark
import com.enigma.tv.ui.theme.EnigmaCyan
import com.enigma.tv.ui.theme.EnigmaPink
import com.enigma.tv.ui.theme.EnigmaPurple
import com.enigma.tv.ui.theme.GlassBorder
import com.enigma.tv.ui.theme.GlassFill
import com.enigma.tv.ui.theme.GlassFillLight
import com.enigma.tv.ui.theme.GlassHighlight

/** Full-screen ambient background for phone / TV shells. */
@Composable
fun AppAmbientBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF12101F),
                        BgDark,
                        Color(0xFF0A0818)
                    )
                )
            )
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        EnigmaPurple.copy(alpha = 0.18f),
                        Color.Transparent
                    ),
                    radius = 900f
                )
            )
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        EnigmaCyan.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = androidx.compose.ui.geometry.Offset(900f, 1200f),
                    radius = 700f
                )
            ),
        content = content
    )
}

fun Modifier.glassSurface(
    cornerRadius: Dp = 16.dp,
    accentBorder: Boolean = false
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    val borderBrush = if (accentBorder) {
        Brush.linearGradient(listOf(EnigmaPurple.copy(0.5f), EnigmaPink.copy(0.35f), GlassBorder))
    } else {
        Brush.linearGradient(listOf(GlassBorder, GlassHighlight, GlassBorder.copy(0.5f)))
    }
    return this
        .clip(shape)
        .background(
            brush = Brush.linearGradient(
                colors = listOf(GlassFillLight, GlassFill, GlassFill.copy(alpha = 0.65f))
            ),
            shape = shape
        )
        .border(width = 1.dp, brush = borderBrush, shape = shape)
}

/** Strip emoji/symbol prefixes from TMDB row titles for a cleaner catalog look.
 *  Uses codepoint range checks instead of \p{Emoji}/\p{Extended_Pictographic} which
 *  are not supported on Fire OS (older ICU / Java regex engine). */
fun cleanRowTitle(title: String): String {
    var start = 0
    val cps = title.codePoints().toArray()
    while (start < cps.size) {
        val cp = cps[start]
        if (isEmojiOrSymbolCodepoint(cp) || cp == 0x20 /* space */ || cp == 0x9 /* tab */) {
            start++
        } else {
            break
        }
    }
    return title.substring(cps.take(start).sumOf { Character.charCount(it) }).trim()
}

private fun isEmojiOrSymbolCodepoint(cp: Int): Boolean =
    cp in 0x2000..0x2BFF ||   // General punctuation, arrows, misc symbols
    cp in 0x2E00..0x2E7F ||   // Supplemental punctuation
    cp in 0x3000..0x303F ||   // CJK symbols
    cp in 0xFE30..0xFE4F ||   // CJK compatibility
    cp in 0x1F000..0x1FFFF || // Emoticons, misc pictographs, transport, etc.
    cp in 0x1FA00..0x1FAFF || // Supplemental symbols and pictographs
    cp in 0x200D..0x200F ||   // Zero-width joiners / direction marks
    cp in 0xFE00..0xFE0F      // Variation selectors
