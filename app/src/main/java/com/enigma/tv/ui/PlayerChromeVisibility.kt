package com.enigma.tv.ui

import androidx.compose.runtime.staticCompositionLocalOf

/** Sync Enigma top chrome with ExoPlayer controller visibility. */
val LocalPlayerChromeSync = staticCompositionLocalOf<(Boolean) -> Unit> { {} }
