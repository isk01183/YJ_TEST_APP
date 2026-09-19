package com.example.chargingapp

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.BatteryManager
import android.os.SystemClock
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 별을 읽는 성역 — V11 Reference Final
 *
 * 이미지 파일 없이 Android Canvas/Path/Gradient만으로 그리는 최종 레퍼런스 매치 화면.
 * 4/5 룬 밴드만 회전하고, 6/7/8 레이어는 정지 상태로 유지한다.
 */
class StellarSanctuaryView(context: Context) : View(context) {

    private val bg = Color.rgb(1, 4, 9)
    private val white = Color.rgb(255, 253, 247)
    private val ivory = Color.rgb(246, 237, 215)
    private val gold = Color.rgb(225, 190, 112)
    private val goldBright = Color.rgb(255, 238, 180)
    private val goldHot = Color.rgb(255, 249, 220)
    private val cyan = Color.rgb(95, 204, 247)
    private val cyanBright = Color.rgb(119, 226, 255)
    private val cyanDeep = Color.rgb(28, 99, 145)
    private val darkCore = Color.rgb(2, 7, 13)

    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
    }

    private val path = Path()

    private var batteryPercent = 69
    private var displayedBatteryPercent = 69f
    private var batteryTempC = 32.5f
    private var batteryHealthText = "양호"
    private var connectionText = "연결되지 않음"
    private var hasBatteryReading = false

    private var animationActive = false
    private var animationStartNanos = SystemClock.elapsedRealtimeNanos()
    private var lastFrameNanos = animationStartNanos

    private var staticSpaceLayer: Bitmap? = null
    private var dynamicGlowMultiplier = 1f

    init {
        setBackgroundColor(bg)
        isClickable = true
        isFocusable = true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animationActive = true
        animationStartNanos = SystemClock.elapsedRealtimeNanos()
        lastFrameNanos = animationStartNanos
        if (staticSpaceLayer == null && width > 0 && height > 0) {
            rebuildStaticSpaceLayer(width, height)
        }
        postInvalidateOnAnimation()
    }

    override fun onDetachedFromWindow() {
        animationActive = false
        staticSpaceLayer?.recycle()
        staticSpaceLayer = null
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        animationActive = visibility == VISIBLE
        if (animationActive) {
            lastFrameNanos = SystemClock.elapsedRealtimeNanos()
            postInvalidateOnAnimation()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildStaticSpaceLayer(w, h)
    }

    private fun rebuildStaticSpaceLayer(w: Int, h: Int) {
        staticSpaceLayer?.recycle()
        staticSpaceLayer = null
        if (w <= 0 || h <= 0) return

        try {
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bitmap)
            c.drawColor(bg)
            drawSpaceBackground(c, w.toFloat(), h.toFloat())
            staticSpaceLayer = bitmap
        } catch (_: OutOfMemoryError) {
            staticSpaceLayer = null
        }
    }

    fun updateFromBatteryIntent(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)

        if (level >= 0 && scale > 0) {
            batteryPercent = ((level * 100f) / scale).toInt().coerceIn(0, 100)
            if (!hasBatteryReading) {
                displayedBatteryPercent = batteryPercent.toFloat()
                hasBatteryReading = true
            }
        }

        batteryTempC =
            intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 325) / 10f

        batteryHealthText = when (
            intent.getIntExtra(
                BatteryManager.EXTRA_HEALTH,
                BatteryManager.BATTERY_HEALTH_UNKNOWN
            )
        ) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "양호"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "과열"
            BatteryManager.BATTERY_HEALTH_DEAD -> "점검 필요"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "과전압"
            BatteryManager.BATTERY_HEALTH_COLD -> "저온"
            else -> "확인 중"
        }

        connectionText = when (intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
            BatteryManager.BATTERY_PLUGGED_AC -> "유선 충전"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB 충전"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "무선 충전"
            else -> "연결되지 않음"
        }

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val now = SystemClock.elapsedRealtimeNanos()
        val elapsedSeconds = ((now - animationStartNanos) / 1_000_000_000.0).toFloat()
        val deltaSeconds = ((now - lastFrameNanos) / 1_000_000_000.0)
            .toFloat()
            .coerceIn(0f, 0.05f)
        lastFrameNanos = now

        displayedBatteryPercent = AnimationMath.damp(
            displayedBatteryPercent,
            batteryPercent.toFloat(),
            deltaSeconds,
            7f
        )

        canvas.drawColor(bg)

        val cached = staticSpaceLayer
        if (cached != null && !cached.isRecycled && cached.width == width && cached.height == height) {
            canvas.drawBitmap(cached, 0f, 0f, null)
        } else {
            drawSpaceBackground(canvas, w, h)
        }

        drawHeader(canvas, w, h)

        val cx = w * 0.5f
        val cy = h * 0.455f
        val r = min(w * 0.465f, h * 0.298f)

        val outerRotation =
            if (AnimationLayerPolicy.animateOuterRuneBand) {
                AnimationMath.wrapDegrees(elapsedSeconds * 1.9f)
            } else {
                0f
            }

        val innerRotation =
            if (AnimationLayerPolicy.animateInnerRuneBand) {
                -AnimationMath.wrapDegrees(elapsedSeconds * 2.6f)
            } else {
                0f
            }

        drawMagicCircle(
            canvas,
            cx,
            cy,
            r,
            outerRotation,
            innerRotation
        )

        drawCenterCore(
            canvas,
            cx,
            cy,
            r,
            displayedBatteryPercent
        )

        drawFooter(canvas, w, h)

        if (animationActive && windowVisibility == VISIBLE) {
            postInvalidateDelayed(16L)
        }
    }

    // ---------------------------------------------------------------------
    // Background
    // ---------------------------------------------------------------------

    private fun drawSpaceBackground(canvas: Canvas, w: Float, h: Float) {
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(
            w * 0.5f,
            h * 0.47f,
            w * 0.72f,
            intArrayOf(
                Color.argb(48, 8, 35, 63),
                Color.argb(24, 4, 16, 32),
                bg
            ),
            floatArrayOf(0f, 0.52f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, p)
        p.shader = null

        drawGalaxy(canvas, w * 0.055f, h * 0.145f, w * 0.26f, 18f, false)
        drawGalaxy(canvas, w * 0.935f, h * 0.215f, w * 0.235f, 205f, true)
        drawGalaxy(canvas, w * 0.085f, h * 0.825f, w * 0.21f, 122f, false)
        drawGalaxy(canvas, w * 0.920f, h * 0.795f, w * 0.205f, 300f, true)

        for (i in 0 until 280) {
            val x = pseudo(i * 17 + 9) * w
            val y = pseudo(i * 31 + 23) * h

            val inCenter =
                x in (w * 0.16f)..(w * 0.84f) &&
                    y in (h * 0.21f)..(h * 0.72f)

            if (inCenter && i % 4 != 0) continue

            val rr = when {
                i % 37 == 0 -> 3.1f
                i % 13 == 0 -> 1.55f
                else -> 0.72f
            }

            val color = when {
                i % 11 == 0 -> goldBright
                i % 5 == 0 -> cyanBright
                else -> white
            }

            p.style = Paint.Style.FILL
            p.color = withAlpha(
                color,
                if (i % 37 == 0) 220 else if (i % 13 == 0) 160 else 92
            )
            canvas.drawCircle(x, y, rr, p)

            if (i % 37 == 0) {
                drawStarBurst(canvas, x, y, w * 0.007f, color, 0.72f)
            }
        }

        drawConstellations(canvas, w, h)
        drawPlanet(canvas, w * 0.055f, h * 0.690f, w * 0.034f, true)
        drawPlanet(canvas, w * 0.945f, h * 0.720f, w * 0.036f, false)
    }

    private fun drawGalaxy(
        canvas: Canvas,
        gx: Float,
        gy: Float,
        size: Float,
        rotation: Float,
        mirror: Boolean
    ) {
        canvas.save()
        canvas.translate(gx, gy)
        canvas.rotate(rotation)
        if (mirror) canvas.scale(-1f, 1f)

        p.style = Paint.Style.FILL
        p.shader = RadialGradient(
            0f,
            0f,
            size * 0.40f,
            intArrayOf(
                Color.argb(110, 255, 238, 205),
                Color.argb(70, 80, 165, 240),
                Color.argb(20, 20, 75, 120),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.22f, 0.58f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(0f, 0f, size * 0.42f, p)
        p.shader = null

        for (arm in 0 until 4) {
            var previous: PointF? = null

            for (i in 0 until 110) {
                val t = i / 109f
                val a = arm * 90f + t * 520f
                val radius = size * (0.035f + t * 0.94f)
                val wave = sin((i + arm * 13) * 0.58).toFloat() * size * 0.026f

                val x = cosDeg(a) * (radius + wave)
                val y = sinDeg(a) * (radius + wave) * 0.62f

                val fade = 1f - t
                val color = when {
                    i % 7 == 0 -> goldBright
                    i % 3 == 0 -> white
                    else -> cyanBright
                }

                p.style = Paint.Style.FILL
                p.color = withAlpha(color, (38 + 170 * fade).toInt())
                canvas.drawCircle(
                    x,
                    y,
                    maxOf(0.60f, size * (0.0020f + 0.0052f * fade)),
                    p
                )

                if (i % 3 == 0 && previous != null) {
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = maxOf(0.45f, size * 0.00075f)
                    p.color = withAlpha(
                        if (arm % 2 == 0) cyanBright else gold,
                        (30 + 75 * fade).toInt()
                    )
                    canvas.drawLine(previous.x, previous.y, x, y, p)
                }

                previous = PointF(x, y)
            }
        }

        drawStarBurst(canvas, 0f, 0f, size * 0.09f, goldHot, 0.94f)
        canvas.restore()
    }

    private fun drawConstellations(canvas: Canvas, w: Float, h: Float) {
        val groups = listOf(
            listOf(
                PointF(w * 0.75f, h * 0.055f),
                PointF(w * 0.82f, h * 0.085f),
                PointF(w * 0.86f, h * 0.130f),
                PointF(w * 0.91f, h * 0.105f),
                PointF(w * 0.95f, h * 0.145f)
            ),
            listOf(
                PointF(w * 0.05f, h * 0.290f),
                PointF(w * 0.095f, h * 0.245f),
                PointF(w * 0.135f, h * 0.310f),
                PointF(w * 0.185f, h * 0.270f)
            ),
            listOf(
                PointF(w * 0.77f, h * 0.610f),
                PointF(w * 0.825f, h * 0.565f),
                PointF(w * 0.875f, h * 0.615f),
                PointF(w * 0.925f, h * 0.575f)
            )
        )

        for (group in groups) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = 0.9f
            p.color = Color.argb(75, 238, 206, 135)

            for (i in 0 until group.size - 1) {
                val a = group[i]
                val b = group[i + 1]
                canvas.drawLine(a.x, a.y, b.x, b.y, p)
            }

            for ((index, q) in group.withIndex()) {
                drawGlowDot(
                    canvas,
                    q.x,
                    q.y,
                    if (index % 2 == 0) 1.8f else 1.2f,
                    if (index % 2 == 0) goldBright else white,
                    0.68f
                )
            }
        }
    }

    private fun drawPlanet(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        r: Float,
        rightLit: Boolean
    ) {
        p.style = Paint.Style.FILL
        p.color = Color.argb(28, 62, 138, 210)
        canvas.drawCircle(cx, cy, r * 1.18f, p)

        p.color = Color.rgb(3, 8, 15)
        canvas.drawCircle(cx, cy, r, p)

        p.style = Paint.Style.STROKE
        p.strokeWidth = maxOf(1f, r * 0.075f)
        p.color = Color.argb(130, 98, 196, 245)
        canvas.drawCircle(cx, cy, r, p)

        val offset = if (rightLit) -r * 0.22f else r * 0.22f
        val rect = RectF(
            cx - r * 0.94f + offset,
            cy - r * 0.94f,
            cx + r * 0.94f + offset,
            cy + r * 0.94f
        )

        p.strokeWidth = maxOf(1.1f, r * 0.085f)
        p.color = Color.argb(185, 255, 226, 160)
        canvas.drawArc(
            rect,
            if (rightLit) -72f else 108f,
            144f,
            false,
            p
        )
    }

    // ---------------------------------------------------------------------
    // Header
    // ---------------------------------------------------------------------

    private fun drawHeader(canvas: Canvas, w: Float, h: Float) {
        val cx = w * 0.5f

        drawStarBurst(canvas, cx, h * 0.043f, w * 0.021f, goldHot, 0.98f)
        drawGlowDot(canvas, cx, h * 0.043f, w * 0.0020f, goldHot, 0.95f)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)

        drawTextGlow(
            canvas,
            "별을 읽는 성역",
            cx,
            h * 0.108f,
            w * 0.057f
        )

        p.style = Paint.Style.STROKE
        p.strokeWidth = maxOf(1f, w * 0.00145f)
        p.color = Color.argb(170, 240, 210, 140)
        canvas.drawLine(w * 0.13f, h * 0.140f, w * 0.87f, h * 0.140f, p)

        drawStarBurst(canvas, cx, h * 0.140f, w * 0.010f, goldHot, 0.88f)
        drawGlowDot(canvas, w * 0.13f, h * 0.140f, 1.8f, goldBright, 0.72f)
        drawGlowDot(canvas, w * 0.87f, h * 0.140f, 1.8f, goldBright, 0.72f)

        textPaint.color = ivory
        textPaint.textSize = w * 0.028f
        canvas.drawText("지혜는 내일을 비춘다.", cx, h * 0.177f, textPaint)
    }

    private fun drawTextGlow(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        size: Float
    ) {
        textPaint.textSize = size
        textPaint.typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)

        textPaint.color = Color.argb(28, 255, 229, 170)
        canvas.drawText(text, x - 1.8f, y, textPaint)
        canvas.drawText(text, x + 1.8f, y, textPaint)

        textPaint.color = goldBright
        canvas.drawText(text, x, y, textPaint)
    }

    // ---------------------------------------------------------------------
    // Magic circle
    // ---------------------------------------------------------------------

    private fun drawMagicCircle(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        r: Float,
        outerRuneRotation: Float,
        innerRuneRotation: Float
    ) {
        drawGlowRing(canvas, cx, cy, r * 1.002f, goldBright, r * 0.0055f, 1.00f)
        drawGlowRing(canvas, cx, cy, r * 0.976f, gold, r * 0.0032f, 0.90f)
        drawGlowRing(canvas, cx, cy, r * 0.948f, goldHot, r * 0.0017f, 0.78f)

        p.style = Paint.Style.STROKE
        p.strokeWidth = maxOf(0.8f, r * 0.0011f)
        p.color = withAlpha(goldBright, 130)
        canvas.drawCircle(cx, cy, r * 0.925f, p)
        canvas.drawCircle(cx, cy, r * 0.885f, p)

        drawTickRing(canvas, cx, cy, r * 0.988f, r * 0.030f, 144, 12)

        canvas.save()
        canvas.rotate(outerRuneRotation, cx, cy)
        drawRuneBand(
            canvas,
            cx,
            cy,
            r * ReferenceFinalSpec.outerRuneRadiusScale,
            ReferenceFinalSpec.outerRuneCount,
            goldBright,
            true
        )
        canvas.restore()

        drawCrescentMarker(canvas, cx, cy, r, 45f)
        drawCrescentMarker(canvas, cx, cy, r, 135f)
        drawCrescentMarker(canvas, cx, cy, r, 225f)
        drawCrescentMarker(canvas, cx, cy, r, 315f)

        drawCardinalFlare(canvas, cx, cy, r, 0f)
        drawCardinalFlare(canvas, cx, cy, r, 90f)
        drawCardinalFlare(canvas, cx, cy, r, 180f)
        drawCardinalFlare(canvas, cx, cy, r, 270f)

        drawGlowRing(canvas, cx, cy, r * 0.838f, cyanBright, r * 0.0046f, 0.96f)
        drawGlowRing(canvas, cx, cy, r * 0.802f, cyan, r * 0.0022f, 0.78f)

        p.style = Paint.Style.STROKE
        p.strokeWidth = maxOf(0.75f, r * 0.0012f)
        p.color = withAlpha(cyanBright, 145)
        canvas.drawCircle(cx, cy, r * 0.765f, p)
        canvas.drawCircle(cx, cy, r * 0.730f, p)

        canvas.save()
        canvas.rotate(innerRuneRotation, cx, cy)
        drawRuneBand(
            canvas,
            cx,
            cy,
            r * ReferenceFinalSpec.innerRuneRadiusScale,
            ReferenceFinalSpec.innerRuneCount,
            cyanBright,
            false
        )
        canvas.restore()

        val savedGlow = dynamicGlowMultiplier
        dynamicGlowMultiplier = 1f

        drawSacredGeometry(canvas, cx, cy, r)
        drawStaticOrbits(canvas, cx, cy, r)

        dynamicGlowMultiplier = savedGlow
    }

    private fun drawTickRing(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        tickLength: Float,
        count: Int,
        majorEvery: Int
    ) {
        for (i in 0 until count) {
            val a = i * 360f / count
            val major = i % majorEvery == 0
            val start = polar(
                cx,
                cy,
                radius - if (major) tickLength else tickLength * 0.42f,
                a
            )
            val end = polar(cx, cy, radius, a)

            p.style = Paint.Style.STROKE
            p.strokeWidth = if (major) 1.35f else 0.65f
            p.color = if (major) {
                withAlpha(goldBright, 185)
            } else {
                withAlpha(gold, 72)
            }

            canvas.drawLine(start.x, start.y, end.x, end.y, p)
        }
    }

    private fun drawRuneBand(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        count: Int,
        color: Int,
        large: Boolean
    ) {
        for (i in 0 until count) {
            val a = i * 360f / count
            val q = polar(cx, cy, radius, a)
            val size = radius * if (large) 0.022f else 0.016f

            canvas.save()
            canvas.rotate(a, q.x, q.y)
            drawRuneGlyph(
                canvas,
                q.x,
                q.y,
                size,
                i % 8,
                color,
                if (large) 0.92f else 0.80f
            )
            canvas.restore()
        }
    }

    private fun drawRuneGlyph(
        canvas: Canvas,
        x: Float,
        y: Float,
        s: Float,
        variant: Int,
        color: Int,
        alpha: Float
    ) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = maxOf(1f, s * 0.12f)
        p.color = withAlpha(color, (255 * alpha).toInt())

        when (variant) {
            0 -> {
                canvas.drawLine(x - s, y, x + s, y, p)
                canvas.drawLine(x, y - s, x, y + s, p)
            }

            1 -> {
                path.reset()
                path.moveTo(x - s, y + s * 0.75f)
                path.lineTo(x, y - s)
                path.lineTo(x + s, y + s * 0.75f)
                canvas.drawPath(path, p)
            }

            2 -> {
                path.reset()
                path.moveTo(x - s, y - s)
                path.lineTo(x + s * 0.75f, y - s * 0.25f)
                path.lineTo(x - s * 0.25f, y + s * 0.10f)
                path.lineTo(x + s, y + s)
                canvas.drawPath(path, p)
            }

            3 -> drawDiamond(canvas, x, y, s * 0.82f, color, alpha)

            4 -> drawPolygon(canvas, x, y, s * 0.86f, 3, 0f, color, alpha)

            5 -> {
                canvas.drawCircle(x, y, s * 0.68f, p)
                canvas.drawLine(x - s * 0.7f, y, x + s * 0.7f, y, p)
            }

            6 -> {
                canvas.drawLine(x - s, y - s, x + s, y + s, p)
                canvas.drawLine(x + s, y - s, x - s, y + s, p)
            }

            else -> {
                path.reset()
                path.moveTo(x - s, y)
                path.lineTo(x, y - s)
                path.lineTo(x + s, y)
                path.lineTo(x, y + s)
                canvas.drawPath(path, p)
            }
        }
    }

    private fun drawCrescentMarker(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        r: Float,
        angle: Float
    ) {
        val q = polar(cx, cy, r * 0.890f, angle)
        val rr = r * 0.036f

        p.style = Paint.Style.FILL
        p.color = goldBright
        canvas.drawCircle(q.x, q.y, rr, p)

        p.color = bg
        canvas.drawCircle(q.x + rr * 0.38f, q.y, rr * 0.86f, p)

        p.style = Paint.Style.STROKE
        p.strokeWidth = maxOf(0.8f, r * 0.0014f)
        p.color = withAlpha(goldHot, 190)
        canvas.drawCircle(q.x, q.y, rr, p)
    }

    private fun drawCardinalFlare(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        r: Float,
        angle: Float
    ) {
        val q = polar(cx, cy, r * 1.014f, angle)
        val s = r * 0.070f

        drawStarBurst(canvas, q.x, q.y, s, goldHot, 0.95f)

        p.style = Paint.Style.STROKE
        p.strokeWidth = maxOf(0.9f, r * 0.0014f)
        p.color = withAlpha(goldBright, 160)
        canvas.drawCircle(q.x, q.y, r * 0.030f, p)

        drawGlowDot(canvas, q.x, q.y, r * 0.009f, white, 0.95f)
    }

    private fun drawSacredGeometry(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        r: Float
    ) {
        drawStar(
            canvas,
            cx,
            cy,
            r * 0.615f,
            r * 0.260f,
            6,
            0f,
            goldHot,
            maxOf(1.5f, r * 0.0040f),
            0.90f
        )

        drawStar(
            canvas,
            cx,
            cy,
            r * 0.565f,
            r * 0.320f,
            8,
            22.5f,
            gold,
            maxOf(0.9f, r * 0.0023f),
            0.52f
        )

        drawPolygon(
            canvas,
            cx,
            cy,
            r * 0.500f,
            8,
            22.5f,
            goldBright,
            0.46f
        )

        drawPolygon(
            canvas,
            cx,
            cy,
            r * 0.415f,
            6,
            30f,
            cyan,
            0.26f
        )

        for (i in 0 until 24) {
            val a = i * 15f
            val start = polar(cx, cy, r * 0.255f, a)
            val end = polar(cx, cy, r * if (i % 3 == 0) 0.580f else 0.525f, a)

            p.style = Paint.Style.STROKE
            p.strokeWidth = maxOf(0.45f, r * if (i % 3 == 0) 0.00115f else 0.00075f)
            p.color = withAlpha(
                if (i % 2 == 0) goldBright else cyan,
                if (i % 3 == 0) 54 else 24
            )
            canvas.drawLine(start.x, start.y, end.x, end.y, p)
        }

        for (i in 0 until 12) {
            val q = polar(cx, cy, r * 0.545f, i * 30f)
            if (i % 2 == 0) {
                drawGlowDot(canvas, q.x, q.y, r * 0.0072f, goldBright, 0.78f)
            } else {
                drawGlowDot(canvas, q.x, q.y, r * 0.0052f, cyanBright, 0.52f)
            }
        }
    }

    private fun drawStaticOrbits(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        r: Float
    ) {
        val rotations = floatArrayOf(0f, 45f, 90f, 135f)
        val rx = floatArrayOf(0.585f, 0.570f, 0.552f, 0.540f)
        val ry = floatArrayOf(0.225f, 0.198f, 0.238f, 0.188f)

        for (i in 0 until ReferenceFinalSpec.orbitCount) {
            val rect = RectF(
                cx - r * rx[i],
                cy - r * ry[i],
                cx + r * rx[i],
                cy + r * ry[i]
            )

            canvas.save()
            canvas.rotate(rotations[i], cx, cy)

            p.style = Paint.Style.STROKE
            p.strokeWidth = maxOf(1.8f, r * 0.009f)
            p.color = withAlpha(cyanBright, 20)
            canvas.drawOval(rect, p)

            p.strokeWidth = maxOf(1.0f, r * 0.0045f)
            p.color = withAlpha(cyanBright, 66)
            canvas.drawOval(rect, p)

            p.strokeWidth = maxOf(0.85f, r * 0.0024f)
            p.color = withAlpha(if (i % 2 == 0) cyanBright else white, 235)
            canvas.drawOval(rect, p)

            canvas.restore()
        }

        for (i in 0 until ReferenceFinalSpec.primaryOrbitNodeCount) {
            val a = i * 45f
            val q = polar(cx, cy, r * 0.565f, a)
            val rr = r * if (i % 2 == 0) 0.011f else 0.0085f

            p.style = Paint.Style.FILL
            p.color = withAlpha(cyanBright, 22)
            canvas.drawCircle(q.x, q.y, rr * 4f, p)

            p.color = withAlpha(cyanBright, 60)
            canvas.drawCircle(q.x, q.y, rr * 2.3f, p)

            p.style = Paint.Style.STROKE
            p.strokeWidth = maxOf(0.75f, r * 0.0013f)
            p.color = withAlpha(if (i % 2 == 0) cyanBright else goldBright, 185)
            canvas.drawCircle(q.x, q.y, rr * 1.45f, p)

            p.style = Paint.Style.FILL
            p.color = white
            canvas.drawCircle(q.x, q.y, rr * 0.92f, p)
        }
    }

    // ---------------------------------------------------------------------
    // Center core
    // ---------------------------------------------------------------------

    private fun drawCenterCore(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        r: Float,
        displayPercent: Float
    ) {
        val coreR = r * ReferenceFinalSpec.coreRadiusScale

        p.style = Paint.Style.FILL
        p.shader = RadialGradient(
            cx - coreR * 0.18f,
            cy - coreR * 0.20f,
            coreR * 1.25f,
            intArrayOf(
                Color.rgb(12, 28, 43),
                Color.rgb(5, 13, 23),
                Color.rgb(1, 4, 8)
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, coreR, p)
        p.shader = null

        for (i in 0 until 34) {
            val a = i * 137.5f
            val rr = coreR * (0.18f + pseudo(i * 19 + 3) * 0.70f)
            val q = polar(cx, cy, rr, a)

            p.style = Paint.Style.FILL
            p.color = withAlpha(
                if (i % 5 == 0) cyanBright else white,
                if (i % 5 == 0) 78 else 40
            )
            canvas.drawCircle(q.x, q.y, if (i % 7 == 0) 1.25f else 0.72f, p)
        }

        drawGlowRing(canvas, cx, cy, coreR * 1.040f, cyanBright, r * 0.0040f, 0.92f)

        p.style = Paint.Style.STROKE
        p.strokeWidth = maxOf(1f, r * 0.0020f)
        p.color = withAlpha(goldBright, 165)
        canvas.drawCircle(cx, cy, coreR * 1.012f, p)

        val shown = displayPercent.roundToInt().coerceIn(0, 100)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        textPaint.color = goldHot
        textPaint.textSize = coreR * 0.67f
        canvas.drawText(
            shown.toString(),
            cx - coreR * 0.05f,
            cy + coreR * 0.06f,
            textPaint
        )

        textPaint.textSize = coreR * 0.24f
        canvas.drawText(
            "%",
            cx + coreR * 0.53f,
            cy + coreR * 0.07f,
            textPaint
        )

        p.style = Paint.Style.STROKE
        p.strokeWidth = maxOf(0.8f, r * 0.0011f)
        p.color = withAlpha(goldBright, 135)
        canvas.drawLine(
            cx - coreR * 0.56f,
            cy + coreR * 0.30f,
            cx + coreR * 0.56f,
            cy + coreR * 0.30f,
            p
        )

        drawStarBurst(
            canvas,
            cx,
            cy + coreR * 0.30f,
            coreR * 0.045f,
            goldBright,
            0.72f
        )

        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        textPaint.textSize = coreR * 0.17f
        textPaint.color = ivory
        canvas.drawText(
            connectionText,
            cx,
            cy + coreR * 0.58f,
            textPaint
        )
    }

    // ---------------------------------------------------------------------
    // Footer
    // ---------------------------------------------------------------------

    private fun drawFooter(canvas: Canvas, w: Float, h: Float) {
        val cx = w * 0.5f
        val topY = h * 0.805f

        p.style = Paint.Style.STROKE
        p.strokeWidth = maxOf(1f, w * 0.0014f)
        p.color = Color.argb(155, 235, 205, 135)

        canvas.drawLine(w * 0.13f, topY, w * 0.87f, topY, p)

        drawStarBurst(canvas, cx, topY, w * 0.009f, goldBright, 0.86f)
        drawGlowDot(canvas, w * 0.13f, topY, 1.7f, goldBright, 0.65f)
        drawGlowDot(canvas, w * 0.87f, topY, 1.7f, goldBright, 0.65f)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        textPaint.textSize = w * 0.027f
        textPaint.color = Color.rgb(238, 220, 180)

        canvas.drawText(
            "지혜는 더 밝은 내일을 비춘다.",
            cx,
            h * 0.845f,
            textPaint
        )

        val valueY = h * 0.905f

        drawInfo(
            canvas,
            w * 0.17f,
            valueY,
            "%.1f°C".format(batteryTempC),
            "배터리 온도",
            w
        )

        drawInfo(
            canvas,
            w * 0.50f,
            valueY,
            batteryHealthText,
            "배터리 상태",
            w
        )

        drawInfo(
            canvas,
            w * 0.83f,
            valueY,
            connectionText,
            "연결 방식",
            w
        )

        p.strokeWidth = maxOf(0.8f, w * 0.0010f)
        p.color = Color.argb(110, 235, 205, 145)

        canvas.drawLine(w * 0.335f, h * 0.870f, w * 0.335f, h * 0.945f, p)
        canvas.drawLine(w * 0.665f, h * 0.870f, w * 0.665f, h * 0.945f, p)

        textPaint.textSize = w * 0.022f
        textPaint.color = Color.rgb(240, 220, 170)

        canvas.drawText(
            "⋯⋯  ◔  ◑  ●  ◐  ◕  ⋯⋯",
            cx,
            h * 0.974f,
            textPaint
        )
    }

    private fun drawInfo(
        canvas: Canvas,
        x: Float,
        y: Float,
        value: String,
        label: String,
        w: Float
    ) {
        textPaint.typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        textPaint.color = ivory
        textPaint.textSize =
            w * if (value.length > 6) 0.032f else 0.041f

        canvas.drawText(value, x, y, textPaint)

        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        textPaint.color = Color.rgb(196, 180, 146)
        textPaint.textSize = w * 0.0215f

        canvas.drawText(label, x, y + w * 0.052f, textPaint)
    }

    // ---------------------------------------------------------------------
    // Drawing helpers
    // ---------------------------------------------------------------------

    private fun drawGlowRing(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        r: Float,
        color: Int,
        stroke: Float,
        alpha: Float
    ) {
        val a = (alpha * dynamicGlowMultiplier).coerceIn(0f, 1f)

        p.style = Paint.Style.STROKE

        p.strokeWidth = maxOf(1f, stroke * 7.2f)
        p.color = withAlpha(color, (18 * a).toInt())
        canvas.drawCircle(cx, cy, r, p)

        p.strokeWidth = maxOf(1f, stroke * 4.4f)
        p.color = withAlpha(color, (34 * a).toInt())
        canvas.drawCircle(cx, cy, r, p)

        p.strokeWidth = maxOf(1f, stroke * 2.2f)
        p.color = withAlpha(color, (72 * a).toInt())
        canvas.drawCircle(cx, cy, r, p)

        p.strokeWidth = maxOf(0.8f, stroke)
        p.color = withAlpha(color, (255 * a).toInt())
        canvas.drawCircle(cx, cy, r, p)
    }

    private fun drawGlowDot(
        canvas: Canvas,
        x: Float,
        y: Float,
        rr: Float,
        color: Int,
        alpha: Float
    ) {
        val a = (alpha * dynamicGlowMultiplier).coerceIn(0f, 1f)

        p.style = Paint.Style.FILL

        p.color = withAlpha(color, (20 * a).toInt())
        canvas.drawCircle(x, y, rr * 4.1f, p)

        p.color = withAlpha(color, (48 * a).toInt())
        canvas.drawCircle(x, y, rr * 2.5f, p)

        p.color = withAlpha(color, (105 * a).toInt())
        canvas.drawCircle(x, y, rr * 1.55f, p)

        p.color = withAlpha(color, (255 * a).toInt())
        canvas.drawCircle(x, y, rr, p)

        p.color = withAlpha(white, (235 * a).toInt())
        canvas.drawCircle(x, y, maxOf(0.5f, rr * 0.28f), p)
    }

    private fun drawPolygon(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        r: Float,
        sides: Int,
        rotation: Float,
        color: Int,
        alpha: Float
    ) {
        path.reset()

        for (i in 0 until sides) {
            val q = polar(
                cx,
                cy,
                r,
                rotation + i * 360f / sides
            )

            if (i == 0) {
                path.moveTo(q.x, q.y)
            } else {
                path.lineTo(q.x, q.y)
            }
        }

        path.close()

        p.style = Paint.Style.STROKE
        p.strokeWidth = maxOf(0.65f, r * 0.004f)
        p.color = withAlpha(color, (255 * alpha).toInt())

        canvas.drawPath(path, p)
    }

    private fun drawStar(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        outer: Float,
        inner: Float,
        tips: Int,
        rotation: Float,
        color: Int,
        width: Float,
        alpha: Float
    ) {
        path.reset()

        for (i in 0 until tips * 2) {
            val rr = if (i % 2 == 0) outer else inner
            val q = polar(
                cx,
                cy,
                rr,
                rotation + i * 180f / tips
            )

            if (i == 0) {
                path.moveTo(q.x, q.y)
            } else {
                path.lineTo(q.x, q.y)
            }
        }

        path.close()

        p.style = Paint.Style.STROKE
        p.strokeWidth = width
        p.color = withAlpha(color, (255 * alpha).toInt())

        canvas.drawPath(path, p)
    }

    private fun drawDiamond(
        canvas: Canvas,
        x: Float,
        y: Float,
        size: Float,
        color: Int,
        alpha: Float
    ) {
        path.reset()
        path.moveTo(x, y - size)
        path.lineTo(x + size, y)
        path.lineTo(x, y + size)
        path.lineTo(x - size, y)
        path.close()

        p.style = Paint.Style.STROKE
        p.strokeWidth = maxOf(0.8f, size * 0.12f)
        p.color = withAlpha(color, (255 * alpha).toInt())

        canvas.drawPath(path, p)
    }

    private fun drawStarBurst(
        canvas: Canvas,
        x: Float,
        y: Float,
        size: Float,
        color: Int,
        alpha: Float
    ) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = maxOf(0.9f, size * 0.075f)
        p.color = withAlpha(color, (255 * alpha).toInt())

        canvas.drawLine(x - size, y, x + size, y, p)
        canvas.drawLine(x, y - size, x, y + size, p)

        canvas.drawLine(
            x - size * 0.52f,
            y - size * 0.52f,
            x + size * 0.52f,
            y + size * 0.52f,
            p
        )

        canvas.drawLine(
            x + size * 0.52f,
            y - size * 0.52f,
            x - size * 0.52f,
            y + size * 0.52f,
            p
        )
    }

    private fun polar(
        cx: Float,
        cy: Float,
        r: Float,
        degrees: Float
    ): PointF {
        val rad = Math.toRadians((degrees - 90f).toDouble())

        return PointF(
            cx + (cos(rad) * r).toFloat(),
            cy + (sin(rad) * r).toFloat()
        )
    }

    private fun cosDeg(degrees: Float): Float =
        cos(Math.toRadians(degrees.toDouble())).toFloat()

    private fun sinDeg(degrees: Float): Float =
        sin(Math.toRadians(degrees.toDouble())).toFloat()

    private fun pseudo(seed: Int): Float {
        val v = abs(sin(seed * 12.9898) * 43758.5453)
        return (v - v.toInt()).toFloat()
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        return Color.argb(
            alpha.coerceIn(0, 255),
            Color.red(color),
            Color.green(color),
            Color.blue(color)
        )
    }
}
