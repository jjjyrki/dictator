package io.jyri.dictator.overlay

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.SweepGradient
import android.os.SystemClock
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Decorative listening visualizer drawn behind the bubble label. A random
 * animation style is picked for every dictation; the live microphone level
 * drives motion. Not focusable and excluded from accessibility.
 */
class BubbleVisualizer(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    enum class Style {
        Pulse,
        Rings,
        WaveformBars,
        OrbitDots,
        ArcSweep,
        Breathing,
        Sparks,
        Wobble,
        Squishy,
    }

    private var style = Style.Pulse
    private var active = false
    private var synthetic = false
    private var level = 0f
    private var smoothedLevel = 0f
    private var seed = 0f
    private val frameTimes = FloatArray(BAR_COUNT)
    private var barIndex = 0
    private val sparks = Array(SPARK_COUNT) { FloatArray(3) } // angle, distance, speed
    private var sparksSeeded = false

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val rect = RectF()

    fun onDictationStart() {
        style = Style.entries[Random.nextInt(Style.entries.size)]
        startCommon(synthetic = false)
    }

    /** Shows one fixed style with a synthetic voice level, for the test bench. */
    fun preview(style: Style) {
        this.style = style
        startCommon(synthetic = true)
    }

    private fun startCommon(synthetic: Boolean) {
        seed = Random.nextFloat() * 100f
        sparksSeeded = false
        level = 0f
        smoothedLevel = 0f
        this.synthetic = synthetic
        active = true
        visibility = VISIBLE
        postInvalidateOnAnimation()
    }

    fun onDictationStop() {
        active = false
        synthetic = false
        level = 0f
        smoothedLevel = 0f
        invalidate()
    }

    /** Raw microphone RMS, expected roughly 0..1 after gain. */
    fun setLevel(rawLevel: Float) {
        level = rawLevel.coerceIn(0f, 1f)
    }

    override fun onDraw(canvas: Canvas) {
        if (!active) {
            visibility = INVISIBLE
            return
        }
        val time = SystemClock.uptimeMillis() / 1000f
        // Fast attack, slow release keeps the visual lively but stable.
        if (synthetic) {
            level = syntheticLevel(time)
        }
        smoothedLevel = if (level > smoothedLevel) {
            smoothedLevel + (level - smoothedLevel) * 0.5f
        } else {
            smoothedLevel + (level - smoothedLevel) * 0.12f
        }
        val center = width / 2f
        val centerY = height / 2f
        val maxRadius = min(center, centerY) - 4f
        val innerRadius = maxRadius * 0.62f
        when (style) {
            Style.Pulse -> drawPulse(canvas, center, centerY, maxRadius, time)
            Style.Rings -> drawRings(canvas, center, centerY, maxRadius, time)
            Style.WaveformBars -> drawWaveformBars(canvas, center, centerY, maxRadius, time)
            Style.OrbitDots -> drawOrbitDots(canvas, center, centerY, maxRadius, time)
            Style.ArcSweep -> drawArcSweep(canvas, center, centerY, maxRadius, time)
            Style.Breathing -> drawBreathing(canvas, center, centerY, maxRadius, time)
            Style.Sparks -> drawSparks(canvas, center, centerY, innerRadius, maxRadius, time)
            Style.Wobble -> drawWobble(canvas, center, centerY, maxRadius, time)
            Style.Squishy -> drawSquishy(canvas, center, centerY, maxRadius, time)
        }
        postInvalidateOnAnimation()
    }

    /** Simulates conversational speech: syllable bursts with pauses. */
    private fun syntheticLevel(time: Float): Float {
        val envelope = 0.5f + 0.5f * sin(time * 1.7f + seed)
        val syllables = 0.5f + 0.5f * sin(time * 9f + seed * 2f)
        val burst = if (sin(time * 0.5f + seed) > -0.3f) 1f else 0.15f
        return (envelope * syllables * burst).coerceIn(0f, 1f)
    }

    private fun drawPulse(canvas: Canvas, cx: Float, cy: Float, maxRadius: Float, time: Float) {
        val phase = (time * 1.6f + seed) % 1f
        val radius = maxRadius * (0.66f + phase * 0.34f)
        ringPaint.color = accent(phase / 1.2f)
        ringPaint.strokeWidth = 6f * (1f - phase) + 1f
        canvas.drawCircle(cx, cy, radius, ringPaint)
        ringPaint.color = accent(smoothedLevel)
        ringPaint.strokeWidth = 3f
        canvas.drawCircle(cx, cy, maxRadius * (0.7f + smoothedLevel * 0.12f), ringPaint)
    }

    private fun drawRings(canvas: Canvas, cx: Float, cy: Float, maxRadius: Float, time: Float) {
        for (i in 0 until RING_COUNT) {
            val phase = ((time * 0.9f + seed) + i / RING_COUNT.toFloat()) % 1f
            val radius = maxRadius * (0.6f + phase * 0.4f) * (1f + smoothedLevel * 0.1f)
            ringPaint.color = accent(phase / 1.4f + i * 0.06f)
            ringPaint.strokeWidth = 4f * (1f - phase) + 1f
            canvas.drawCircle(cx, cy, radius, ringPaint)
        }
    }

    private fun drawWaveformBars(canvas: Canvas, cx: Float, cy: Float, maxRadius: Float, time: Float) {
        frameTimes[barIndex % BAR_COUNT] = level
        barIndex++
        pathPaint.strokeWidth = 5f
        for (i in 0 until BAR_COUNT) {
            val angle = (PI * 2.0 * i / BAR_COUNT + seed).toFloat()
            val history = frameTimes[(barIndex - 1 - i + BAR_COUNT * 4) % BAR_COUNT]
            val inner = maxRadius * 0.7f
            val outer = inner + maxRadius * (0.06f + history * 0.3f)
            pathPaint.color = accent(i / BAR_COUNT.toFloat() + time * 0.2f)
            canvas.drawLine(
                cx + cos(angle) * inner,
                cy + sin(angle) * inner,
                cx + cos(angle) * outer,
                cy + sin(angle) * outer,
                pathPaint,
            )
        }
    }

    private fun drawOrbitDots(canvas: Canvas, cx: Float, cy: Float, maxRadius: Float, time: Float) {
        fillPaint.color = accent(0.2f)
        val orbit = maxRadius * (0.72f + smoothedLevel * 0.15f)
        for (i in 0 until DOT_COUNT) {
            val angle = time * (1.2f + i * 0.35f) + i * (PI * 2.0 / DOT_COUNT) + seed
            val radius = orbit * (1f + 0.08f * sin(time * 3f + i))
            canvas.drawCircle(
                cx + cos(angle.toFloat()) * radius,
                cy + sin(angle.toFloat()) * radius,
                5f + smoothedLevel * 6f,
                fillPaint,
            )
        }
    }

    private fun drawArcSweep(canvas: Canvas, cx: Float, cy: Float, maxRadius: Float, time: Float) {
        val rotation = (time * 120f + seed * 60f) % 360f
        val sweep = 40f + smoothedLevel * 200f
        val gradient = SweepGradient(
            cx,
            cy,
            intArrayOf(Color.TRANSPARENT, accent(0.5f), accent(0.1f), Color.TRANSPARENT),
            floatArrayOf(0f, 0.5f, 0.9f, 1f),
        )
        ringPaint.shader = gradient
        ringPaint.strokeWidth = 7f + smoothedLevel * 5f
        rect.set(cx - maxRadius * 0.8f, cy - maxRadius * 0.8f, cx + maxRadius * 0.8f, cy + maxRadius * 0.8f)
        canvas.rotate(rotation, cx, cy)
        canvas.drawArc(rect, 0f, sweep, false, ringPaint)
        canvas.rotate(-rotation, cx, cy)
        ringPaint.shader = null
    }

    private fun drawBreathing(canvas: Canvas, cx: Float, cy: Float, maxRadius: Float, time: Float) {
        val breathe = 0.5f + 0.5f * sin(time * 2f + seed)
        val radius = maxRadius * (0.62f + breathe * 0.12f + smoothedLevel * 0.18f)
        fillPaint.color = accent(0.45f - breathe * 0.2f)
        fillPaint.alpha = ((0.25f + smoothedLevel * 0.5f) * 255).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, cy, radius, fillPaint)
        ringPaint.color = accent(0.1f)
        ringPaint.strokeWidth = 3f
        canvas.drawCircle(cx, cy, radius + 8f, ringPaint)
    }

    private fun drawSparks(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        innerRadius: Float,
        maxRadius: Float,
        time: Float,
    ) {
        if (!sparksSeeded) {
            for (spark in sparks) {
                spark[0] = Random.nextFloat() * (PI * 2).toFloat()
                spark[1] = innerRadius
                spark[2] = 40f + Random.nextFloat() * 90f
            }
            sparksSeeded = true
        }
        fillPaint.color = accent(0.25f)
        for (spark in sparks) {
            spark[1] += spark[2] * (0.4f + smoothedLevel * 2.2f) / 60f
            if (spark[1] > maxRadius) {
                spark[1] = innerRadius
                spark[0] = Random.nextFloat() * (PI * 2).toFloat()
            }
            val alpha = (1f - (spark[1] - innerRadius) / (maxRadius - innerRadius)).coerceIn(0f, 1f)
            fillPaint.alpha = (alpha * 255).toInt()
            canvas.drawCircle(
                cx + cos(spark[0]) * spark[1],
                cy + sin(spark[0]) * spark[1],
                3f + smoothedLevel * 3f,
                fillPaint,
            )
        }
        fillPaint.alpha = 255
    }

    /** A translucent green blob that bounces and sings with the sound wave. */
    private fun drawSquishy(canvas: Canvas, cx: Float, cy: Float, maxRadius: Float, time: Float) {
        val beat = (sin(time * 18f) * 0.5f + 0.5f) * smoothedLevel
        val squash = 0.12f * smoothedLevel + 0.05f * beat
        val baseRadius = maxRadius * 0.55f

        canvas.save()
        canvas.translate(cx, cy + baseRadius * 0.15f)
        canvas.scale(1f + squash, 1f - squash)
        canvas.translate(-cx, -cy)

        // Body: wobbling blob outline.
        val body = Path()
        val points = 64
        for (i in 0..points) {
            val fraction = i.toFloat() / points
            val angle = fraction * (PI * 2).toFloat()
            val wobble = 1f +
                0.05f * sin(3f * angle + time * 6f + seed) +
                0.22f * smoothedLevel * sin(5f * angle - time * 11f) +
                0.06f * sin(7f * angle + time * 3f)
            val radius = baseRadius * wobble
            val x = cx + cos(angle) * radius
            val y = cy + sin(angle) * radius
            if (i == 0) body.moveTo(x, y) else body.lineTo(x, y)
        }
        body.close()
        fillPaint.color = Color.argb(225, 106, 226, 108)
        canvas.drawPath(body, fillPaint)
        ringPaint.color = Color.argb(255, 62, 180, 66)
        ringPaint.strokeWidth = 3f
        canvas.drawPath(body, ringPaint)

        // Eyes: blink occasionally; pupils glance toward the loudest side.
        val blinkPhase = sin(time * 0.9f + seed * 3f)
        val eyeOpen = if (blinkPhase > 0.985f) 0.12f else 1f
        val eyeOffsetY = -baseRadius * 0.22f
        val eyeOffsetX = baseRadius * 0.28f
        val eyeRadiusX = baseRadius * 0.16f
        val eyeRadiusY = eyeRadiusX * (0.6f + smoothedLevel * 0.5f) * eyeOpen
        fillPaint.color = Color.WHITE
        canvas.drawOval(
            rectOf(cx - eyeOffsetX - eyeRadiusX, cy + eyeOffsetY - eyeRadiusY, cx - eyeOffsetX + eyeRadiusX, cy + eyeOffsetY + eyeRadiusY),
            fillPaint,
        )
        canvas.drawOval(
            rectOf(cx + eyeOffsetX - eyeRadiusX, cy + eyeOffsetY - eyeRadiusY, cx + eyeOffsetX + eyeRadiusX, cy + eyeOffsetY + eyeRadiusY),
            fillPaint,
        )
        val pupilShift = smoothedLevel * eyeRadiusX * 0.5f
        fillPaint.color = Color.argb(255, 20, 40, 20)
        canvas.drawCircle(cx - eyeOffsetX + pupilShift, cy + eyeOffsetY, eyeRadiusX * 0.4f * eyeOpen + 1f, fillPaint)
        canvas.drawCircle(cx + eyeOffsetX + pupilShift, cy + eyeOffsetY, eyeRadiusX * 0.4f * eyeOpen + 1f, fillPaint)

        // Mouth: opens with the level like a singing mouth.
        val mouthOpen = baseRadius * (0.06f + smoothedLevel * 0.5f)
        fillPaint.color = Color.argb(255, 30, 20, 20)
        canvas.drawOval(
            rectOf(cx - baseRadius * 0.2f, cy + baseRadius * 0.18f - mouthOpen, cx + baseRadius * 0.2f, cy + baseRadius * 0.18f + mouthOpen),
            fillPaint,
        )

        // Highlight.
        fillPaint.color = Color.argb(90, 255, 255, 255)
        canvas.drawOval(
            rectOf(cx - baseRadius * 0.55f, cy - baseRadius * 0.62f, cx - baseRadius * 0.15f, cy - baseRadius * 0.3f),
            fillPaint,
        )
        fillPaint.alpha = 255
        canvas.restore()
    }

    private fun rectOf(left: Float, top: Float, right: Float, bottom: Float): RectF {
        rect.set(left, top, right, bottom)
        return rect
    }

    private fun drawWobble(canvas: Canvas, cx: Float, cy: Float, maxRadius: Float, time: Float) {
        pathPaint.color = accent(0.3f)
        pathPaint.strokeWidth = 4f
        pathPaint.style = Paint.Style.STROKE
        val path = Path()
        val points = 48
        for (i in 0..points) {
            val fraction = i.toFloat() / points
            val angle = fraction * (PI * 2).toFloat()
            val wobble = 1f +
                0.05f * sin(fraction * 9f + time * 4f + seed) +
                smoothedLevel * 0.16f * sin(fraction * 5f - time * 2.4f)
            val radius = maxRadius * 0.75f * wobble
            val x = cx + cos(angle) * radius
            val y = cy + sin(angle) * radius
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, pathPaint)
    }

    private fun accent(offset: Float): Int {
        val hue = (BASE_HUE + offset * 40f + smoothedLevel * 30f).mod(360f)
        return Color.HSVToColor(floatArrayOf(hue, 0.62f, 0.95f))
    }

    private companion object {
        const val BASE_HUE = 190f
        const val RING_COUNT = 3
        const val BAR_COUNT = 24
        const val DOT_COUNT = 5
        const val SPARK_COUNT = 12
    }
}
