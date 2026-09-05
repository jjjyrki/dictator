package io.jyri.dictator.overlay

import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import io.jyri.dictator.R
import io.jyri.dictator.session.DictationState

class BubbleOverlay(
    private val windowManager: WindowManager,
    inflater: LayoutInflater,
    private val onTap: () -> Unit,
) {
    private val view: View = inflater.inflate(R.layout.bubble, null)
    private val label: TextView = view.findViewById(R.id.bubbleLabel)
    private var attached = false

    init {
        view.setOnClickListener { onTap() }
    }

    fun show(state: DictationState) {
        bind(state)
        if (attached) return
        windowManager.addView(view, layoutParams())
        attached = true
    }

    fun update(state: DictationState) {
        if (!attached) return
        bind(state)
    }

    fun hide() {
        if (!attached) return
        windowManager.removeView(view)
        attached = false
    }

    private fun bind(state: DictationState) {
        val (text, background, description) = when (state) {
            DictationState.Idle -> Triple(
                R.string.bubble_idle,
                R.drawable.bubble_idle,
                R.string.bubble_idle,
            )
            DictationState.Recording -> Triple(
                R.string.bubble_recording,
                R.drawable.bubble_recording,
                R.string.bubble_recording,
            )
            DictationState.Processing -> Triple(
                R.string.bubble_processing,
                R.drawable.bubble_processing,
                R.string.bubble_processing,
            )
            DictationState.Done -> Triple(
                R.string.bubble_done,
                R.drawable.bubble_done,
                R.string.bubble_done,
            )
            DictationState.Error -> Triple(
                R.string.bubble_error,
                R.drawable.bubble_error,
                R.string.bubble_error,
            )
        }
        label.setText(text)
        label.setBackgroundResource(background)
        label.contentDescription = label.context.getString(description)
    }

    private fun layoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.BOTTOM or Gravity.END
            x = 24
            y = 180
        }
    }
}
