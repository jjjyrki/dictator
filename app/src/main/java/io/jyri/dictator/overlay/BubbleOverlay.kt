package io.jyri.dictator.overlay

import android.animation.ObjectAnimator
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import io.jyri.dictator.R
import io.jyri.dictator.session.DictationState
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Wispr-style bubble: idle shows a round mic; recording morphs the mic into
 * an accept button while cancel and a waveform chip slide out from behind it.
 * The accept button is pinned to the row's right edge and never moves.
 */
class BubbleOverlay(
    private val windowManager: WindowManager,
    inflater: LayoutInflater,
    private val onTap: () -> Unit,
    private val onCancel: () -> Unit = {},
) {
    // WindowManager owns this root and supplies its layout parameters in addView.
    @Suppress("InflateParams")
    private val view: View = inflater.inflate(R.layout.bubble, null)
    private val row: BubbleRowView = view.findViewById(R.id.bubbleRow)
    private val acceptButton: FrameLayout = view.findViewById(R.id.acceptButton)
    private val acceptIcon: ImageView = view.findViewById(R.id.acceptIcon)
    private val cancelButton: FrameLayout = view.findViewById(R.id.cancelButton)
    private val waveChip: FrameLayout = view.findViewById(R.id.waveChip)
    private val waveChipView: WaveChipView = view.findViewById(R.id.waveChipView)
    private val partialText: TextView = view.findViewById(R.id.partialText)
    private var attached = false
    private var expanded = false
    private var lastState: DictationState? = null
    private var transitionGeneration = 0L
    private var cancelScaleAnimator: ObjectAnimator? = null
    private var waveScaleAnimator: ObjectAnimator? = null
    private var cancelFadeAnimator: ObjectAnimator? = null
    private var waveFadeAnimator: ObjectAnimator? = null
    private var processingSpinnerAnimator: ObjectAnimator? = null
    private var idleButtonResetRunnable: Runnable? = null
    private var dragStartX = 0
    private var dragStartY = 0
    private val density = view.context.resources.displayMetrics.density
    private val compactWidthPx = (BUTTON_DP + 2 * ROW_PADDING_DP) * density.roundToInt()
    private val expandedWidthPx = (ROW_WIDTH_DP + 2 * ROW_PADDING_DP) * density.roundToInt()
    private val heightPx = (ROW_HEIGHT_DP + 2 * ROW_PADDING_DP) * density.roundToInt()
    private val paddingPx = ROW_PADDING_DP * density.roundToInt()
    private val buttonPx = BUTTON_DP * density.roundToInt()
    // Idle only needs to cover the mic button. Keeping the window compact is
    // important: WindowManager routes touches inside the overlay window to it,
    // even when the view underneath is transparent.
    private var windowWidthPx = compactWidthPx
    private val position = Position.load(view.context, expandedWidthPx, paddingPx, buttonPx)

    init {
        // The first visible state is always the idle mic with the extras hidden.
        expanded = false
        cancelButton.visibility = View.GONE
        waveChip.visibility = View.GONE
        cancelButton.alpha = 1f
        waveChip.alpha = 1f
        acceptButton.setBackgroundResource(R.drawable.bubble_idle)
        setAcceptIcon(R.drawable.dictator, DICTATOR_ICON_DP)
        acceptButton.setOnClickListener { onTap() }
        cancelButton.setOnClickListener { onCancel() }
        row.onDragStart = {
            dragStartX = position.acceptX
            dragStartY = position.y
            row.alpha = 0.6f
        }
        row.onDrag = { dx, dy ->
            position.acceptX = (dragStartX + dx.roundToInt()).coerceIn(
                position.minAcceptX,
                position.maxAcceptX,
            )
            position.y = (dragStartY + dy.roundToInt()).coerceIn(0, position.maxY)
            windowManager.updateViewLayout(view, layoutParams())
        }
        row.onDragEnd = {
            row.alpha = 1f
            position.save()
        }
    }

    fun show(state: DictationState) {
        if (!attached) {
            // Attach before starting animations so their first frame is not spent
            // on a detached view.
            windowWidthPx = compactWidthPx
            windowManager.addView(view, layoutParams())
            attached = true
        }
        if (state != lastState) bind(state)
    }

    fun update(state: DictationState) {
        if (!attached || state == lastState) return
        bind(state)
    }

    fun setLevel(rawLevel: Float) {
        if (attached) waveChipView.setLevel(rawLevel)
    }

    fun hide() {
        // removeView() does not stop property animators or posted callbacks.
        // Reset the reusable view before detaching it so a later show starts
        // from a deterministic idle layout.
        cancelTransitionAnimations()
        idleButtonResetRunnable?.let { view.removeCallbacks(it) }
        idleButtonResetRunnable = null
        stopProcessingSpinner()
        acceptButton.animate().cancel()
        acceptButton.scaleX = 1f
        acceptButton.scaleY = 1f
        cancelButton.visibility = View.GONE
        waveChip.visibility = View.GONE
        cancelButton.alpha = 1f
        waveChip.alpha = 1f
        cancelButton.scaleY = 1f
        waveChip.scaleY = 1f
        cancelButton.translationX = 0f
        waveChip.translationX = 0f
        waveChipView.setActive(false)
        row.alpha = 1f
        expanded = false
        windowWidthPx = compactWidthPx
        lastState = null
        if (attached) {
            windowManager.removeView(view)
            attached = false
        }
    }

    private fun bind(state: DictationState) {
        val changed = state != lastState
        lastState = state
        if (state != DictationState.Done) {
            idleButtonResetRunnable?.let { view.removeCallbacks(it) }
            idleButtonResetRunnable = null
        }
        row.dragEnabled = state == DictationState.Idle ||
            state == DictationState.MicrophonePermissionRequired
        when (state) {
            DictationState.Idle -> {
                collapse()
                stopProcessingSpinner()
                acceptButton.setBackgroundResource(R.drawable.bubble_idle)
                setAcceptIcon(R.drawable.dictator, DICTATOR_ICON_DP)
                rootDescription(R.string.bubble_idle)
            }
            DictationState.MicrophonePermissionRequired -> {
                collapse()
                stopProcessingSpinner()
                acceptButton.setBackgroundResource(R.drawable.bubble_permission)
                setAcceptIcon(R.drawable.ic_bubble_mic_off, STANDARD_ICON_DP)
                rootDescription(R.string.bubble_microphone_permission_required)
            }
            DictationState.Recording -> {
                expand()
                stopProcessingSpinner()
                acceptButton.setBackgroundResource(R.drawable.circle_bg_ok)
                setAcceptIcon(R.drawable.ic_bubble_check, STANDARD_ICON_DP)
                resetPartialChip()
                waveChipView.setActive(true)
                rootDescription(R.string.bubble_recording)
            }
            DictationState.Processing -> {
                expand()
                setAcceptIcon(R.drawable.ic_bubble_spinner, STANDARD_ICON_DP)
                startProcessingSpinner()
                waveChipView.setActive(false)
                rootDescription(R.string.bubble_processing)
            }
            DictationState.Done -> {
                collapse()
                showIdleButtonAfterCollapse()
                waveChipView.setActive(false)
                rootDescription(R.string.bubble_done)
            }
            DictationState.Error -> {
                expand()
                stopProcessingSpinner()
                acceptButton.setBackgroundResource(R.drawable.bubble_error)
                setAcceptIcon(R.drawable.ic_bubble_error, STANDARD_ICON_DP)
                waveChipView.setActive(false)
                rootDescription(R.string.bubble_error)
            }
        }
        if (changed && state == DictationState.Recording) {
            bounce(acceptButton)
        }
    }

    private fun showIdleButtonAfterCollapse() {
        idleButtonResetRunnable?.let { view.removeCallbacks(it) }
        val generation = transitionGeneration
        idleButtonResetRunnable = Runnable {
            if (generation != transitionGeneration || lastState != DictationState.Done) {
                return@Runnable
            }
            idleButtonResetRunnable = null
            stopProcessingSpinner()
            acceptButton.setBackgroundResource(R.drawable.bubble_idle)
            setAcceptIcon(R.drawable.dictator, DICTATOR_ICON_DP)
        }.also { view.postDelayed(it, CANCEL_OUT_MS) }
    }

    private fun startProcessingSpinner() {
        if (processingSpinnerAnimator?.isRunning == true) return
        processingSpinnerAnimator = ObjectAnimator.ofFloat(acceptIcon, View.ROTATION, 0f, 360f).apply {
            duration = SPINNER_DURATION_MS
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            start()
        }
    }

    private fun stopProcessingSpinner() {
        processingSpinnerAnimator?.cancel()
        processingSpinnerAnimator = null
        acceptIcon.rotation = 0f
    }

    private fun setAcceptIcon(resource: Int, sizeDp: Int) {
        val sizePx = (sizeDp * density).roundToInt()
        acceptIcon.layoutParams = acceptIcon.layoutParams.apply {
            width = sizePx
            height = sizePx
        }
        acceptIcon.setImageResource(resource)
    }

    private fun rootDescription(resource: Int) {
        view.contentDescription = view.context.getString(resource)
    }

    private fun resetPartialChip() {
        partialText.text = ""
        partialText.visibility = View.GONE
        waveChipView.visibility = View.VISIBLE
    }

    /** Swaps the waveform bars for interim transcription text while recording. */
    fun showPartial(text: String) {
        if (lastState != DictationState.Recording) return
        if (partialText.visibility == View.VISIBLE) {
            partialText.text = text
            return
        }
        waveChipView.setActive(false)
        waveChipView.visibility = View.GONE
        partialText.text = text
        partialText.visibility = View.VISIBLE
    }

    private fun bounce(target: View) {
        target.animate().cancel()
        target.scaleX = 1.12f
        target.scaleY = 1.12f
        target.animate().scaleX(1f).scaleY(1f).setDuration(140).start()
    }

    /** Slides cancel and the waveform chip out from behind the accept button. */
    private fun expand() {
        if (expanded) return
        // A collapse may still be running if the user tapped quickly; take over.
        cancelTransitionAnimations()
        expanded = true
        resizeWindow(expandedWidthPx)
        cancelButton.visibility = View.VISIBLE
        waveChip.visibility = View.VISIBLE
        // Both start stacked on the accept button, then settle into their slots.
        cancelButton.translationX = hiddenOffsetFor(cancelButton)
        waveChip.translationX = hiddenOffsetFor(waveChip)
        cancelButton.alpha = 0f
        waveChip.alpha = 0f
        cancelButton.scaleY = EXTRA_MIN_SCALE_Y
        waveChip.scaleY = EXTRA_MIN_SCALE_Y
        val interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
        cancelButton.animate().translationX(0f)
            .setInterpolator(interpolator).setDuration(SLIDE_IN_MS).start()
        waveChip.animate().translationX(0f)
            .setInterpolator(interpolator).setDuration(SLIDE_IN_MS)
            .setStartDelay(SLIDE_STAGGER_MS).start()
        animateExtraScale(cancelButton, 1f, SLIDE_IN_MS / 2)
        animateExtraScale(waveChip, 1f, SLIDE_IN_MS / 2, SLIDE_STAGGER_MS)
        val fadeDelayMs = SLIDE_IN_MS * FADE_IN_DELAY_NUMERATOR / FADE_IN_DENOMINATOR
        val fadeDurationMs = SLIDE_IN_MS - fadeDelayMs
        animateExtraAlpha(cancelButton, 1f, fadeDurationMs, interpolator, fadeDelayMs)
        animateExtraAlpha(waveChip, 1f, fadeDurationMs, interpolator, SLIDE_STAGGER_MS + fadeDelayMs)
        bounce(acceptButton)
    }

    /** Slides both extras back behind the accept button and hides them. */
    private fun collapse() {
        if (!expanded) return
        expanded = false
        waveChipView.setActive(false)
        val generation = cancelTransitionAnimations()
        val interpolator = PathInterpolator(0.4f, 0f, 1f, 1f)
        val hide = { target: View, durationMs: Long ->
            val hidden = hiddenOffsetFor(target)
            target.animate().translationX(hidden)
                .setInterpolator(interpolator).setDuration(durationMs)
                .withEndAction {
                    if (generation != transitionGeneration) return@withEndAction
                    target.visibility = View.GONE
                    target.alpha = 1f
                    if (target === cancelButton) resizeWindow(compactWidthPx)

                }
                .start()
        }
        // The waveform lingers a beat shorter; cancel drifts out last and slowest.
        hide(waveChip, WAVE_OUT_MS)
        hide(cancelButton, CANCEL_OUT_MS)
        animateExtraScale(waveChip, EXTRA_MIN_SCALE_Y, WAVE_OUT_MS / 2)
        animateExtraScale(cancelButton, EXTRA_MIN_SCALE_Y, CANCEL_OUT_MS / 2)
        animateExtraAlpha(waveChip, 0f, WAVE_OUT_MS * FADE_OUT_NUMERATOR / FADE_OUT_DENOMINATOR, interpolator)
        animateExtraAlpha(cancelButton, 0f, CANCEL_OUT_MS * FADE_OUT_NUMERATOR / FADE_OUT_DENOMINATOR, interpolator)
    }

    /** Cancels every animation that can mutate the extra controls. */
    private fun cancelTransitionAnimations(): Long {
        // Invalidate callbacks before cancelling: ViewPropertyAnimator can still
        // deliver an end callback after cancel() on some Android releases.
        transitionGeneration++
        cancelButton.animate().cancel()
        waveChip.animate().cancel()
        cancelScaleAnimator?.cancel()
        waveScaleAnimator?.cancel()
        cancelFadeAnimator?.cancel()
        waveFadeAnimator?.cancel()
        cancelScaleAnimator = null
        waveScaleAnimator = null
        cancelFadeAnimator = null
        waveFadeAnimator = null
        return transitionGeneration
    }

    private fun animateExtraAlpha(
        target: View,
        endAlpha: Float,
        durationMs: Long,
        interpolator: TimeInterpolator,
        startDelayMs: Long = 0,
    ) {
        val animator = ObjectAnimator.ofFloat(target, View.ALPHA, endAlpha).apply {
            duration = durationMs
            startDelay = startDelayMs
            this.interpolator = interpolator
        }
        if (target === cancelButton) {
            cancelFadeAnimator = animator
        } else {
            waveFadeAnimator = animator
        }
        animator.start()
    }

    private fun animateExtraScale(
        target: View,
        endScaleY: Float,
        durationMs: Long,
        startDelayMs: Long = 0,
    ) {
        val animator = ObjectAnimator.ofFloat(target, View.SCALE_Y, endScaleY).apply {
            duration = durationMs
            startDelay = startDelayMs
            interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
        }
        if (target === cancelButton) {
            cancelScaleAnimator = animator
        } else {
            waveScaleAnimator = animator
        }
        animator.start()
    }

    /** Translation that stacks the view on the accept button slot. */
    private fun hiddenOffsetFor(target: View): Float {
        // The row expands to the right from the compact mic window. Keeping
        // the mic at the window's left edge means resizing never changes its
        // screen position at either end of the animation.
        val acceptLeft = paddingPx.toFloat()
        val slotLeft = if (target.id == R.id.waveChip) {
            (paddingPx + (BUTTON_DP + MARGIN_DP) * density.roundToInt()).toFloat()
        } else {
            (paddingPx + (2 * BUTTON_DP + 2 * MARGIN_DP) * density.roundToInt()).toFloat()
        }
        return acceptLeft - slotLeft
    }

    private fun resizeWindow(widthPx: Int) {
        if (windowWidthPx == widthPx) return
        windowWidthPx = widthPx
        if (attached) windowManager.updateViewLayout(view, layoutParams())
    }

    private fun layoutParams(): WindowManager.LayoutParams {
        val metrics = view.context.resources.displayMetrics
        position.maxAcceptX = (metrics.widthPixels - paddingPx - buttonPx).coerceAtLeast(0)
        // The accept button is anchored to the window's left edge in both
        // compact and expanded layouts, so changing width does not move it.
        position.minAcceptX = paddingPx
        position.acceptX = position.acceptX.coerceIn(position.minAcceptX, position.maxAcceptX)
        position.maxY = (metrics.heightPixels - heightPx).coerceAtLeast(0)
        return WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            width = windowWidthPx
            height = heightPx
            gravity = Gravity.TOP or Gravity.START
            // Keep the window's left edge fixed while it expands to the right.
            x = position.acceptX - paddingPx
            y = position.y
        }
    }

    private companion object {
        const val ROW_WIDTH_DP = 212
        const val ROW_HEIGHT_DP = 52
        const val ROW_PADDING_DP = 10
        const val BUTTON_DP = 52
        const val MARGIN_DP = 8
        const val STANDARD_ICON_DP = 22
        const val DICTATOR_ICON_DP = 30
        const val EXTRA_MIN_SCALE_Y = 0.65f
        const val FADE_OUT_NUMERATOR = 9L
        const val FADE_OUT_DENOMINATOR = 10L
        const val FADE_IN_DELAY_NUMERATOR = 5L
        const val FADE_IN_DENOMINATOR = 100L
        const val SPINNER_DURATION_MS = 800L
        const val SLIDE_IN_MS = 320L
        const val SLIDE_OUT_MS = 210L
        const val WAVE_OUT_MS = 260L
        const val CANCEL_OUT_MS = 320L
        const val SLIDE_STAGGER_MS = 70L
    }
}

