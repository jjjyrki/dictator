package io.jyri.dictator

import android.os.Build
import android.view.View
import android.view.WindowInsets

/** Keeps scrollable content below the system bars on Android 15+ edge-to-edge windows. */
fun View.applySystemBarInsets() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return

    val initialLeft = paddingLeft
    val initialTop = paddingTop
    val initialRight = paddingRight
    val initialBottom = paddingBottom

    setOnApplyWindowInsetsListener { view, insets ->
        val systemBars = insets.getInsets(WindowInsets.Type.systemBars())
        view.setPadding(
            initialLeft + systemBars.left,
            initialTop + systemBars.top,
            initialRight + systemBars.right,
            initialBottom + systemBars.bottom,
        )
        insets
    }
    requestApplyInsets()
}
