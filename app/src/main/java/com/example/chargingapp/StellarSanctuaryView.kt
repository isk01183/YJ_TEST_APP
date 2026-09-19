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
 * 별을 읽는 성역 — V12 Reference Rebuild
 *
 * 1080x2400 가상 좌표계에서 원본 레퍼런스의 비율을 우선 재현한다.
 * 이미지 리소스는 사용하지 않고 Canvas / Path / Gradient로만 렌더링한다.
 * Layer 4/5 룬만 회전하며 Layer 6/7/8은 정지한다.
 */
class StellarSanctuaryView(context: Context) : View(context) {

    private val bg = Color.rgb(1, 4, 9)
    private val white = Color.rgb(255, 253, 247)
    private val ivory = Color.rgb(247, 236, 211)
    private val gold = Color.rgb(225, 187, 105)
    private val goldBright = Color.rgb(255, 236, 176)
    private val goldHot = Color.rgb(255, 249, 224)
    private val cyan = Color.rgb(87, 198, 244)
    private val cyanBright = Color.rgb(115, 228, 255)
    private val cyanDeep = Color.rgb(23, 91, 142)
    private val darkCore = Color.rgb(2, 7, 14)

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

    private var animationStartNanos = SystemClock.elapsedRealtimeNanos()
    private var lastFrameNanos = animationStartNanos
    private var animationActive = false

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

            val scale = min(
                w / ReferenceV12Spec.virtualWidth,
                h / ReferenceV12Spec.virtualHeight
            )

            val offsetX =
                (w - ReferenceV12Spec.virtualWidth * scale) * 0.5f
            val offsetY =
                (h - ReferenceV12Spec.virtualHeight * scale) * 0.5f

            c.save()
            c.translate(offsetX, offsetY)
            c.scale(scale, scale)
            drawSpaceBackground(c)
            c.restore()

