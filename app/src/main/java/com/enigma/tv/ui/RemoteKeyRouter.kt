package com.enigma.tv.ui

import android.view.KeyEvent

/**
 * Singleton bus: MainActivity dispatches every hardware key press here.
 * PlayerFullscreenHost registers a handler when active so it intercepts
 * remote keys BEFORE any View or Compose focus chain has a chance to
 * swallow them.
 */
object RemoteKeyRouter {
    var handler: ((keyCode: Int) -> Boolean)? = null

    fun dispatch(keyCode: Int): Boolean = handler?.invoke(keyCode) ?: false
}
