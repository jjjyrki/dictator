package io.jyri.dictator.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/**
 * Pill-shaped live waveform for the recording chip: a row of rounded bars
 * driven by the microphone level. Level frames arrive at ~10 Hz, so incoming
 * values are smoothed and bar heights ease between updates to stay fluid.
 */
class WaveChipView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val levels = FloatArray(BAR_COUNT)
    private val display = FloatArray(BAR_COUNT)
    private var smoothedLive = 0f
    private var lastFrameTimeMs = 0L
    private var live = false
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }

    fun setBarColor(color: Int) {
        barPaint.color = color
        invalidate()
    }

    fun setActive(value: Boolean) {
        live = value
        if (!live) {
            for (i in levels.indices) {
                levels[i] = 0f
                display[i] = 0f
            }
            smoothedLive = 0f
        }
        postInvalidateDelayed(FRAME_INTERVAL_MS)
    }

    /** Raw microphone RMS, expected roughly 0..1 after gain. */
    fun setLevel(rawLevel: Float) {
        // Gate the amplified room-noise floor so the chip rests in silence
        // instead of the traveling wave breathing on top of it.
        val clamped = if (rawLevel < NOISE_GATE) 0f else rawLevel.coerceIn(0f, 1f)
        // Fast attack, slow release keeps peaks lively without flicker.
        smoothedLive = if (clamped > smoothedLive) {
            smoothedLive + (clamped - smoothedLive) * ATTACK
        } else {
            smoothedLive + (clamped - smoothedLive) * RELEASE
        }
        System.arraycopy(levels, 0, levels, 1, levels.size - 1)
        levels[levels.size - 1] = smoothedLive
        postInvalidateDelayed(FRAME_INTERVAL_MS)
    }

    override fun onDraw(canvas: Canvas) {
        val density = resources.displayMetrics.density
        val barWidth = 4f * density
        val gap = 3f * density
        val maxBarHeight = height - 2f * VERTICAL_PADDING_DP * density
        val centerY = height / 2f
        val totalWidth = BAR_COUNT * barWidth + (BAR_COUNT - 1) * gap
        var x = (width - totalWidth) / 2f
        val now = SystemClock.uptimeMillis()
        val dtSeconds = if (lastFrameTimeMs == 0L) 0f else (now - lastFrameTimeMs) / 1_000f
        lastFrameTimeMs = now
        val ease = 1f - exp(-dtSeconds * EASE_RATE)
        val time = now / 1_000f
        val liveSample = levels.last()
        for (i in levels.indices) {
            // Newest bar on the right; history flows left. Blend in the current
            // level with a traveling wave so all bars react while speaking.
            val history = levels[(levels.size - 1 - i).coerceIn(0, levels.size - 1)]
            val wave = 0.4f + 0.6f * ((sin(time * WAVE_SPEED + i * WAVE_PHASE) + 1f) / 2f)
            val target = max(history, liveSample * wave)
            display[i] += (target - display[i]) * ease
            val heightFraction = 0.12f + display[i].coerceIn(0f, 1f) * 0.88f
            val barHeight = maxBarHeight * heightFraction
            canvas.drawRoundRect(
                x,
                centerY - barHeight / 2f,
                x + barWidth,
                centerY + barHeight / 2f,
                barWidth / 2f,
                barWidth / 2f,
                barPaint,
            )
            x += barWidth + gap
        }
        if (live) postInvalidateDelayed(FRAME_INTERVAL_MS)
    }

    private companion object {
        const val BAR_COUNT = 7
        const val VERTICAL_PADDING_DP = 6f
        const val WAVE_SPEED = 12f
        const val WAVE_PHASE = 1.1f
        const val ATTACK = 0.55f
        const val RELEASE = 0.22f
        // Above the 18x-gained quiet-room floor, well below normal speech.
        const val NOISE_GATE = 0.08f
        // The microphone level arrives at ~10 Hz; 30 fps is enough for the
        // cosmetic easing while avoiding unnecessary display wakeups.
        const val FRAME_INTERVAL_MS = 33L
        const val EASE_RATE = 18f
    }
}