            staticSpaceLayer = bitmap
        } catch (_: OutOfMemoryError) {
            staticSpaceLayer = null
        }
    }

    fun updateFromBatteryIntent(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)

        if (level >= 0 && scale > 0) {
            batteryPercent =
                ((level * 100f) / scale).toInt().coerceIn(0, 100)

            if (!hasBatteryReading) {
                displayedBatteryPercent = batteryPercent.toFloat()
                hasBatteryReading = true
            }
        }

        batteryTempC =
            intent.getIntExtra(
                BatteryManager.EXTRA_TEMPERATURE,
                325
            ) / 10f

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

        connectionText = when (
            intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        ) {
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
        val elapsed =
            ((now - animationStartNanos) / 1_000_000_000.0).toFloat()
        val delta =
            ((now - lastFrameNanos) / 1_000_000_000.0)
                .toFloat()
                .coerceIn(0f, 0.05f)

        lastFrameNanos = now

        displayedBatteryPercent = AnimationMath.damp(
            displayedBatteryPercent,
            batteryPercent.toFloat(),
            delta,
            7f
        )

        canvas.drawColor(bg)

        val cached = staticSpaceLayer
        if (
            cached != null &&
            !cached.isRecycled &&
            cached.width == width &&
            cached.height == height
        ) {
            canvas.drawBitmap(cached, 0f, 0f, null)
        } else {
            val scale = min(
                w / ReferenceV12Spec.virtualWidth,
                h / ReferenceV12Spec.virtualHeight
            )

            val offsetX =
                (w - ReferenceV12Spec.virtualWidth * scale) * 0.5f
            val offsetY =
                (h - ReferenceV12Spec.virtualHeight * scale) * 0.5f

            canvas.save()
            canvas.translate(offsetX, offsetY)
            canvas.scale(scale, scale)
            drawSpaceBackground(canvas)
            canvas.restore()
        }

        val scale = min(
            w / ReferenceV12Spec.virtualWidth,
            h / ReferenceV12Spec.virtualHeight
        )

        val offsetX =
            (w - ReferenceV12Spec.virtualWidth * scale) * 0.5f
        val offsetY =
            (h - ReferenceV12Spec.virtualHeight * scale) * 0.5f

        val outerRotation =
            if (AnimationLayerPolicy.animateOuterRuneBand) {
                AnimationMath.wrapDegrees(elapsed * 1.45f)
            } else {
                0f
            }

        val innerRotation =
            if (AnimationLayerPolicy.animateInnerRuneBand) {
                -AnimationMath.wrapDegrees(elapsed * 2.05f)
            } else {
                0f
            }

        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(scale, scale)

        drawHeader(canvas)

        drawMagicCircle(
            canvas,
            ReferenceV12Spec.magicCenterX,
            ReferenceV12Spec.magicCenterY,
            ReferenceV12Spec.magicRadius,
            outerRotation,
            innerRotation
        )

        drawCenterCore(
            canvas,
            ReferenceV12Spec.magicCenterX,
            ReferenceV12Spec.magicCenterY,
            ReferenceV12Spec.coreRadius,
            displayedBatteryPercent
        )

        drawFooter(canvas)

        canvas.restore()

        if (animationActive && windowVisibility == VISIBLE) {
            postInvalidateDelayed(16L)
        }
    }

    // ------------------------------------------------------------------
    // Static space background
    // ------------------------------------------------------------------

    private fun drawSpaceBackground(canvas: Canvas) {
        val vw = ReferenceV12Spec.virtualWidth
        val vh = ReferenceV12Spec.virtualHeight

        p.style = Paint.Style.FILL
        p.shader = RadialGradient(
            vw * 0.5f,
            vh * 0.46f,
            980f,
            intArrayOf(
                Color.argb(48, 10, 39, 72),
                Color.argb(25, 4, 18, 36),
                bg
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, vw, vh, p)
        p.shader = null

        drawGalaxy(canvas, 95f, 205f, 325f, 18f, false)
        drawGalaxy(canvas, 980f, 335f, 300f, 205f, true)
        drawGalaxy(canvas, 95f, 1975f, 265f, 118f, false)
        drawGalaxy(canvas, 975f, 1910f, 285f, 302f, true)

        for (i in 0 until 340) {
            val x = pseudo(i * 17 + 7) * vw
            val y = pseudo(i * 29 + 19) * vh

            val centerProtected =
                x in 125f..955f &&
                    y in 420f..1660f

            if (centerProtected && i % 4 != 0) continue

            val radius = when {
                i % 43 == 0 -> 3.2f
                i % 17 == 0 -> 1.8f
                i % 7 == 0 -> 1.1f
                else -> 0.65f
            }

            val color = when {
                i % 13 == 0 -> goldBright
                i % 5 == 0 -> cyanBright
                else -> white
            }

            p.style = Paint.Style.FILL
            p.color = withAlpha(
                color,
                when {
                    i % 43 == 0 -> 235
                    i % 17 == 0 -> 175
                    else -> 95
                }
            )
            canvas.drawCircle(x, y, radius, p)

            if (i % 43 == 0) {
                drawStarBurst(
                    canvas,
                    x,
                    y,
                    15f,
                    color,
                    0.72f
                )
            }
        }

        drawConstellation(
            canvas,
            arrayOf(
                PointF(760f, 80f),
                PointF(825f, 112f),
                PointF(870f, 168f),
                PointF(930f, 132f),
                PointF(996f, 188f)
            )
        )

        drawConstellation(
            canvas,
            arrayOf(
                PointF(42f, 520f),
                PointF(100f, 460f),
                PointF(155f, 535f),
                PointF(215f, 485f)
            )
        )

        drawConstellation(
            canvas,
            arrayOf(
                PointF(830f, 1690f),
                PointF(880f, 1620f),
                PointF(938f, 1680f),
                PointF(1010f, 1615f)
            )
        )

        drawPlanet(canvas, 62f, 1700f, 48f, true)
        drawPlanet(canvas, 1010f, 1770f, 54f, false)
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

        if (mirror) {
            canvas.scale(-1f, 1f)
        }

        drawNebulaCloud(
            canvas,
            0f,
            0f,
            size * 0.34f,
            Color.argb(120, 255, 230, 188),
            Color.argb(75, 60, 164, 242)
        )

        for (arm in 0 until 4) {
            var previous: PointF? = null

            for (i in 0 until 120) {
                val t = i / 119f
                val angle = arm * 90f + t * 540f

                val radius =
                    size * (0.025f + t * 0.96f)

                val wave =
                    sin((i + arm * 17) * 0.57)
                        .toFloat() * size * 0.026f

                val x =
                    cosDeg(angle) * (radius + wave)
                val y =
                    sinDeg(angle) * (radius + wave) * 0.62f

                val fade = 1f - t

                if (i % 8 == 0) {
                    drawNebulaCloud(
                        canvas,
                        x,
                        y,
                        size * (0.050f * fade + 0.010f),
                        Color.argb(
                            (70 + 80 * fade).toInt(),
                            70,
                            170,
                            255
                        ),
                        Color.argb(
                            (38 + 80 * fade).toInt(),
                            255,
                            205,
                            128
                        )
                    )
                }

                val dotColor = when {
                    i % 9 == 0 -> goldHot
                    i % 3 == 0 -> white
                    else -> cyanBright
                }

                p.style = Paint.Style.FILL
                p.color = withAlpha(
                    dotColor,
                    (42 + 180 * fade).toInt()
                )

                canvas.drawCircle(
                    x,
                    y,
                    maxOf(
                        0.7f,
                        size * (0.0018f + 0.0050f * fade)
                    ),
                    p
                )

                if (i % 3 == 0 && previous != null) {
                    p.style = Paint.Style.STROKE
                    p.strokeWidth =
                        maxOf(0.50f, size * 0.00075f)

                    p.color = withAlpha(
                        if (arm % 2 == 0) cyanBright else gold,
                        (28 + 75 * fade).toInt()
                    )

                    canvas.drawLine(
                        previous.x,
                        previous.y,
                        x,
                        y,
                        p
                    )
                }

                previous = PointF(x, y)
            }
        }

        drawStarBurst(
            canvas,
            0f,
            0f,
            size * 0.09f,
            goldHot,
            0.98f
        )

        canvas.restore()
    }

    private fun drawNebulaCloud(
        canvas: Canvas,
        x: Float,
        y: Float,
        radius: Float,
        colorA: Int,
        colorB: Int
    ) {
        p.style = Paint.Style.FILL

        p.shader = RadialGradient(
            x,
            y,
            radius,
            intArrayOf(
                colorA,
                colorB,
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.44f, 1f),
            Shader.TileMode.CLAMP
        )

        canvas.drawCircle(x, y, radius, p)
        p.shader = null
    }

    private fun drawConstellation(
        canvas: Canvas,
        points: Array<PointF>
    ) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = 0.9f
        p.color = Color.argb(88, 238, 208, 140)

        for (i in 0 until points.size - 1) {
            canvas.drawLine(
                points[i].x,
                points[i].y,
                points[i + 1].x,
                points[i + 1].y,
                p
            )
        }

        for ((index, point) in points.withIndex()) {
            drawGlowDot(
                canvas,
                point.x,
                point.y,
                if (index % 2 == 0) 2.4f else 1.5f,
                if (index % 2 == 0) goldBright else white,
                0.72f
            )
        }
    }

    private fun drawPlanet(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        rightLit: Boolean
    ) {
        p.style = Paint.Style.FILL
        p.color = Color.argb(32, 66, 145, 214)
        canvas.drawCircle(cx, cy, radius * 1.20f, p)

        p.color = Color.rgb(3, 8, 15)
        canvas.drawCircle(cx, cy, radius, p)

        p.style = Paint.Style.STROKE
        p.strokeWidth = 3.2f
        p.color = Color.argb(150, 95, 194, 245)
        canvas.drawCircle(cx, cy, radius, p)

        val shift =
            if (rightLit) -radius * 0.22f
            else radius * 0.22f

        val rect = RectF(
            cx - radius * 0.94f + shift,
            cy - radius * 0.94f,
            cx + radius * 0.94f + shift,
            cy + radius * 0.94f
        )

        p.strokeWidth = 4.5f
        p.color = Color.argb(205, 255, 226, 160)

        canvas.drawArc(
            rect,
            if (rightLit) -72f else 108f,
            144f,
            false,
            p
        )
    }

    // ------------------------------------------------------------------
    // Header
    // ------------------------------------------------------------------

    private fun drawHeader(canvas: Canvas) {
        drawStarBurst(
            canvas,
            540f,
            85f,
            34f,
            goldHot,
            0.98f
        )

        drawGlowDot(
            canvas,
            540f,
            85f,
            3.5f,
            goldHot,
            0.95f
        )

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface =
            Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        textPaint.textSize = 72f
        textPaint.color = goldBright

        drawTextHalo(
            canvas,
            "별을 읽는 성역",
            540f,
            205f,
            72f
        )

        p.style = Paint.Style.STROKE
        p.strokeWidth = 2f
        p.color = Color.argb(175, 238, 207, 136)

        canvas.drawLine(
            180f,
            280f,
            900f,
            280f,
            p
        )

        drawStarBurst(
            canvas,
            540f,
            280f,
            18f,
            goldHot,
            0.92f
        )

        drawGlowDot(
            canvas,
            180f,
            280f,
            2.4f,
            goldBright,
            0.70f
        )

        drawGlowDot(
            canvas,
            900f,
            280f,
            2.4f,
            goldBright,
            0.70f
        )

        textPaint.textSize = 34f
        textPaint.color = ivory

        canvas.drawText(
            "지혜는 내일을 비춘다.",
            540f,
            342f,
            textPaint
        )
    }

    private fun drawTextHalo(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        size: Float
    ) {
        textPaint.textSize = size
        textPaint.typeface =
            Typeface.create(Typeface.SERIF, Typeface.NORMAL)

        textPaint.color =
            Color.argb(22, 255, 226, 160)

        canvas.drawText(text, x - 2f, y, textPaint)
        canvas.drawText(text, x + 2f, y, textPaint)

        textPaint.color = goldBright
        canvas.drawText(text, x, y, textPaint)
    }

    // ------------------------------------------------------------------
    // Main magic circle
    // ------------------------------------------------------------------

    private fun drawMagicCircle(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        r: Float,
        outerRotation: Float,
        innerRotation: Float
    ) {
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(
            cx,
            cy,
            r * 1.12f,
            intArrayOf(
                Color.argb(22, 255, 224, 150),
                Color.argb(18, 70, 190, 255),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.62f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r * 1.10f, p)
        p.shader = null

        // Outer gold frame
        drawGlowRing(
            canvas,
            cx,
            cy,
            r,
            goldHot,
            6f,
            1f
        )

        drawGlowRing(
            canvas,
            cx,
            cy,
            r - 18f,
            goldBright,
            3f,
            0.92f
        )

        drawGlowRing(
            canvas,
            cx,
            cy,
            r - 42f,
            gold,
            2f,
            0.80f
        )

        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.4f
        p.color = withAlpha(goldBright, 130)

        canvas.drawCircle(cx, cy, r - 62f, p)
        canvas.drawCircle(cx, cy, r - 92f, p)

        drawTickRing(
            canvas,
            cx,
            cy,
            r - 8f,
            28f,
            160,
            10
        )

        // Large gold rune ring
        canvas.save()
        canvas.rotate(outerRotation, cx, cy)
        drawRuneRing(
            canvas,
            cx,
            cy,
            472f,
            ReferenceV12Spec.outerRuneCount,
            24f,
            goldBright,
            true
        )
        canvas.restore()

        // Crescents and cardinal flares
        for (angle in floatArrayOf(45f, 135f, 225f, 315f)) {
            drawCrescentMarker(
                canvas,
                cx,
                cy,
                463f,
                angle
            )
        }

        for (angle in floatArrayOf(0f, 90f, 180f, 270f)) {
            drawCardinalFlare(
                canvas,
                cx,
                cy,
                r + 2f,
                angle
            )
        }

        // Bright blue band
        drawGlowRing(
            canvas,
            cx,
            cy,
            430f,
            cyanBright,
            5.2f,
            0.98f
        )

        drawGlowRing(
            canvas,
            cx,
            cy,
            409f,
            cyan,
            2.2f,
            0.82f
        )

        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.2f
        p.color = withAlpha(cyanBright, 165)

        canvas.drawCircle(cx, cy, 392f, p)
        canvas.drawCircle(cx, cy, 373f, p)

        // Inner blue rune ring
        canvas.save()
        canvas.rotate(innerRotation, cx, cy)
        drawRuneRing(
            canvas,
            cx,
            cy,
            397f,
            ReferenceV12Spec.innerRuneCount,
            17f,
            cyanBright,
            false
        )
        canvas.restore()

        // Static 6/7 layers
        val savedGlow = dynamicGlowMultiplier
        dynamicGlowMultiplier = 1f

        drawSacredGeometry(canvas, cx, cy)
        drawStaticOrbits(canvas, cx, cy)

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
            val angle = i * 360f / count
            val major = i % majorEvery == 0

            val start = polar(
                cx,
                cy,
                radius -
                    if (major) tickLength
                    else tickLength * 0.40f,
                angle
            )

            val end =
                polar(cx, cy, radius, angle)

            p.style = Paint.Style.STROKE
            p.strokeWidth =
                if (major) 1.5f else 0.65f

            p.color =
                if (major) {
                    withAlpha(goldBright, 190)
                } else {
                    withAlpha(gold, 70)
                }

            canvas.drawLine(
                start.x,
                start.y,
                end.x,
                end.y,
                p
            )
        }
    }

    private fun drawRuneRing(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        count: Int,
        runeSize: Float,
        color: Int,
        major: Boolean
    ) {
        for (i in 0 until count) {
            val angle = i * 360f / count
            val q = polar(cx, cy, radius, angle)

            canvas.save()
            canvas.rotate(angle, q.x, q.y)

            drawRuneGlyph(
                canvas,
                q.x,
                q.y,
                runeSize,
                i % 10,
                if (i % 8 == 0) goldHot else color,
                if (major) 0.96f else 0.88f
            )

            canvas.restore()
        }
    }

    private fun drawRuneGlyph(
        canvas: Canvas,
        x: Float,
        y: Float,
        size: Float,
        variant: Int,
        color: Int,
        alpha: Float
    ) {
        p.style = Paint.Style.STROKE
        p.strokeWidth =
            if (size > 20f) 2.5f else 1.8f

        p.color =
            withAlpha(
                color,
                (255 * alpha).toInt()
            )

        when (variant) {
            0 -> {
                canvas.drawLine(
                    x - size,
                    y,
                    x + size,
                    y,
                    p
                )
                canvas.drawLine(
                    x,
                    y - size,
                    x,
                    y + size,
                    p
                )
            }

            1 -> {
                path.reset()
                path.moveTo(
                    x - size,
                    y + size * 0.72f
                )
                path.lineTo(
                    x,
                    y - size
                )
                path.lineTo(
                    x + size,
                    y + size * 0.72f
                )
                canvas.drawPath(path, p)
            }

            2 -> {
                path.reset()
                path.moveTo(
                    x - size,
                    y - size
                )
                path.lineTo(
                    x + size * 0.70f,
                    y - size * 0.20f
                )
                path.lineTo(
                    x - size * 0.25f,
                    y + size * 0.10f
                )
                path.lineTo(
                    x + size,
                    y + size
                )
                canvas.drawPath(path, p)
            }

            3 -> drawDiamond(
                canvas,
                x,
                y,
                size * 0.82f,
                color,
                alpha
            )

            4 -> drawPolygon(
                canvas,
                x,
                y,
                size * 0.88f,
                3,
                0f,
                color,
                2f,
                alpha
            )

            5 -> {
                canvas.drawCircle(
                    x,
                    y,
                    size * 0.68f,
                    p
                )
                canvas.drawLine(
                    x - size * 0.7f,
                    y,
                    x + size * 0.7f,
                    y,
                    p
                )
            }

            6 -> {
                canvas.drawLine(
                    x - size,
                    y - size,
                    x + size,
                    y + size,
                    p
                )
                canvas.drawLine(
                    x + size,
                    y - size,
                    x - size,
                    y + size,
                    p
                )
            }

            7 -> {
                path.reset()
                path.moveTo(x - size, y)
                path.lineTo(x, y - size)
                path.lineTo(x + size, y)
                path.lineTo(x, y + size)
                path.close()
                canvas.drawPath(path, p)
            }

            8 -> {
                path.reset()
                path.moveTo(x - size, y + size)
                path.lineTo(x, y - size)
                path.lineTo(x + size, y + size)
                path.moveTo(x - size * 0.55f, y)
                path.lineTo(x + size * 0.55f, y)
                canvas.drawPath(path, p)
            }

            else -> {
                canvas.drawCircle(
                    x,
                    y,
                    size * 0.62f,
                    p
                )
                canvas.drawLine(
                    x,
                    y - size,
                    x,
                    y + size,
                    p
                )
            }
        }
    }

    private fun drawCrescentMarker(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        angle: Float
    ) {
        val q = polar(cx, cy, radius, angle)
        val rr = 27f

        p.style = Paint.Style.FILL
        p.color = goldHot
        canvas.drawCircle(q.x, q.y, rr, p)

        p.color = bg
        canvas.drawCircle(
            q.x + rr * 0.38f,
            q.y,
            rr * 0.86f,
            p
        )

        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.8f
        p.color = withAlpha(goldHot, 190)
        canvas.drawCircle(q.x, q.y, rr, p)
    }

    private fun drawCardinalFlare(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        angle: Float
    ) {
        val q = polar(cx, cy, radius, angle)

        drawStarBurst(
            canvas,
            q.x,
            q.y,
            58f,
            goldHot,
            0.98f
        )

        drawGlowDot(
            canvas,
            q.x,
            q.y,
            7f,
            white,
            0.98f
        )

        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.8f
        p.color = withAlpha(goldBright, 160)
        canvas.drawCircle(
            q.x,
            q.y,
            23f,
            p
        )
    }

    private fun drawSacredGeometry(
        canvas: Canvas,
        cx: Float,
        cy: Float
    ) {
        // Large golden sacred structure.
        drawStar(
            canvas,
            cx,
            cy,
            365f,
            158f,
            6,
            0f,
            goldHot,
            3.1f,
            0.94f
        )

        drawStar(
            canvas,
            cx,
            cy,
            332f,
            208f,
            8,
            22.5f,
            goldBright,
            1.9f,
            0.62f
        )

        drawPolygon(
            canvas,
            cx,
            cy,
            302f,
            8,
            22.5f,
            gold,
            1.5f,
            0.48f
        )

        drawPolygon(
            canvas,
            cx,
            cy,
            268f,
            6,
            30f,
            cyan,
            1.2f,
            0.30f
        )

        // Fine gold mesh.
        val nodes = ArrayList<PointF>()

        for (i in 0 until 12) {
            nodes += polar(
                cx,
                cy,
                320f,
                i * 30f
            )
        }

        for (i in nodes.indices) {
            val a = nodes[i]
            val b = nodes[(i + 5) % nodes.size]
            val d = nodes[(i + 7) % nodes.size]

            p.style = Paint.Style.STROKE
            p.strokeWidth = 1.0f
            p.color = withAlpha(goldBright, 72)

            canvas.drawLine(
                a.x,
                a.y,
                b.x,
                b.y,
                p
            )

            p.color = withAlpha(cyan, 30)
            canvas.drawLine(
                a.x,
                a.y,
                d.x,
                d.y,
                p
            )
        }

        for (i in 0 until 24) {
            val angle = i * 15f

            val start =
                polar(cx, cy, 242f, angle)

            val end =
                polar(
                    cx,
                    cy,
                    if (i % 3 == 0) 350f else 315f,
                    angle
                )

            p.style = Paint.Style.STROKE
            p.strokeWidth =
                if (i % 3 == 0) 1.2f else 0.75f

            p.color =
                if (i % 2 == 0) {
                    withAlpha(goldBright, 52)
                } else {
                    withAlpha(cyanBright, 25)
                }

            canvas.drawLine(
                start.x,
                start.y,
                end.x,
                end.y,
                p
            )
        }

        // Bright anchor nodes.
        for (i in 0 until 12) {
            val q = polar(
                cx,
                cy,
                323f,
                i * 30f
            )

            if (i % 2 == 0) {
                drawGlowDot(
                    canvas,
                    q.x,
                    q.y,
                    7f,
                    goldHot,
                    0.88f
                )

                drawStarBurst(
                    canvas,
                    q.x,
                    q.y,
                    16f,
                    goldHot,
                    0.52f
                )
            } else {
                drawGlowDot(
                    canvas,
                    q.x,
                    q.y,
                    5f,
                    cyanBright,
                    0.58f
                )
            }
        }

        // Faint guide ellipses.
        val guide = RectF(
            cx - 350f,
            cy - 126f,
            cx + 350f,
            cy + 126f
        )

        for (rotation in floatArrayOf(
            0f,
            60f,
            120f
        )) {
            canvas.save()
            canvas.rotate(rotation, cx, cy)

            p.style = Paint.Style.STROKE
            p.strokeWidth = 0.8f
            p.color = withAlpha(gold, 30)

            canvas.drawOval(guide, p)
            canvas.restore()
        }
    }

    private fun drawStaticOrbits(
        canvas: Canvas,
        cx: Float,
        cy: Float
    ) {
        val rotations =
            floatArrayOf(0f, 45f, 90f, 135f)

        val rx =
            floatArrayOf(345f, 335f, 325f, 315f)

        val ry =
            floatArrayOf(125f, 142f, 116f, 136f)

        for (i in 0 until ReferenceV12Spec.orbitCount) {
            val rect = RectF(
                cx - rx[i],
                cy - ry[i],
                cx + rx[i],
                cy + ry[i]
            )

            canvas.save()
            canvas.rotate(
                rotations[i],
                cx,
                cy
            )

            p.style = Paint.Style.STROKE

            p.strokeWidth = 12f
            p.color = withAlpha(
                cyanBright,
                20
            )
            canvas.drawOval(rect, p)

            p.strokeWidth = 6f
            p.color = withAlpha(
                cyanBright,
                66
            )
            canvas.drawOval(rect, p)

            p.strokeWidth = 2.7f
            p.color =
                withAlpha(
                    if (i % 2 == 0)
                        cyanBright
                    else white,
                    238
                )

            canvas.drawOval(rect, p)

            canvas.restore()
        }

        // 8 prominent nodes.
        for (i in 0 until ReferenceV12Spec.primaryOrbitNodeCount) {
            val angle = i * 45f

            val q = polar(
                cx,
                cy,
                330f,
                angle
            )

            val rr =
                if (i % 2 == 0) 10f
                else 8f

            p.style = Paint.Style.FILL
            p.color = withAlpha(
                cyanBright,
                22
            )
            canvas.drawCircle(
                q.x,
                q.y,
                rr * 4.2f,
                p
            )

            p.color = withAlpha(
                cyanBright,
                58
            )
            canvas.drawCircle(
                q.x,
                q.y,
                rr * 2.5f,
                p
            )

            p.style = Paint.Style.STROKE
            p.strokeWidth = 1.6f
            p.color = withAlpha(
                if (i % 2 == 0)
                    cyanBright
                else goldBright,
                195
            )

            canvas.drawCircle(
                q.x,
                q.y,
                rr * 1.55f,
                p
            )

            p.style = Paint.Style.FILL
            p.color = white
            canvas.drawCircle(
                q.x,
                q.y,
                rr,
                p
            )

            p.color = goldHot
            canvas.drawCircle(
                q.x,
                q.y,
                rr * 0.28f,
                p
            )
        }
    }

    // ------------------------------------------------------------------
    // Center core
    // ------------------------------------------------------------------

    private fun drawCenterCore(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        coreR: Float,
        displayPercent: Float
    ) {
        p.style = Paint.Style.FILL

        p.shader = RadialGradient(
            cx - coreR * 0.22f,
            cy - coreR * 0.22f,
            coreR * 1.28f,
            intArrayOf(
                Color.rgb(13, 31, 49),
                Color.rgb(5, 14, 25),
                darkCore
            ),
            floatArrayOf(
                0f,
                0.54f,
                1f
            ),
            Shader.TileMode.CLAMP
        )

        canvas.drawCircle(
            cx,
            cy,
            coreR,
            p
        )

        p.shader = null

        // Tiny stars inside planet.
        for (i in 0 until 52) {
            val angle = i * 137.5f

            val radius =
                coreR *
                    (
                        0.15f +
                            pseudo(i * 21 + 7) *
                            0.76f
                        )

            val q = polar(
                cx,
                cy,
                radius,
                angle
            )

            p.style = Paint.Style.FILL

            p.color =
                withAlpha(
                    if (i % 6 == 0)
                        cyanBright
                    else white,
                    if (i % 6 == 0)
                        86
                    else 42
                )

            canvas.drawCircle(
                q.x,
                q.y,
                if (i % 9 == 0) 1.4f
                else 0.75f,
                p
            )
        }

        drawGlowRing(
            canvas,
            cx,
            cy,
            coreR * 1.035f,
            cyanBright,
            5.0f,
            0.96f
        )

        p.style = Paint.Style.STROKE
        p.strokeWidth = 2.1f
        p.color =
            withAlpha(
                goldBright,
                180
            )

        canvas.drawCircle(
            cx,
            cy,
            coreR * 1.012f,
            p
        )

        val shown =
            displayPercent
                .roundToInt()
                .coerceIn(0, 100)

        textPaint.textAlign =
            Paint.Align.CENTER

        textPaint.typeface =
            Typeface.create(
                Typeface.SERIF,
                Typeface.NORMAL
            )

        textPaint.color = goldHot
        textPaint.textSize = 148f

        canvas.drawText(
            shown.toString(),
            cx - 18f,
            cy + 30f,
            textPaint
        )

        textPaint.textSize = 47f

        canvas.drawText(
            "%",
            cx + 145f,
            cy + 32f,
            textPaint
        )

        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.3f
        p.color =
            withAlpha(
                goldBright,
                145
            )

        canvas.drawLine(
            cx - 132f,
            cy + 78f,
            cx + 132f,
            cy + 78f,
            p
        )

        drawStarBurst(
            canvas,
            cx,
            cy + 78f,
            12f,
            goldHot,
            0.82f
        )

        textPaint.typeface =
            Typeface.create(
                Typeface.SANS_SERIF,
                Typeface.NORMAL
            )

        textPaint.textSize = 35f
        textPaint.color = ivory

        canvas.drawText(
            connectionText,
            cx,
            cy + 145f,
            textPaint
        )
    }

    // ------------------------------------------------------------------
    // Footer
    // ------------------------------------------------------------------

    private fun drawFooter(canvas: Canvas) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2f
        p.color = Color.argb(
            165,
            235,
            204,
            132
        )

        canvas.drawLine(
            180f,
            1815f,
            900f,
            1815f,
            p
        )

        drawStarBurst(
            canvas,
            540f,
            1815f,
            18f,
            goldHot,
            0.92f
        )

        drawGlowDot(
            canvas,
            180f,
            1815f,
            2.4f,
            goldBright,
            0.70f
        )

        drawGlowDot(
            canvas,
            900f,
            1815f,
            2.4f,
            goldBright,
            0.70f
        )

        textPaint.textAlign =
            Paint.Align.CENTER

        textPaint.typeface =
            Typeface.create(
                Typeface.SERIF,
                Typeface.NORMAL
            )

        textPaint.textSize = 38f
        textPaint.color =
            Color.rgb(
                239,
                220,
                179
            )

        canvas.drawText(
            "지혜는 더 밝은 내일을 비춘다.",
            540f,
            1890f,
            textPaint
        )

        drawInfo(
            canvas,
            180f,
            2070f,
            "%.1f°C".format(
                batteryTempC
            ),
            "배터리 온도"
        )

        drawInfo(
            canvas,
            540f,
            2070f,
            batteryHealthText,
            "배터리 상태"
        )

        drawInfo(
            canvas,
            900f,
            2070f,
            connectionText,
            "연결 방식"
        )

        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.2f
        p.color =
            Color.argb(
                120,
                235,
                205,
                145
            )

        canvas.drawLine(
            360f,
            1980f,
            360f,
            2175f,
            p
        )

        canvas.drawLine(
            720f,
            1980f,
            720f,
            2175f,
            p
        )

        textPaint.textSize = 31f
        textPaint.color =
            Color.rgb(
                241,
                220,
                168
            )

        canvas.drawText(
            "⋯⋯  ◔  ◑  ●  ◐  ◕  ⋯⋯",
            540f,
            2275f,
            textPaint
        )
    }

    private fun drawInfo(
        canvas: Canvas,
        x: Float,
        valueY: Float,
        value: String,
        label: String
    ) {
        textPaint.typeface =
            Typeface.create(
                Typeface.SERIF,
                Typeface.NORMAL
            )

        textPaint.color = ivory

        textPaint.textSize =
            if (value.length > 6)
                42f
            else
                52f

        canvas.drawText(
            value,
            x,
            valueY,
            textPaint
        )

        textPaint.typeface =
            Typeface.create(
                Typeface.SANS_SERIF,
                Typeface.NORMAL
            )

        textPaint.color =
            Color.rgb(
                196,
                179,
                146
            )

        textPaint.textSize = 27f

        canvas.drawText(
            label,
            x,
            valueY + 62f,
            textPaint
        )
    }

    // ------------------------------------------------------------------
    // Drawing helpers
    // ------------------------------------------------------------------

    private fun drawGlowRing(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        color: Int,
        stroke: Float,
        alpha: Float
    ) {
        val a =
            (alpha * dynamicGlowMultiplier)
                .coerceIn(0f, 1f)

        p.style = Paint.Style.STROKE

        p.strokeWidth =
            maxOf(1f, stroke * 7f)

        p.color =
            withAlpha(
                color,
                (18 * a).toInt()
            )

        canvas.drawCircle(
            cx,
            cy,
            radius,
            p
        )

        p.strokeWidth =
            maxOf(1f, stroke * 4.2f)

        p.color =
            withAlpha(
                color,
                (36 * a).toInt()
            )

        canvas.drawCircle(
            cx,
            cy,
            radius,
            p
        )

        p.strokeWidth =
            maxOf(1f, stroke * 2.1f)

        p.color =
            withAlpha(
                color,
                (78 * a).toInt()
            )

        canvas.drawCircle(
            cx,
            cy,
            radius,
            p
        )

        p.strokeWidth =
            maxOf(0.8f, stroke)

        p.color =
            withAlpha(
                color,
                (255 * a).toInt()
            )

        canvas.drawCircle(
            cx,
            cy,
            radius,
            p
        )
    }

    private fun drawGlowDot(
        canvas: Canvas,
        x: Float,
        y: Float,
        radius: Float,
        color: Int,
        alpha: Float
    ) {
        val a =
            (alpha * dynamicGlowMultiplier)
                .coerceIn(0f, 1f)

        p.style = Paint.Style.FILL

        p.color =
            withAlpha(
                color,
                (20 * a).toInt()
            )
        canvas.drawCircle(
            x,
            y,
            radius * 4.2f,
            p
        )

        p.color =
            withAlpha(
                color,
                (50 * a).toInt()
            )
        canvas.drawCircle(
            x,
            y,
            radius * 2.5f,
            p
        )

        p.color =
            withAlpha(
                color,
                (110 * a).toInt()
            )
        canvas.drawCircle(
            x,
            y,
            radius * 1.55f,
            p
        )

        p.color =
            withAlpha(
                color,
                (255 * a).toInt()
            )
        canvas.drawCircle(
            x,
            y,
            radius,
            p
        )

        p.color =
            withAlpha(
                white,
                (240 * a).toInt()
            )
        canvas.drawCircle(
            x,
            y,
            maxOf(
                0.5f,
                radius * 0.28f
            ),
            p
        )
    }

    private fun drawPolygon(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        sides: Int,
        rotation: Float,
        color: Int,
        width: Float,
        alpha: Float
    ) {
        path.reset()

        for (i in 0 until sides) {
            val q = polar(
                cx,
                cy,
                radius,
                rotation +
                    i * 360f / sides
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

        p.color =
            withAlpha(
                color,
                (255 * alpha).toInt()
            )

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
            val rr =
                if (i % 2 == 0)
                    outer
                else
                    inner

            val q = polar(
                cx,
                cy,
                rr,
                rotation +
                    i * 180f / tips
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

        p.color =
            withAlpha(
                color,
                (255 * alpha).toInt()
            )

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
        p.strokeWidth =
            maxOf(1f, size * 0.10f)

        p.color =
            withAlpha(
                color,
                (255 * alpha).toInt()
            )

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
        p.strokeWidth =
            maxOf(1f, size * 0.075f)

        p.color =
            withAlpha(
                color,
                (255 * alpha).toInt()
            )

        canvas.drawLine(
            x - size,
            y,
            x + size,
            y,
            p
        )

        canvas.drawLine(
            x,
            y - size,
            x,
            y + size,
            p
        )

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
        radius: Float,
        degrees: Float
    ): PointF {
        val rad =
            Math.toRadians(
                (degrees - 90f).toDouble()
            )

        return PointF(
            cx +
                (cos(rad) * radius).toFloat(),
            cy +
                (sin(rad) * radius).toFloat()
        )
    }

    private fun cosDeg(degrees: Float): Float =
        cos(
            Math.toRadians(
                degrees.toDouble()
            )
        ).toFloat()

    private fun sinDeg(degrees: Float): Float =
        sin(
            Math.toRadians(
                degrees.toDouble()
            )
        ).toFloat()

    private fun pseudo(seed: Int): Float {
        val v =
            abs(
                sin(seed * 12.9898) *
                    43758.5453
            )

        return (
            v -
                v.toInt()
            ).toFloat()
    }

    private fun withAlpha(
        color: Int,
        alpha: Int
    ): Int {
        return Color.argb(
            alpha.coerceIn(0, 255),
            Color.red(color),
            Color.green(color),
            Color.blue(color)
        )
    }
}
