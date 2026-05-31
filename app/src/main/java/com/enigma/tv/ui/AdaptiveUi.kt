package com.enigma.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

enum class ScreenLayout { PHONE, TABLET, TV }

@Composable
fun rememberScreenLayout(): ScreenLayout {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return when {
        widthDp >= 960 -> ScreenLayout.TV
        widthDp >= 600 -> ScreenLayout.TABLET
        else -> ScreenLayout.PHONE
    }
}

fun ScreenLayout.posterColumns(): Int = when (this) {
    ScreenLayout.PHONE -> 3
    ScreenLayout.TABLET -> 5
    ScreenLayout.TV -> 7
}

fun ScreenLayout.drawerWidthDp(): Int = when (this) {
    ScreenLayout.PHONE -> 280
    ScreenLayout.TABLET -> 300
    ScreenLayout.TV -> 240
}

fun ScreenLayout.usePermanentDrawer(): Boolean = this == ScreenLayout.TV

fun ScreenLayout.posterWidthDp(): Int = when (this) {
    ScreenLayout.PHONE -> 148
    ScreenLayout.TABLET -> 148
    ScreenLayout.TV -> 118
}

fun ScreenLayout.contentPaddingDp(): Int = when (this) {
    ScreenLayout.PHONE -> 16
    ScreenLayout.TABLET -> 24
    ScreenLayout.TV -> 36
}
