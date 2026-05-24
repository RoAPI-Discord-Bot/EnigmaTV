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
    ScreenLayout.TABLET -> 4
    ScreenLayout.TV -> 6
}

fun ScreenLayout.drawerWidthDp(): Int = when (this) {
    ScreenLayout.PHONE -> 280
    ScreenLayout.TABLET -> 300
    ScreenLayout.TV -> 320
}

fun ScreenLayout.usePermanentDrawer(): Boolean = this == ScreenLayout.TV

fun ScreenLayout.posterWidthDp(): Int = when (this) {
    ScreenLayout.PHONE -> 150
    ScreenLayout.TABLET -> 165
    ScreenLayout.TV -> 140
}

fun ScreenLayout.contentPaddingDp(): Int = when (this) {
    ScreenLayout.PHONE -> 16
    ScreenLayout.TABLET -> 24
    ScreenLayout.TV -> 32
}
