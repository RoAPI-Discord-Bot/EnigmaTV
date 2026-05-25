package com.enigma.tv.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager

fun Context.findActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

fun Context.isTelevision(): Boolean =
    packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
