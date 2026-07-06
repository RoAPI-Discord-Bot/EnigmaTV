package com.enigma.tv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Deep cinematic base
val BgDark = Color(0xFF050508)
val BgElevated = Color(0xFF0C0C14)
val BgHeader = Color(0xFF101018)
val BgSidebar = Color(0xFF0A0A12)
val MovieAccent = Color(0xFFE50914)
val TvAccent = Color(0xFFC9A227)
val EnigmaPurple = Color(0xFF9B5DE5)
val EnigmaPink = Color(0xFFE040FB)
val EnigmaCyan = Color(0xFF00BBF9)
val CardBg = Color(0xFF161622)
val TextPrimary = Color(0xFFF4F4F8)
val TextSecondary = Color(0xFFB4B4C8)
val SearchBg = Color(0xFF222233)

// Glass tokens
val GlassFill = Color(0xFF1E1E2E).copy(alpha = 0.72f)
val GlassFillLight = Color(0xFFFFFFFF).copy(alpha = 0.08f)
val GlassBorder = Color(0xFFFFFFFF).copy(alpha = 0.14f)
val GlassBorderAccent = EnigmaPurple.copy(alpha = 0.45f)
val GlassHighlight = Color(0xFFFFFFFF).copy(alpha = 0.06f)

private val DarkScheme = darkColorScheme(
    primary = EnigmaPurple,
    secondary = EnigmaPink,
    tertiary = MovieAccent,
    background = BgDark,
    surface = BgElevated,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

@Composable
fun EnigmaTVTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkScheme,
        content = content
    )
}
