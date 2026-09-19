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
 * 별을 읽는 성역 — V13 Ultra Reference Match
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
                w / ReferenceV13Spec.virtualWidth,
                h / ReferenceV13Spec.virtualHeight
            )

            val offsetX =
                (w - ReferenceV13Spec.virtualWidth * scale) * 0.5f
            val offsetY =
                (h - ReferenceV13Spec.virtualHeight * scale) * 0.5f

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
                w / ReferenceV13Spec.virtualWidth,
                h / ReferenceV13Spec.virtualHeight
            )

            val offsetX =
                (w - ReferenceV13Spec.virtualWidth * scale) * 0.5f
            val offsetY =
                (h - ReferenceV13Spec.virtualHeight * scale) * 0.5f

            canvas.save()
            canvas.translate(offsetX, offsetY)
            canvas.scale(scale, scale)
            drawSpaceBackground(canvas)
            canvas.restore()
        }

        val scale = min(
            w / ReferenceV13Spec.virtualWidth,
            h / ReferenceV13Spec.virtualHeight
        )

        val offsetX =
            (w - ReferenceV13Spec.virtualWidth * scale) * 0.5f
        val offsetY =
            (h - ReferenceV13Spec.virtualHeight * scale) * 0.5f

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
            ReferenceV13Spec.magicCenterX,
            ReferenceV13Spec.magicCenterY,
            ReferenceV13Spec.magicRadius,
            outerRotation,
            innerRotation
        )

        drawCenterCore(
            canvas,
            ReferenceV13Spec.magicCenterX,
            ReferenceV13Spec.magicCenterY,
            ReferenceV13Spec.coreRadius,
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
        val vw = ReferenceV13Spec.virtualWidth
        val vh = ReferenceV13Spec.virtualHeight

        p.style = Paint.Style.FILL
        p.shader = RadialGradient(
            vw * 0.5f,
            vh * 0.46f,
            1120f,
            intArrayOf(
                Color.argb(54, 10, 42, 78),
                Color.argb(29, 5, 20, 40),
                bg
            ),
            floatArrayOf(0f, 0.52f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, vw, vh, p)
        p.shader = null

        // Four bright galaxy clusters arranged like the reference image.
        drawGalaxy(canvas, 80f, 190f, 370f, 15f, false)
        drawGalaxy(canvas, 1000f, 300f, 345f, 207f, true)
        drawGalaxy(canvas, 105f, 1985f, 305f, 122f, false)
        drawGalaxy(canvas, 982f, 1915f, 320f, 302f, true)

        // Fine star field: rich at the edges, calmer behind the magic circle.
        for (i in 0 until 430) {
            val x = pseudo(i * 17 + 7) * vw
            val y = pseudo(i * 29 + 19) * vh
            val centerProtected = x in 105f..975f && y in 430f..1655f
            if (centerProtected && i % 5 != 0) continue

            val rr = when {
                i % 47 == 0 -> 3.5f
                i % 19 == 0 -> 2.0f
                i % 7 == 0 -> 1.15f
                else -> 0.62f
            }
            val color = when {
                i % 13 == 0 -> goldHot
                i % 5 == 0 -> cyanBright
                else -> white
            }
            p.style = Paint.Style.FILL
            p.color = withAlpha(
                color,
                when {
                    i % 47 == 0 -> 238
                    i % 19 == 0 -> 185
                    else -> 100
                }
            )
            canvas.drawCircle(x, y, rr, p)

            if (i % 47 == 0) {
                drawStarBurst(canvas, x, y, 18f, color, 0.78f)
            }
        }

        drawConstellation(
            canvas,
            arrayOf(
                PointF(748f, 70f), PointF(812f, 105f), PointF(858f, 162f),
                PointF(918f, 126f), PointF(1003f, 184f)
            )
        )
        drawConstellation(
            canvas,
            arrayOf(
                PointF(35f, 500f), PointF(96f, 446f), PointF(152f, 527f),
                PointF(215f, 475f)
            )
        )
        drawConstellation(
            canvas,
            arrayOf(
                PointF(806f, 1685f), PointF(866f, 1615f), PointF(930f, 1672f),
                PointF(1010f, 1608f)
            )
        )
        drawConstellation(
            canvas,
            arrayOf(
                PointF(72f, 1525f), PointF(122f, 1470f), PointF(178f, 1515f),
                PointF(220f, 1455f)
            )
        )

        drawPlanet(canvas, 58f, 1695f, 52f, true)
        drawPlanet(canvas, 1015f, 1775f, 58f, false)
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

        // Multiple broad nebula cores create a continuous galaxy instead of dotted spirals.
        drawNebulaCloud(
            canvas, 0f, 0f, size * 0.42f,
            Color.argb(150, 255, 232, 190),
            Color.argb(95, 54, 157, 244)
        )
        drawNebulaCloud(
            canvas, size * 0.05f, -size * 0.03f, size * 0.30f,
            Color.argb(85, 110, 210, 255),
            Color.argb(52, 240, 190, 100)
        )

        for (arm in 0 until 5) {
            var previous: PointF? = null
            for (i in 0 until 150) {
                val t = i / 149f
                val angle = arm * 72f + t * 575f
                val radius = size * (0.022f + t * 0.98f)
                val wave = sin((i + arm * 19) * 0.49).toFloat() * size * 0.030f
                val x = cosDeg(angle) * (radius + wave)
                val y = sinDeg(angle) * (radius + wave) * 0.61f
                val fade = 1f - t

                if (i % 6 == 0) {
                    drawNebulaCloud(
                        canvas,
                        x,
                        y,
                        size * (0.060f * fade + 0.012f),
                        Color.argb((60 + 95 * fade).toInt(), 70, 180, 255),
                        Color.argb((28 + 90 * fade).toInt(), 255, 205, 126)
                    )
                }

                val dotColor = when {
                    i % 11 == 0 -> goldHot
                    i % 4 == 0 -> white
                    else -> cyanBright
                }
                p.style = Paint.Style.FILL
                p.color = withAlpha(dotColor, (48 + 188 * fade).toInt())
                canvas.drawCircle(
                    x, y,
                    maxOf(0.75f, size * (0.0018f + 0.0055f * fade)),
                    p
                )

                if (i % 2 == 0 && previous != null) {
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = maxOf(0.5f, size * 0.00085f)
                    p.color = withAlpha(
                        if (arm % 2 == 0) cyanBright else gold,
                        (32 + 92 * fade).toInt()
                    )
                    canvas.drawLine(previous.x, previous.y, x, y, p)
                }
                previous = PointF(x, y)
            }
        }

        drawStarBurst(canvas, 0f, 0f, size * 0.11f, goldHot, 1f)
        drawGlowDot(canvas, 0f, 0f, size * 0.020f, white, 0.96f)
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
        drawStarBurst(canvas, 540f, 78f, 38f, goldHot, 1f)
        drawGlowDot(canvas, 540f, 78f, 4f, goldHot, 0.96f)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        drawTextHalo(canvas, "별을 읽는 성역", 540f, 202f, 76f)

        p.style = Paint.Style.STROKE
        p.strokeWidth = 2.1f
        p.color = Color.argb(190, 241, 210, 138)
        canvas.drawLine(168f, 278f, 912f, 278f, p)

        drawGlowDot(canvas, 168f, 278f, 3f, goldBright, 0.78f)
        drawGlowDot(canvas, 912f, 278f, 3f, goldBright, 0.78f)
        drawStarBurst(canvas, 540f, 278f, 21f, goldHot, 0.96f)

        textPaint.textSize = 35f
        textPaint.color = ivory
        canvas.drawText("지혜는 내일을 비춘다.", 540f, 342f, textPaint)
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
            cx, cy, r * 1.13f,
            intArrayOf(
                Color.argb(24, 255, 228, 154),
                Color.argb(18, 72, 195, 255),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.62f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r * 1.11f, p)
        p.shader = null

        // Reference-style luminous gold frame: broad outer glow + crisp inner filaments.
        drawGlowRing(canvas, cx, cy, r, goldHot, 6.8f, 1f)
        drawGlowRing(canvas, cx, cy, r - 17f, goldBright, 3.8f, 0.96f)
        drawGlowRing(canvas, cx, cy, r - 42f, gold, 2.0f, 0.82f)

        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.35f
        p.color = withAlpha(goldBright, 145)
        canvas.drawCircle(cx, cy, r - 61f, p)
        canvas.drawCircle(cx, cy, r - 89f, p)

        // Fine dotted/astrolabe tracks.
        for (i in 0 until 240) {
            val a = i * 1.5f
            val q = polar(cx, cy, r - 7f, a)
            p.style = Paint.Style.FILL
            p.color = withAlpha(
                if (i % 12 == 0) goldHot else goldBright,
                if (i % 12 == 0) 205 else 92
            )
            canvas.drawCircle(q.x, q.y, if (i % 12 == 0) 2.2f else 0.85f, p)
        }

        drawTickRing(canvas, cx, cy, r - 9f, 30f, 176, 11)

        canvas.save()
        canvas.rotate(outerRotation, cx, cy)
        drawRuneRing(
            canvas, cx, cy, r - 58f,
            ReferenceV13Spec.outerRuneCount,
            26f, goldBright, true
        )
        canvas.restore()

        // Four reference crescents and cardinal flares.
        for (angle in floatArrayOf(45f, 135f, 225f, 315f)) {
            drawCrescentMarker(canvas, cx, cy, r - 69f, angle)
        }
        for (angle in floatArrayOf(0f, 90f, 180f, 270f)) {
            drawCardinalFlare(canvas, cx, cy, r + 1f, angle)
        }

        // Bright cyan inner band with two additional hairline rings.
        drawGlowRing(canvas, cx, cy, r - 102f, cyanBright, 5.8f, 1f)
        drawGlowRing(canvas, cx, cy, r - 128f, cyan, 2.6f, 0.86f)

        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.3f
        p.color = withAlpha(cyanBright, 180)
        canvas.drawCircle(cx, cy, r - 149f, p)
        canvas.drawCircle(cx, cy, r - 173f, p)

        for (i in 0 until 196) {
            val q = polar(cx, cy, r - 141f, i * (360f / 196f))
            p.style = Paint.Style.FILL
            p.color = withAlpha(
                if (i % 14 == 0) white else cyanBright,
                if (i % 14 == 0) 210 else 88
            )
            canvas.drawCircle(q.x, q.y, if (i % 14 == 0) 1.8f else 0.75f, p)
        }

        canvas.save()
        canvas.rotate(innerRotation, cx, cy)
        drawRuneRing(
            canvas, cx, cy, r - 137f,
            ReferenceV13Spec.innerRuneCount,
            18f, cyanBright, false
        )
        canvas.restore()

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
        // Main 6-point golden star crossing behind the core.
        drawStar(canvas, cx, cy, 382f, 155f, 6, 0f, goldHot, 3.4f, 0.96f)
        drawStar(canvas, cx, cy, 350f, 218f, 8, 22.5f, goldBright, 2.0f, 0.68f)
        drawPolygon(canvas, cx, cy, 316f, 8, 22.5f, gold, 1.5f, 0.52f)
        drawPolygon(canvas, cx, cy, 282f, 6, 30f, cyanBright, 1.2f, 0.30f)

        // Dense golden constellation mesh.
        val nodes = ArrayList<PointF>()
        for (i in 0 until 16) {
            nodes += polar(cx, cy, 335f, i * 22.5f)
        }

        for (i in nodes.indices) {
            val a = nodes[i]
            val b = nodes[(i + 5) % nodes.size]
            val d = nodes[(i + 7) % nodes.size]

            p.style = Paint.Style.STROKE
            p.strokeWidth = 1.05f
            p.color = withAlpha(goldBright, 82)
            canvas.drawLine(a.x, a.y, b.x, b.y, p)

            p.color = withAlpha(cyanBright, 28)
            canvas.drawLine(a.x, a.y, d.x, d.y, p)
        }

        // Radial filaments.
        for (i in 0 until 32) {
            val angle = i * 11.25f
            val start = polar(cx, cy, 230f, angle)
            val end = polar(cx, cy, if (i % 4 == 0) 365f else 325f, angle)

            p.style = Paint.Style.STROKE
            p.strokeWidth = if (i % 4 == 0) 1.25f else 0.72f
            p.color = withAlpha(
                if (i % 2 == 0) goldBright else cyanBright,
                if (i % 4 == 0) 62 else 26
            )
            canvas.drawLine(start.x, start.y, end.x, end.y, p)
        }

        // Three faint guide ellipses.
        val guide = RectF(cx - 370f, cy - 132f, cx + 370f, cy + 132f)
        for (rot in floatArrayOf(0f, 60f, 120f)) {
            canvas.save()
            canvas.rotate(rot, cx, cy)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 0.85f
            p.color = withAlpha(gold, 32)
            canvas.drawOval(guide, p)
            canvas.restore()
        }

        // High-contrast anchor nodes.
        for (i in 0 until 16) {
            val q = polar(cx, cy, 338f, i * 22.5f)
            if (i % 2 == 0) {
                drawGlowDot(canvas, q.x, q.y, 7.5f, goldHot, 0.92f)
                drawStarBurst(canvas, q.x, q.y, 17f, goldHot, 0.58f)
            } else {
                drawGlowDot(canvas, q.x, q.y, 5.5f, cyanBright, 0.64f)
            }
        }
    }

    private fun drawStaticOrbits(
        canvas: Canvas,
        cx: Float,
        cy: Float
    ) {
        val rotations = floatArrayOf(0f, 45f, 90f, 135f)
        val rx = floatArrayOf(358f, 346f, 334f, 322f)
        val ry = floatArrayOf(130f, 150f, 120f, 142f)

        for (i in 0 until ReferenceV13Spec.orbitCount) {
            val rect = RectF(cx - rx[i], cy - ry[i], cx + rx[i], cy + ry[i])
            canvas.save()
            canvas.rotate(rotations[i], cx, cy)

            p.style = Paint.Style.STROKE
            p.strokeWidth = 14f
            p.color = withAlpha(cyanBright, 22)
            canvas.drawOval(rect, p)

            p.strokeWidth = 7f
            p.color = withAlpha(cyanBright, 72)
            canvas.drawOval(rect, p)

            p.strokeWidth = 3.0f
            p.color = withAlpha(if (i % 2 == 0) cyanBright else white, 245)
            canvas.drawOval(rect, p)

            p.strokeWidth = 1f
            p.color = withAlpha(goldBright, 45)
            canvas.drawOval(
                RectF(rect.left + 8f, rect.top + 8f, rect.right - 8f, rect.bottom - 8f),
                p
            )
            canvas.restore()
        }

        for (i in 0 until ReferenceV13Spec.primaryOrbitNodeCount) {
            val angle = i * 45f
            val q = polar(cx, cy, 344f, angle)
            val rr = if (i % 2 == 0) 11.5f else 9.0f

            p.style = Paint.Style.FILL
            p.color = withAlpha(cyanBright, 24)
            canvas.drawCircle(q.x, q.y, rr * 4.3f, p)

            p.color = withAlpha(cyanBright, 64)
            canvas.drawCircle(q.x, q.y, rr * 2.6f, p)

            p.style = Paint.Style.STROKE
            p.strokeWidth = 1.8f
            p.color = withAlpha(if (i % 2 == 0) cyanBright else goldBright, 210)
            canvas.drawCircle(q.x, q.y, rr * 1.55f, p)

            p.style = Paint.Style.FILL
            p.color = white
            canvas.drawCircle(q.x, q.y, rr, p)

            p.color = goldHot
            canvas.drawCircle(q.x, q.y, rr * 0.28f, p)
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
        // Large black planet core with deep-space texture.
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(
            cx - coreR * 0.24f,
            cy - coreR * 0.22f,
            coreR * 1.30f,
            intArrayOf(
                Color.rgb(15, 34, 52),
                Color.rgb(6, 15, 27),
                darkCore
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, coreR, p)
        p.shader = null

        for (i in 0 until 72) {
            val angle = i * 137.5f
            val radius = coreR * (0.12f + pseudo(i * 23 + 5) * 0.80f)
            val q = polar(cx, cy, radius, angle)

            p.style = Paint.Style.FILL
            p.color = withAlpha(
                if (i % 7 == 0) cyanBright else if (i % 11 == 0) goldBright else white,
                if (i % 7 == 0) 92 else 44
            )
            canvas.drawCircle(q.x, q.y, if (i % 10 == 0) 1.6f else 0.75f, p)
        }

        drawGlowRing(canvas, cx, cy, coreR * 1.038f, cyanBright, 5.5f, 0.98f)

        p.style = Paint.Style.STROKE
        p.strokeWidth = 2.3f
        p.color = withAlpha(goldBright, 185)
        canvas.drawCircle(cx, cy, coreR * 1.012f, p)

        val shown = displayPercent.roundToInt().coerceIn(0, 100)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        textPaint.color = goldHot
        textPaint.textSize = 154f
        canvas.drawText(shown.toString(), cx - 20f, cy + 32f, textPaint)

        textPaint.textSize = 50f
        canvas.drawText("%", cx + 151f, cy + 34f, textPaint)

        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.4f
        p.color = withAlpha(goldBright, 150)
        canvas.drawLine(cx - 138f, cy + 83f, cx + 138f, cy + 83f, p)

        drawStarBurst(canvas, cx, cy + 83f, 13f, goldHot, 0.86f)

        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        textPaint.textSize = 36f
        textPaint.color = ivory
        canvas.drawText(connectionText, cx, cy + 153f, textPaint)
    }

    // ------------------------------------------------------------------
    // Footer
    // ------------------------------------------------------------------

    private fun drawFooter(canvas: Canvas) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2.1f
        p.color = Color.argb(175, 238, 205, 132)
        canvas.drawLine(170f, 1818f, 910f, 1818f, p)

        drawStarBurst(canvas, 540f, 1818f, 20f, goldHot, 0.95f)
        drawGlowDot(canvas, 170f, 1818f, 2.8f, goldBright, 0.72f)
        drawGlowDot(canvas, 910f, 1818f, 2.8f, goldBright, 0.72f)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        textPaint.textSize = 40f
        textPaint.color = Color.rgb(241, 222, 180)
        canvas.drawText("지혜는 더 밝은 내일을 비춘다.", 540f, 1895f, textPaint)

        drawInfo(canvas, 180f, 2075f, "%.1f°C".format(batteryTempC), "배터리 온도")
        drawInfo(canvas, 540f, 2075f, batteryHealthText, "배터리 상태")
        drawInfo(canvas, 900f, 2075f, connectionText, "연결 방식")

        p.strokeWidth = 1.3f
        p.color = Color.argb(130, 235, 205, 145)
        canvas.drawLine(360f, 1980f, 360f, 2180f, p)
        canvas.drawLine(720f, 1980f, 720f, 2180f, p)

        textPaint.textSize = 33f
        textPaint.color = Color.rgb(243, 222, 169)
        canvas.drawText("⋯⋯  ◔  ◑  ●  ◐  ◕  ⋯⋯", 540f, 2280f, textPaint)
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
