package com.enigma.tv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BgDark = Color(0xFF0A0A0A)
val BgHeader = Color(0xFF141414)
val BgSidebar = Color(0xFF0E0E12)
val MovieAccent = Color(0xFFE50914)
val TvAccent = Color(0xFFAA9933)
val EnigmaPurple = Color(0xFF9B5DE5)
val EnigmaPink = Color(0xFFE040FB)
val EnigmaCyan = Color(0xFF00BBF9)
val CardBg = Color(0xFF1A1A1A)
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFCCCCCC)
val SearchBg = Color(0xFF333333)

private val DarkScheme = darkColorScheme(
    primary = EnigmaPurple,
    secondary = EnigmaPink,
    tertiary = MovieAccent,
    background = BgDark,
    surface = BgHeader,
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
