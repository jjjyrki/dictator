package io.jyri.dictator.overlay

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Bubble root. When dictation is idle it intercepts drags so the bubble can
 * be repositioned; while recording it lets the buttons handle every touch.
 */
class BubbleRowView(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    /** Fired once when a drag begins, after the slop is exceeded. */
    var onDragStart: (() -> Unit)? = null

    /** Raw finger delta from the drag start; called on every drag move. */
    var onDrag: ((dx: Float, dy: Float) -> Unit)? = null

    /** Fired once when the finger lifts after a drag. */
    var onDragEnd: (() -> Unit)? = null

    /** Dragging is only allowed while the bubble is idle. */
    var dragEnabled = true

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var dragging = false
    private var downX = 0f
    private var downY = 0f

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (!dragEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && (abs(event.rawX - downX) > slop || abs(event.rawY - downY) > slop)) {
                    dragging = true
                    onDragStart?.invoke()
                    return true
                }
            }
        }
        return false
    }

    // Non-drag events go to FrameLayout, which handles performClick. Child buttons
    // own taps; ending an intercepted drag must not trigger a click.
    @Suppress("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!dragging) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                onDrag?.invoke(event.rawX - downX, event.rawY - downY)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                onDragEnd?.invoke()
            }
        }
        return true
    }
}