/** Stores the accept button position independently of the current window width. */
private class Position private constructor(
    private val prefs: android.content.SharedPreferences,
    var acceptX: Int,
    var y: Int,
) {
    var minAcceptX = 0
    var maxAcceptX = Int.MAX_VALUE
    var maxY = Int.MAX_VALUE

    fun save() {
        prefs.edit()
            .putInt(KEY_ACCEPT_X, acceptX)
            .putInt(KEY_Y, y)
            .remove(KEY_LEGACY_X)
            .apply()
    }

    companion object {
        private const val PREFS = "bubble_position"
        private const val KEY_ACCEPT_X = "accept_x"
        private const val KEY_LEGACY_X = "x"
        private const val KEY_Y = "y"

        fun load(
            context: android.content.Context,
            expandedWidthPx: Int,
            paddingPx: Int,
            buttonPx: Int,
        ): Position {
            val stored = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            val metrics = context.resources.displayMetrics
            val expandedAcceptOffset = expandedWidthPx - paddingPx - buttonPx
            val defaultWindowX = (metrics.widthPixels - expandedWidthPx * 2).coerceAtLeast(0)
            val defaultAcceptX = defaultWindowX + expandedAcceptOffset
            val acceptX = if (stored.contains(KEY_ACCEPT_X)) {
                stored.getInt(KEY_ACCEPT_X, defaultAcceptX)
            } else {
                // Before the compact idle window, x stored the expanded window's left edge.
                stored.getInt(KEY_LEGACY_X, defaultWindowX) + expandedAcceptOffset
            }
            val defaultY = metrics.heightPixels / 4
            return Position(
                stored,
                acceptX,
                stored.getInt(KEY_Y, defaultY),
            )
        }
    }
}
