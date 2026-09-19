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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * "별을 읽는 성역" 충전 화면.
 * 이미지 파일을 전혀 사용하지 않고 Android Canvas/Path/Gradient만으로 렌더링한다.
 */
class StellarSanctuaryView(context: Context) : View(context) {

    private val bg = Color.rgb(2, 4, 8)
    private val white = Color.rgb(255, 253, 248)
    private val gold = Color.rgb(246, 227, 177)
    private val goldBright = Color.rgb(255, 239, 184)
    private val cyan = Color.rgb(169, 229, 255)
    private val cyanBright = Color.rgb(104, 215, 255)
    private val cyanDim = Color.rgb(48, 94, 118)
    private val darkCore = Color.rgb(3, 10, 16)

    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans", Typeface.NORMAL)
    }
    private val path = Path()

    private var batteryPercent = 69
    private var batteryTempC = 32.5f
    private var batteryHealthText = "양호"
    private var connectionText = "연결되지 않음"

    private var displayedBatteryPercent = batteryPercent.toFloat()
    private var hasBatteryReading = false

    private var animationStartNanos = SystemClock.elapsedRealtimeNanos()
    private var lastFrameNanos = animationStartNanos
    private var animationActive = false
    private var dynamicGlowMultiplier = 1f

    private var staticSpaceLayer: Bitmap? = null

    init {
        setBackgroundColor(bg)
        isClickable = true
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
        animationActive = visibility == View.VISIBLE
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
            val cacheCanvas = Canvas(bitmap)
            cacheCanvas.drawColor(bg)

            val savedGlow = dynamicGlowMultiplier
            dynamicGlowMultiplier = 1f
            drawBackdrop(cacheCanvas, w.toFloat(), h.toFloat())
            dynamicGlowMultiplier = savedGlow

            staticSpaceLayer = bitmap
        } catch (_: OutOfMemoryError) {
            // 고해상도 기기에서 캐시 생성이 부담되면 실시간 배경 렌더링으로 안전하게 폴백한다.
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
        batteryTempC = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 325) / 10f
        batteryHealthText = when (
            intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
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
            drawBackdrop(canvas, w, h)
        }

        drawHeader(canvas, w, h)

        val cx = w * 0.5f
        val cy = h * 0.455f
        val r = min(w * 0.46f, h * 0.31f)

        val outerRuneRotation =
            if (AnimationLayerPolicy.animateOuterRuneBand) {
                AnimationMath.wrapDegrees(elapsedSeconds * 2.4f)
            } else {
                0f
            }

        val innerRuneRotation =
            if (AnimationLayerPolicy.animateInnerRuneBand) {
                -AnimationMath.wrapDegrees(elapsedSeconds * 3.6f)
            } else {
                0f
            }

        val shimmer = AnimationMath.pulse01(elapsedSeconds, 0.82f, 0.31f)

        dynamicGlowMultiplier = 0.84f + shimmer * 0.16f

        drawCircleSystem(
            canvas = canvas,
            cx = cx,
            cy = cy,
            r = r,
            outerRuneRotation = outerRuneRotation,
            innerRuneRotation = innerRuneRotation
        )
        drawCenterPanel(
            canvas,
            cx,
            cy,
            r,
            displayedBatteryPercent
        )

        dynamicGlowMultiplier = 1f
        drawFooter(canvas, w, h)

        if (animationActive && windowVisibility == View.VISIBLE) {
            // 60fps 부근으로 제한해 120Hz 기기에서도 발열/배터리 사용량을 억제한다.
            postInvalidateDelayed(16L)
        }
    }

    private fun drawBackdrop(canvas: Canvas, w: Float, h: Float) {
        val nebulae = arrayOf(
            floatArrayOf(0.12f, 0.25f, 0.24f, 0.11f),
            floatArrayOf(0.86f, 0.28f, 0.22f, 0.09f),
            floatArrayOf(0.15f, 0.74f, 0.24f, 0.08f),
            floatArrayOf(0.86f, 0.76f, 0.22f, 0.08f),
            floatArrayOf(0.50f, 0.48f, 0.38f, 0.045f)
        )
        for ((i, b) in nebulae.withIndex()) {
            val x = w * b[0]
            val y = h * b[1]
            val rr = w * b[2]
            val a = (255 * b[3]).toInt()
            p.style = Paint.Style.FILL
            p.shader = RadialGradient(
                x, y, rr,
                intArrayOf(
                    if (i % 2 == 0) Color.argb(a, 40, 120, 165) else Color.argb(a, 175, 130, 64),
                    Color.argb(a / 3, 10, 35, 58),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.48f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(x, y, rr, p)
            p.shader = null
        }

        val stars = intArrayOf(
            6,10, 12,18, 18,8, 24,14, 30,7, 36,20, 42,11, 49,7, 55,16, 62,9, 69,19,
            76,10, 83,16, 91,8, 9,32, 16,40, 24,28, 31,45, 39,35, 46,26, 54,38, 62,30,
            71,42, 80,31, 89,39, 95,28, 5,52, 13,60, 22,51, 31,58, 41,47, 51,55, 61,49,
            71,58, 82,51, 94,60, 6,72, 15,81, 25,69, 36,79, 46,70, 57,82, 67,73,
            78,84, 88,71, 94,80, 11,90, 28,91, 45,88, 61,92, 76,89, 91,94
        )
        for (i in stars.indices step 2) {
            val sx = w * stars[i] / 100f
            val sy = h * stars[i + 1] / 100f
            val rr = if (i % 8 == 0) 2.8f else 1.4f
            drawGlowDot(canvas, sx, sy, rr, if (i % 6 == 0) goldBright else cyan, 0.72f)
        }

        // 참고 이미지처럼 화면 가장자리에 은하 팔과 별자리 연결선을 추가한다.
        drawProceduralGalaxy(canvas, w * 0.08f, h * 0.15f, w * 0.24f, 18f, false)
        drawProceduralGalaxy(canvas, w * 0.91f, h * 0.20f, w * 0.21f, 202f, true)
        drawProceduralGalaxy(canvas, w * 0.10f, h * 0.78f, w * 0.18f, 120f, false)
        drawProceduralGalaxy(canvas, w * 0.92f, h * 0.76f, w * 0.18f, 300f, true)
        drawConstellationBackground(canvas, w, h)
        drawCelestialDust(canvas, w, h)
        drawReferenceStarFlares(canvas, w, h)
        drawCrescentPlanets(canvas, w, h)
    }

    private fun drawCelestialDust(canvas: Canvas, w: Float, h: Float) {
        for (i in 0 until 96) {
            val x = ((abs(sin((i + 17) * 14.731) * 23841.17) % 1.0).toFloat()) * w
            val y = ((abs(sin((i + 43) * 9.173) * 19317.77) % 1.0).toFloat()) * h
            val rr = when {
                i % 19 == 0 -> 1.6f
                i % 7 == 0 -> 1.0f
                else -> 0.55f
            }
            val color = when {
                i % 11 == 0 -> goldBright
                i % 5 == 0 -> cyanBright
                else -> white
            }
            p.style = Paint.Style.FILL
            p.color = withAlpha(color, if (i % 19 == 0) 135 else 70)
            canvas.drawCircle(x, y, rr, p)
        }
    }

    private fun drawProceduralGalaxy(
        canvas: Canvas,
        gx: Float,
        gy: Float,
        size: Float,
        rotation: Float,
        goldBias: Boolean
    ) {
        // 파일 이미지 대신 로그 나선 형태의 별들을 코드로 직접 그린다.
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(
            gx, gy, size * 0.42f,
            intArrayOf(
                Color.argb(72, 225, 240, 255),
                Color.argb(32, 90, 165, 230),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.38f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(gx, gy, size * 0.44f, p)
        p.shader = null

        for (arm in 0 until 3) {
            var previous: PointF? = null
            for (i in 0 until 96) {
                val t = i / 95f
                val angle = rotation + arm * 120f + t * 520f
                val wave = (sin((i + arm * 11) * 0.73).toFloat()) * size * 0.020f
                val radius = size * (0.045f + 0.94f * t) + wave
                val q = polar(gx, gy, radius, angle)
                val fade = (1f - t).coerceIn(0f, 1f)
                val col = if (goldBias && i % 5 == 0) goldBright else if (i % 3 == 0) white else cyan
                val alpha = (30 + 135 * fade).toInt().coerceIn(0, 170)
                val rr = maxOf(0.55f, size * (0.0018f + 0.0042f * fade))

                p.style = Paint.Style.FILL
                p.color = withAlpha(col, alpha)
                canvas.drawCircle(q.x, q.y, rr, p)

                if (i % 3 == 0 && previous != null) {
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = maxOf(0.45f, size * 0.0007f)
                    p.color = withAlpha(if (goldBias) gold else cyan, (alpha * 0.30f).toInt())
                    canvas.drawLine(previous.x, previous.y, q.x, q.y, p)
                }
                previous = q
            }
        }

        for (i in 0 until 9) {
            val a = rotation + i * 40f
            val q = polar(gx, gy, size * (0.14f + (i % 4) * 0.13f), a)
            drawGlowDot(
                canvas,
                q.x,
                q.y,
                maxOf(1.1f, size * 0.007f),
                if (i % 3 == 0) goldBright else white,
                0.78f
            )
        }
    }

    private fun drawConstellationBackground(canvas: Canvas, w: Float, h: Float) {
        val pts = arrayOf(
            PointF(w * 0.07f, h * 0.31f),
            PointF(w * 0.16f, h * 0.26f),
            PointF(w * 0.25f, h * 0.34f),
            PointF(w * 0.11f, h * 0.52f),
            PointF(w * 0.19f, h * 0.61f),
            PointF(w * 0.82f, h * 0.29f),
            PointF(w * 0.91f, h * 0.36f),
            PointF(w * 0.84f, h * 0.53f),
            PointF(w * 0.93f, h * 0.66f),
            PointF(w * 0.77f, h * 0.72f)
        )
        val links = arrayOf(
            intArrayOf(0, 1), intArrayOf(1, 2), intArrayOf(0, 3), intArrayOf(3, 4),
            intArrayOf(5, 6), intArrayOf(6, 7), intArrayOf(7, 8), intArrayOf(7, 9)
        )
        p.style = Paint.Style.STROKE
        p.strokeWidth = 0.75f
        p.color = Color.argb(48, 140, 205, 255)
        links.forEach { pair ->
            val a = pts[pair[0]]
            val b = pts[pair[1]]
            canvas.drawLine(a.x, a.y, b.x, b.y, p)
        }
        pts.forEachIndexed { index, q ->
            drawGlowDot(canvas, q.x, q.y, if (index % 3 == 0) 2.0f else 1.15f,
                if (index % 4 == 0) goldBright else cyan, 0.62f)
        }
    }

    private fun drawHeader(canvas: Canvas, w: Float, h: Float) {
        val cx = w / 2f
        drawStarBurst(canvas, cx, h * 0.055f, w * 0.018f, goldBright, 0.95f)

        textPaint.color = goldBright
        textPaint.textSize = w * 0.048f
        textPaint.setShadowLayer(w * 0.014f, 0f, 0f, Color.argb(135, 255, 225, 150))
        canvas.drawText("별을 읽는 성역", cx, h * 0.118f, textPaint)
        textPaint.clearShadowLayer()

        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.2f
        p.color = Color.argb(110, 220, 195, 125)
        canvas.drawLine(w * 0.16f, h * 0.145f, w * 0.84f, h * 0.145f, p)
        drawDiamond(canvas, cx, h * 0.145f, w * 0.009f, goldBright, 0.9f)

        textPaint.textSize = w * 0.025f
        textPaint.color = Color.argb(220, 220, 202, 160)
        canvas.drawText("지혜는 내일을 비춘다.", cx, h * 0.176f, textPaint)

        drawStarBurst(canvas, w * 0.25f, h * 0.145f, w * 0.006f, gold, 0.65f)
        drawStarBurst(canvas, w * 0.75f, h * 0.145f, w * 0.006f, gold, 0.65f)
    }

    private fun drawCircleSystem(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        r: Float,
        outerRuneRotation: Float,
        innerRuneRotation: Float
    ) {
        // 중심 원 전체를 감싸는 광원층. 기존 선 구조를 덮지 않도록 낮은 알파로만 사용한다.
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(
            cx, cy, r * 1.12f,
            intArrayOf(
                Color.argb(8, 255, 240, 190),
                Color.argb(11, 95, 195, 255),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.62f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r * 1.10f, p)
        p.shader = null

        // 8중 광륜
        drawGlowRing(canvas, cx, cy, r * 1.000f, goldBright, r * 0.0045f, 1.00f)
        drawGlowRing(canvas, cx, cy, r * 0.966f, goldBright, r * 0.0030f, 0.88f)
        drawGlowRing(canvas, cx, cy, r * 0.922f, cyanBright, r * 0.0040f, 0.96f)
        drawGlowRing(canvas, cx, cy, r * 0.875f, cyanBright, r * 0.0028f, 0.72f)
        drawGlowRing(canvas, cx, cy, r * 0.810f, gold, r * 0.0026f, 0.50f)
        drawGlowRing(canvas, cx, cy, r * 0.742f, cyan, r * 0.0022f, 0.45f)
        drawGlowRing(canvas, cx, cy, r * 0.664f, gold, r * 0.0018f, 0.28f)
        drawGlowRing(canvas, cx, cy, r * 0.586f, cyanDim, r * 0.0015f, 0.24f)

        // 192/144/120 미세 눈금
        drawTickRing(canvas, cx, cy, r * 0.982f, r * 0.048f, 192, 16)
        drawTickRing(canvas, cx, cy, r * 0.855f, r * 0.036f, 144, 12)
        drawTickRing(canvas, cx, cy, r * 0.725f, r * 0.030f, 120, 10)

        // 서로 반대 방향으로 회전하는 룬 밴드.
        canvas.save()
        canvas.rotate(outerRuneRotation, cx, cy)
        drawRuneBand(canvas, cx, cy, r * 0.905f, 84, goldBright, 0)
        drawMicroGlyphBand(canvas, cx, cy, r * 0.944f, 168)
        for (i in 0 until 288) {
            val q = polar(cx, cy, r * 0.835f, i * 1.25f)
            val rr = if (i % 12 == 0) r * 0.0048f else r * 0.0024f
            p.style = Paint.Style.FILL
            p.color = withAlpha(
                if (i % 12 == 0) goldBright else cyanBright,
                if (i % 12 == 0) 205 else 100
            )
            canvas.drawCircle(q.x, q.y, rr, p)
        }
        canvas.restore()

        canvas.save()
        canvas.rotate(innerRuneRotation, cx, cy)
        drawRuneBand(canvas, cx, cy, r * 0.777f, 84, cyan, 1)
        drawRuneBand(canvas, cx, cy, r * 0.700f, 88, gold, 2)
        canvas.restore()

        // 축선 및 외곽 마커
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.0032f
        p.color = withAlpha(gold, 170)
        canvas.drawLine(cx, cy - r * 0.99f, cx, cy + r * 0.99f, p)
        canvas.drawLine(cx - r * 0.99f, cy, cx + r * 0.99f, cy, p)
        p.strokeWidth = r * 0.0015f
        p.color = withAlpha(cyan, 52)
        canvas.drawLine(cx - r * 0.70f, cy - r * 0.70f, cx + r * 0.70f, cy + r * 0.70f, p)
        canvas.drawLine(cx + r * 0.70f, cy - r * 0.70f, cx - r * 0.70f, cy + r * 0.70f, p)

        for (angle in intArrayOf(0, 90, 180, 270)) {
            val q = polar(cx, cy, r * 1.035f, angle.toFloat())
            drawDiamond(canvas, q.x, q.y, r * 0.045f, goldBright, 0.95f)
            drawGlowDot(canvas, q.x, q.y, r * 0.010f, white, 0.95f)
        }

        // Sacred Geometry
        drawRegularPolygon(canvas, cx, cy, r * 0.69f, 12, 0f, cyan, r * 0.0024f, 0.56f)
        drawStar(canvas, cx, cy, r * 0.66f, r * 0.42f, 12, 0f, gold, r * 0.0026f, 0.78f)
        drawStar(canvas, cx, cy, r * 0.57f, r * 0.31f, 8, 22.5f, white, r * 0.0030f, 0.86f)
        drawRegularPolygon(canvas, cx, cy, r * 0.47f, 8, 22.5f, cyan, r * 0.0024f, 0.68f)
        drawRegularPolygon(canvas, cx, cy, r * 0.37f, 6, 30f, goldBright, r * 0.0028f, 0.76f)
        drawRegularPolygon(canvas, cx, cy, r * 0.31f, 6, 0f, cyanBright, r * 0.0021f, 0.50f)
        drawRegularPolygon(canvas, cx, cy, r * 0.25f, 4, 45f, gold, r * 0.0020f, 0.55f)

        for (i in 0 until 16) {
            val start = polar(cx, cy, r * 0.12f, i * 22.5f)
            val end = polar(cx, cy, r * 0.70f, i * 22.5f)
            p.color = if (i % 2 == 0) withAlpha(gold, 70) else withAlpha(cyan, 70)
            p.strokeWidth = r * 0.0016f
            canvas.drawLine(start.x, start.y, end.x, end.y, p)
        }

        drawOuterCrownSpikes(canvas, cx, cy, r)
        drawReferenceCardinalFlares(canvas, cx, cy, r)
        drawReferenceCrescentMarkers(canvas, cx, cy, r)

        canvas.save()
        canvas.rotate(outerRuneRotation * 0.72f, cx, cy)
        drawMajorRuneBand(canvas, cx, cy, r * 0.935f, 40)
        canvas.restore()

        drawAstrolabeMedallions(canvas, cx, cy, r)
        drawDecorativeArcs(canvas, cx, cy, r)
        drawLunarMarkers(canvas, cx, cy, r)
        drawConstellationMesh(canvas, cx, cy, r)
        drawReferenceSacredGeometry(canvas, cx, cy, r)

        // Layer 6: 신성한 기하학 구조는 고정.
        drawCelestialPetalLattice(canvas, cx, cy, r)

        drawAuxiliarySigils(canvas, cx, cy, r)
        drawMicroSigils(canvas, cx, cy, r)
        drawInnerInscriptionHalo(canvas, cx, cy, r)

        // Layer 7: 궤도 곡선과 노드는 회전하지 않는다.
        drawReferenceOrbitHalo(canvas, cx, cy, r)
        drawOrbits(canvas, cx, cy, r)

        // Layer 8: 중앙 코어는 확대/축소 맥동 없이 고정한다.
        drawCore(canvas, cx, cy, r)
        drawCrystalCoreOverlay(canvas, cx, cy, r)
        drawReferenceCoreHalo(canvas, cx, cy, r)
    }

    private fun drawTickRing(canvas: Canvas, cx: Float, cy: Float, radius: Float, length: Float, count: Int, majorEvery: Int) {
        for (i in 0 until count) {
            val a = i * (360f / count)
            val major = i % majorEvery == 0
            val medium = i % (majorEvery / 2).coerceAtLeast(1) == 0
            val inner = radius - if (major) length else if (medium) length * 0.62f else length * 0.32f
            val q1 = polar(cx, cy, inner, a)
            val q2 = polar(cx, cy, radius, a)
            p.style = Paint.Style.STROKE
            p.color = when {
                major -> withAlpha(goldBright, 235)
                medium -> withAlpha(cyan, 155)
                else -> withAlpha(cyanDim, 90)
            }
            p.strokeWidth = when {
                major -> maxOf(1.2f, length * 0.10f)
                medium -> maxOf(0.9f, length * 0.065f)
                else -> maxOf(0.6f, length * 0.040f)
            }
            canvas.drawLine(q1.x, q1.y, q2.x, q2.y, p)
        }
    }

    private fun drawRuneBand(canvas: Canvas, cx: Float, cy: Float, radius: Float, count: Int, color: Int, variant: Int) {
        for (i in 0 until count) {
            val a = i * (360f / count)
            val q = polar(cx, cy, radius, a)
            val s = radius * 0.018f
            canvas.save()
            canvas.rotate(a, q.x, q.y)
            p.style = Paint.Style.STROKE
            p.strokeWidth = maxOf(1f, radius * 0.0030f)
            p.color = withAlpha(if (i % 5 == 0) goldBright else color, if (variant == 0) 220 else 175)

            when ((i + variant) % 5) {
                0 -> {
                    canvas.drawLine(q.x - s, q.y, q.x + s, q.y, p)
                    canvas.drawLine(q.x, q.y - s, q.x, q.y + s, p)
                }
                1 -> {
                    path.reset()
                    path.moveTo(q.x - s, q.y + s)
                    path.lineTo(q.x, q.y - s)
                    path.lineTo(q.x + s, q.y + s)
                    path.lineTo(q.x, q.y + s * 0.3f)
                    path.close()
                    canvas.drawPath(path, p)
                }
                2 -> {
                    path.reset()
                    path.moveTo(q.x - s, q.y - s)
                    path.lineTo(q.x + s * 0.8f, q.y - s * 0.35f)
                    path.lineTo(q.x - s * 0.35f, q.y + s * 0.1f)
                    path.lineTo(q.x + s, q.y + s)
                    canvas.drawPath(path, p)
                }
                3 -> {
                    canvas.drawCircle(q.x, q.y, s * 0.70f, p)
                    drawDiamond(canvas, q.x, q.y, s * 0.46f, color, 0.85f)
                }
                else -> {
                    path.reset()
                    path.moveTo(q.x - s, q.y)
                    path.lineTo(q.x, q.y - s)
                    path.lineTo(q.x + s, q.y)
                    path.lineTo(q.x, q.y + s)
                    path.close()
                    canvas.drawPath(path, p)
                    canvas.drawLine(q.x - s * 0.5f, q.y, q.x + s * 0.5f, q.y, p)
                }
            }
            canvas.restore()
        }
    }

    private fun drawReferenceStarFlares(canvas: Canvas, w: Float, h: Float) {
        val stars = arrayOf(
            floatArrayOf(0.24f, 0.05f, 0.010f),
            floatArrayOf(0.81f, 0.085f, 0.008f),
            floatArrayOf(0.10f, 0.23f, 0.007f),
            floatArrayOf(0.93f, 0.28f, 0.007f),
            floatArrayOf(0.06f, 0.69f, 0.008f),
            floatArrayOf(0.89f, 0.67f, 0.009f),
            floatArrayOf(0.18f, 0.88f, 0.006f),
            floatArrayOf(0.78f, 0.89f, 0.006f)
        )
        for ((index, item) in stars.withIndex()) {
            val x = w * item[0]
            val y = h * item[1]
            val size = w * item[2]
            drawStarBurst(canvas, x, y, size, if (index % 3 == 0) goldBright else white, 0.90f)
            drawGlowDot(canvas, x, y, maxOf(1f, size * 0.08f), goldBright, 0.72f)
        }
    }

    private fun drawCrescentPlanets(canvas: Canvas, w: Float, h: Float) {
        fun planet(x: Float, y: Float, radius: Float, rightLight: Boolean) {
            p.style = Paint.Style.FILL
            p.color = Color.argb(22, 90, 150, 220)
            canvas.drawCircle(x, y, radius * 1.22f, p)

            p.color = Color.rgb(5, 10, 16)
            canvas.drawCircle(x, y, radius, p)

            p.style = Paint.Style.STROKE
            p.strokeWidth = maxOf(1f, radius * 0.07f)
            p.color = withAlpha(cyan, 95)
            canvas.drawCircle(x, y, radius, p)

            val offset = if (rightLight) -radius * 0.23f else radius * 0.23f
            val rect = RectF(
                x - radius * 0.92f + offset,
                y - radius * 0.92f,
                x + radius * 0.92f + offset,
                y + radius * 0.92f
            )
            p.strokeWidth = maxOf(1.1f, radius * 0.09f)
            p.color = withAlpha(goldBright, 150)
            canvas.drawArc(rect, if (rightLight) -74f else 106f, 148f, false, p)
        }

        planet(w * 0.055f, h * 0.70f, w * 0.034f, true)
        planet(w * 0.945f, h * 0.72f, w * 0.036f, false)
    }

    private fun drawReferenceCardinalFlares(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        for (angle in floatArrayOf(0f, 90f, 180f, 270f)) {
            val q = polar(cx, cy, r * 1.02f, angle)
            val s = r * 0.055f

            p.style = Paint.Style.STROKE
            p.strokeWidth = maxOf(1.2f, r * 0.0030f)
            p.color = withAlpha(goldBright, 230)

            val a1 = polar(q.x, q.y, s, 0f)
            val a2 = polar(q.x, q.y, s, 90f)
            val a3 = polar(q.x, q.y, s, 180f)
            val a4 = polar(q.x, q.y, s, 270f)

            path.reset()
            path.moveTo(a1.x, a1.y)
            path.lineTo(q.x + (a2.x - q.x) * 0.30f, q.y + (a2.y - q.y) * 0.30f)
            path.lineTo(a3.x, a3.y)
            path.lineTo(q.x + (a4.x - q.x) * 0.30f, q.y + (a4.y - q.y) * 0.30f)
            path.close()
            canvas.drawPath(path, p)

            drawGlowDot(canvas, q.x, q.y, r * 0.010f, white, 0.96f)
            p.strokeWidth = maxOf(0.9f, r * 0.0015f)
            p.color = withAlpha(goldBright, 110)
            canvas.drawCircle(q.x, q.y, r * 0.027f, p)
        }
    }

    private fun drawReferenceCrescentMarkers(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val angles = floatArrayOf(0f, 90f, 180f, 270f)
        for ((index, angle) in angles.withIndex()) {
            val q = polar(cx, cy, r * 0.825f, angle)
            val rr = r * 0.035f

            p.style = Paint.Style.FILL
            p.color = withAlpha(goldBright, 220)
            canvas.drawCircle(q.x, q.y, rr, p)

            p.color = bg
            val shift = if (index % 2 == 0) rr * 0.42f else -rr * 0.42f
            canvas.drawCircle(q.x + shift, q.y, rr * 0.88f, p)

            p.style = Paint.Style.STROKE
            p.strokeWidth = maxOf(0.9f, r * 0.0018f)
            p.color = withAlpha(goldBright, 170)
            canvas.drawCircle(q.x, q.y, rr, p)
        }
    }

    private fun drawMajorRuneBand(canvas: Canvas, cx: Float, cy: Float, radius: Float, count: Int) {
        for (i in 0 until count) {
            val angle = i * 360f / count
            val q = polar(cx, cy, radius, angle)
            val size = radius * 0.0175f

            canvas.save()
            canvas.rotate(angle, q.x, q.y)
            p.style = Paint.Style.STROKE
            p.strokeWidth = maxOf(1f, radius * 0.0027f)
            p.color = withAlpha(goldBright, 210)

            when (i % 8) {
                0 -> {
                    canvas.drawLine(q.x - size, q.y, q.x + size, q.y, p)
                    canvas.drawLine(q.x, q.y - size, q.x, q.y + size, p)
                }
                1 -> drawDiamond(canvas, q.x, q.y, size, goldBright, 0.85f)
                2 -> drawRegularPolygon(canvas, q.x, q.y, size, 3, angle, goldBright, maxOf(1f, radius * 0.0025f), 0.84f)
                3 -> drawRegularPolygon(canvas, q.x, q.y, size, 6, angle, goldBright, maxOf(1f, radius * 0.0025f), 0.82f)
                4 -> {
                    canvas.drawCircle(q.x, q.y, size * 0.72f, p)
                    canvas.drawLine(q.x - size * 0.7f, q.y, q.x + size * 0.7f, q.y, p)
                }
                5 -> {
                    path.reset()
                    path.moveTo(q.x - size, q.y + size * 0.7f)
                    path.lineTo(q.x, q.y - size)
                    path.lineTo(q.x + size, q.y + size * 0.7f)
                    canvas.drawPath(path, p)
                }
                6 -> {
                    canvas.drawLine(q.x - size, q.y - size, q.x + size, q.y + size, p)
                    canvas.drawLine(q.x + size, q.y - size, q.x - size, q.y + size, p)
                }
                else -> drawStar(canvas, q.x, q.y, size, size * 0.42f, 4, 45f, goldBright, maxOf(1f, radius * 0.0024f), 0.80f)
            }
            canvas.restore()
        }
    }

    private fun drawReferenceSacredGeometry(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        // Bright gold sacred-star structure, similar to the supplied reference.
        drawStar(canvas, cx, cy, r * 0.655f, r * 0.255f, 6, 0f, goldBright, maxOf(1.2f, r * 0.0042f), 0.74f)
        drawStar(canvas, cx, cy, r * 0.615f, r * 0.305f, 8, 22.5f, gold, maxOf(1.0f, r * 0.0030f), 0.54f)

        val outer = ArrayList<PointF>()
        for (i in 0 until 12) outer += polar(cx, cy, r * 0.61f, i * 30f)

        p.style = Paint.Style.STROKE
        p.strokeWidth = maxOf(0.9f, r * 0.0022f)
        for (i in outer.indices) {
            val a = outer[i]
            val b = outer[(i + 5) % outer.size]
            p.color = withAlpha(if (i % 2 == 0) goldBright else cyanBright, if (i % 2 == 0) 90 else 72)
            canvas.drawLine(a.x, a.y, b.x, b.y, p)
        }

        for (i in 0 until 12) {
            val q = polar(cx, cy, r * 0.59f, i * 30f)
            drawGlowDot(canvas, q.x, q.y, r * if (i % 3 == 0) 0.011f else 0.006f,
                if (i % 2 == 0) goldBright else cyanBright,
                if (i % 3 == 0) 0.90f else 0.64f)
        }
    }

    private fun drawReferenceOrbitHalo(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val rx = r * 0.61f
        val ry = r * 0.245f
        val rect = RectF(cx - rx, cy - ry, cx + rx, cy + ry)

        for (rot in floatArrayOf(0f, 45f, 90f, 135f)) {
            canvas.save()
            canvas.rotate(rot, cx, cy)

            p.style = Paint.Style.STROKE
            p.strokeWidth = maxOf(1.0f, r * 0.0048f)
            p.color = withAlpha(cyanBright, 42)
            canvas.drawOval(rect, p)

            p.strokeWidth = maxOf(0.9f, r * 0.0027f)
            p.color = withAlpha(cyanBright, 205)
            canvas.drawOval(rect, p)

            canvas.restore()
        }

        for (i in 0 until 8) {
            val angle = i * 45f
            val q = polar(cx, cy, r * 0.61f, angle)
            drawGlowDot(canvas, q.x, q.y, r * 0.012f, if (i % 2 == 0) cyanBright else goldBright, 0.95f)
        }
    }

    private fun drawReferenceCoreHalo(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        p.style = Paint.Style.STROKE

        val rings = floatArrayOf(0.215f, 0.232f, 0.250f)
        for ((index, scale) in rings.withIndex()) {
            p.strokeWidth = maxOf(0.9f, r * if (index == 0) 0.0032f else 0.0018f)
            p.color = withAlpha(if (index % 2 == 0) goldBright else cyanBright, if (index == 0) 130 else 75)
            canvas.drawCircle(cx, cy, r * scale, p)
        }

        for (i in 0 until 24) {
            val q = polar(cx, cy, r * 0.267f, i * 15f)
            drawGlowDot(
                canvas,
                q.x,
                q.y,
                r * if (i % 6 == 0) 0.0052f else 0.0026f,
                if (i % 2 == 0) goldBright else cyanBright,
                if (i % 6 == 0) 0.68f else 0.34f
            )
        }
    }

    private fun drawOuterCrownSpikes(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        for (i in 0 until 32) {
            val angle = i * 11.25f
            val major = i % 4 == 0
            val start = polar(cx, cy, r * 0.985f, angle)
            val end = polar(cx, cy, r * if (major) 1.055f else 1.025f, angle)

            p.style = Paint.Style.STROKE
            p.strokeWidth = if (major) maxOf(1.0f, r * 0.0023f) else maxOf(0.6f, r * 0.0012f)
            p.color = withAlpha(if (i % 2 == 0) goldBright else cyan, if (major) 180 else 80)
            canvas.drawLine(start.x, start.y, end.x, end.y, p)

            if (major) {
                val q = polar(cx, cy, r * 1.067f, angle)
                drawDiamond(canvas, q.x, q.y, r * 0.012f, if (i % 8 == 0) goldBright else cyan, 0.72f)
            }
        }
    }

    private fun drawAstrolabeMedallions(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        for (i in 0 until 12) {
            val angle = i * 30f
            val q = polar(cx, cy, r * 0.615f, angle)
            val sr = r * 0.040f

            p.style = Paint.Style.STROKE
            p.strokeWidth = maxOf(0.7f, r * 0.0014f)
            p.color = withAlpha(if (i % 2 == 0) gold else cyan, 120)
            canvas.drawCircle(q.x, q.y, sr, p)

            p.color = withAlpha(white, 55)
            canvas.drawCircle(q.x, q.y, sr * 0.68f, p)

            when (i % 4) {
                0 -> drawStar(canvas, q.x, q.y, sr * 0.55f, sr * 0.24f, 5, angle, goldBright, maxOf(0.7f, r * 0.0012f), 0.66f)
                1 -> drawRegularPolygon(canvas, q.x, q.y, sr * 0.52f, 6, angle, cyan, maxOf(0.7f, r * 0.0012f), 0.58f)
                2 -> drawDiamond(canvas, q.x, q.y, sr * 0.48f, goldBright, 0.62f)
                else -> {
                    p.color = withAlpha(cyanBright, 130)
                    canvas.drawLine(q.x - sr * 0.42f, q.y, q.x + sr * 0.42f, q.y, p)
                    canvas.drawLine(q.x, q.y - sr * 0.42f, q.x, q.y + sr * 0.42f, p)
                }
            }

            drawGlowDot(canvas, q.x, q.y, sr * 0.085f, white, 0.72f)
        }
    }

    private fun drawCelestialPetalLattice(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val oval = RectF(
            cx - r * 0.47f,
            cy - r * 0.135f,
            cx + r * 0.47f,
            cy + r * 0.135f
        )

        for (i in 0 until 12) {
            canvas.save()
            canvas.rotate(i * 30f, cx, cy)
            p.style = Paint.Style.STROKE
            p.strokeWidth = maxOf(0.65f, r * 0.00145f)
            p.color = withAlpha(if (i % 2 == 0) cyanBright else goldBright, 72)
            canvas.drawOval(oval, p)
            canvas.restore()
        }
    }

    private fun drawInnerInscriptionHalo(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val radius = r * 0.365f
        for (i in 0 until 48) {
            val angle = i * 7.5f
            val q = polar(cx, cy, radius, angle)
            val size = if (i % 6 == 0) r * 0.010f else r * 0.006f

            p.style = Paint.Style.STROKE
            p.strokeWidth = maxOf(0.55f, r * 0.0010f)
            p.color = withAlpha(if (i % 3 == 0) goldBright else cyan, if (i % 6 == 0) 105 else 60)

            when (i % 6) {
                0 -> drawDiamond(canvas, q.x, q.y, size, goldBright, 0.46f)
                1 -> canvas.drawCircle(q.x, q.y, size * 0.7f, p)
                2 -> {
                    canvas.drawLine(q.x - size, q.y, q.x + size, q.y, p)
                    canvas.drawLine(q.x, q.y - size, q.x, q.y + size, p)
                }
                3 -> drawRegularPolygon(canvas, q.x, q.y, size, 3, angle, cyan, maxOf(0.55f, r * 0.0010f), 0.42f)
                4 -> drawRegularPolygon(canvas, q.x, q.y, size, 6, angle, gold, maxOf(0.55f, r * 0.0010f), 0.40f)
                else -> drawStar(canvas, q.x, q.y, size, size * 0.42f, 4, angle, white, maxOf(0.55f, r * 0.0010f), 0.36f)
            }
        }
    }

    private fun drawCrystalCoreOverlay(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val coreR = r * 0.185f

        drawRegularPolygon(canvas, cx, cy, coreR, 16, 11.25f, white, maxOf(0.7f, r * 0.0012f), 0.28f)
        drawStar(canvas, cx, cy, coreR * 0.92f, coreR * 0.42f, 12, 0f, goldBright, maxOf(0.7f, r * 0.0012f), 0.34f)
        drawStar(canvas, cx, cy, coreR * 0.78f, coreR * 0.34f, 8, 22.5f, cyanBright, maxOf(0.7f, r * 0.0012f), 0.38f)

        for (i in 0 until 16) {
            val angle = i * 22.5f
            val a = polar(cx, cy, r * 0.072f, angle)
            val b = polar(cx, cy, r * 0.182f, angle)
            p.style = Paint.Style.STROKE
            p.strokeWidth = maxOf(0.55f, r * 0.0010f)
            p.color = withAlpha(if (i % 2 == 0) goldBright else cyanBright, 48)
            canvas.drawLine(a.x, a.y, b.x, b.y, p)
        }

        for (i in 0 until 12) {
            val q = polar(cx, cy, r * 0.225f, i * 30f)
            drawGlowDot(
                canvas,
                q.x,
                q.y,
                r * if (i % 3 == 0) 0.0060f else 0.0036f,
                if (i % 2 == 0) goldBright else cyanBright,
                if (i % 3 == 0) 0.70f else 0.48f
            )
        }
    }

    private fun drawMicroGlyphBand(canvas: Canvas, cx: Float, cy: Float, radius: Float, count: Int) {
        for (i in 0 until count) {
            val angle = i * (360f / count)
            val q = polar(cx, cy, radius, angle)
            val size = radius * if (i % 12 == 0) 0.010f else 0.006f
            p.style = Paint.Style.STROKE
            p.strokeWidth = maxOf(0.65f, radius * 0.0014f)
            p.color = withAlpha(if (i % 6 == 0) goldBright else cyan, if (i % 12 == 0) 185 else 95)

            when (i % 4) {
                0 -> drawDiamond(canvas, q.x, q.y, size, if (i % 6 == 0) goldBright else cyan, 0.70f)
                1 -> canvas.drawCircle(q.x, q.y, size * 0.72f, p)
                2 -> {
                    canvas.drawLine(q.x - size, q.y, q.x + size, q.y, p)
                    canvas.drawLine(q.x, q.y - size, q.x, q.y + size, p)
                }
                else -> {
                    path.reset()
                    path.moveTo(q.x - size, q.y + size * 0.6f)
                    path.lineTo(q.x, q.y - size)
                    path.lineTo(q.x + size, q.y + size * 0.6f)
                    canvas.drawPath(path, p)
                }
            }
        }
    }

    private fun drawLunarMarkers(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        for (i in 0 until 8) {
            val angle = i * 45f
            val q = polar(cx, cy, r * 0.865f, angle)
            val rr = r * 0.024f

            if (i % 2 == 0) {
                p.style = Paint.Style.FILL
                p.color = withAlpha(goldBright, 195)
                canvas.drawCircle(q.x, q.y, rr, p)
                p.color = bg
                val offset = if (i % 4 == 0) rr * 0.42f else -rr * 0.42f
                canvas.drawCircle(q.x + offset, q.y, rr * 0.88f, p)
                p.style = Paint.Style.STROKE
                p.strokeWidth = maxOf(0.8f, r * 0.0016f)
                p.color = withAlpha(goldBright, 180)
                canvas.drawCircle(q.x, q.y, rr, p)
            } else {
                drawStarBurst(canvas, q.x, q.y, rr * 1.25f, cyan, 0.80f)
                drawDiamond(canvas, q.x, q.y, rr * 0.62f, goldBright, 0.68f)
            }
        }
    }

    private fun drawConstellationMesh(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val nodes = ArrayList<PointF>()
        for (i in 0 until 24) {
            val rr = when (i % 3) {
                0 -> r * 0.58f
                1 -> r * 0.49f
                else -> r * 0.42f
            }
            nodes += polar(cx, cy, rr, i * 15f)
        }

        p.style = Paint.Style.STROKE
        for (i in nodes.indices) {
            val a = nodes[i]
            val b = nodes[(i + 5) % nodes.size]
            val d = nodes[(i + 9) % nodes.size]
            p.strokeWidth = maxOf(0.55f, r * 0.0012f)
            p.color = withAlpha(if (i % 2 == 0) cyan else gold, if (i % 3 == 0) 58 else 34)
            canvas.drawLine(a.x, a.y, b.x, b.y, p)
            if (i % 3 == 0) {
                p.color = withAlpha(goldBright, 26)
                canvas.drawLine(a.x, a.y, d.x, d.y, p)
            }
        }

        nodes.forEachIndexed { index, q ->
            drawGlowDot(
                canvas,
                q.x,
                q.y,
                if (index % 6 == 0) r * 0.0075f else r * 0.0037f,
                if (index % 4 == 0) goldBright else cyanBright,
                if (index % 6 == 0) 0.82f else 0.48f
            )
        }
    }

    private fun drawMicroSigils(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        for (i in 0 until 16) {
            val angle = i * 22.5f + 11.25f
            val q = polar(cx, cy, r * 0.615f, angle)
            val sr = r * 0.027f

            p.style = Paint.Style.STROKE
            p.strokeWidth = maxOf(0.6f, r * 0.0011f)
            p.color = withAlpha(if (i % 2 == 0) gold else cyan, 100)
            canvas.drawCircle(q.x, q.y, sr, p)

            if (i % 2 == 0) {
                drawDiamond(canvas, q.x, q.y, sr * 0.58f, goldBright, 0.55f)
            } else {
                drawRegularPolygon(canvas, q.x, q.y, sr * 0.62f, 3, angle, cyan, maxOf(0.6f, r * 0.0011f), 0.55f)
            }
            drawGlowDot(canvas, q.x, q.y, sr * 0.12f, white, 0.62f)
        }
    }

    private fun drawDecorativeArcs(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        p.style = Paint.Style.STROKE
        for (i in 0 until 12) {
            val outer = RectF(cx - r * 0.60f, cy - r * 0.60f, cx + r * 0.60f, cy + r * 0.60f)
            p.color = withAlpha(if (i % 2 == 0) gold else cyan, 80)
            p.strokeWidth = r * 0.0018f
            canvas.drawArc(outer, i * 30f + 6f, 17f, false, p)

            val inner = RectF(cx - r * 0.52f, cy - r * 0.52f, cx + r * 0.52f, cy + r * 0.52f)
            p.color = withAlpha(if (i % 2 == 0) cyan else gold, 52)
            p.strokeWidth = r * 0.0014f
            canvas.drawArc(inner, i * 30f + 13f, 12f, false, p)
        }
    }

    private fun drawAuxiliarySigils(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        for (i in 0 until 8) {
            val angle = i * 45f
            val q = polar(cx, cy, r * 0.53f, angle)
            val sr = r * 0.072f
            val col = if (i % 2 == 0) cyanBright else goldBright
            drawGlowRing(canvas, q.x, q.y, sr, col, r * 0.0018f, 0.85f)
            drawGlowRing(canvas, q.x, q.y, sr * 0.68f, white, r * 0.0012f, 0.42f)

            when (i % 4) {
                0 -> {
                    drawDiamond(canvas, q.x, q.y, sr * 0.46f, goldBright, 0.92f)
                    p.color = withAlpha(cyan, 180)
                    p.strokeWidth = r * 0.0015f
                    canvas.drawLine(q.x - sr * 0.5f, q.y, q.x + sr * 0.5f, q.y, p)
                    canvas.drawLine(q.x, q.y - sr * 0.5f, q.x, q.y + sr * 0.5f, p)
                }
                1 -> {
                    drawRegularPolygon(canvas, q.x, q.y, sr * 0.52f, 3, angle, col, r * 0.0015f, 0.85f)
                    drawRegularPolygon(canvas, q.x, q.y, sr * 0.52f, 3, angle + 180f, white, r * 0.0012f, 0.50f)
                }
                2 -> drawStar(canvas, q.x, q.y, sr * 0.55f, sr * 0.24f, 5, angle, col, r * 0.0015f, 0.85f)
                else -> {
                    drawRegularPolygon(canvas, q.x, q.y, sr * 0.54f, 6, angle, col, r * 0.0015f, 0.85f)
                    drawDiamond(canvas, q.x, q.y, sr * 0.28f, white, 0.70f)
                }
            }
            drawGlowDot(canvas, q.x, q.y, sr * 0.10f, white, 0.95f)
        }
    }

    private fun drawOrbits(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val configs = arrayOf(
            floatArrayOf(0f, 0.61f, 0.22f),
            floatArrayOf(36f, 0.60f, 0.235f),
            floatArrayOf(72f, 0.59f, 0.205f),
            floatArrayOf(108f, 0.56f, 0.17f),
            floatArrayOf(144f, 0.56f, 0.17f)
        )
        for ((i, c) in configs.withIndex()) {
            canvas.save()
            canvas.rotate(c[0], cx, cy)
            val rect = RectF(cx - r * c[1], cy - r * c[2], cx + r * c[1], cy + r * c[2])
            val col = if (i % 2 == 0) cyanBright else goldBright
            p.style = Paint.Style.STROKE
            p.color = withAlpha(col, 30)
            p.strokeWidth = r * 0.011f
            canvas.drawOval(rect, p)
            p.color = withAlpha(col, if (i < 3) 215 else 105)
            p.strokeWidth = if (i < 3) r * 0.004f else r * 0.002f
            canvas.drawOval(rect, p)
            canvas.restore()
        }

        val nodeAngles = floatArrayOf(0f, 45f, 90f, 135f, 180f, 225f, 270f, 315f)
        for ((i, a) in nodeAngles.withIndex()) {
            val rr = if (i % 2 == 0) r * 0.60f else r * 0.51f
            val q = polar(cx, cy, rr, a)
            drawGlowDot(canvas, q.x, q.y, if (i % 2 == 0) r * 0.020f else r * 0.015f,
                if (i % 2 == 0) cyanBright else goldBright, 0.95f)
        }
    }

    private fun drawCore(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(
            cx, cy, r * 0.42f,
            intArrayOf(
                Color.argb(118, 255, 255, 255),
                Color.argb(56, 255, 232, 170),
                Color.argb(24, 120, 220, 255),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.22f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r * 0.42f, p)
        p.shader = null

        p.color = darkCore
        canvas.drawCircle(cx, cy, r * 0.30f, p)
        drawGlowRing(canvas, cx, cy, r * 0.305f, cyan, r * 0.0040f, 0.95f)
        drawGlowRing(canvas, cx, cy, r * 0.260f, gold, r * 0.0018f, 0.44f)
        drawGlowRing(canvas, cx, cy, r * 0.220f, white, r * 0.0012f, 0.30f)

        drawRegularPolygon(canvas, cx, cy, r * 0.235f, 6, 30f, goldBright, r * 0.0026f, 0.52f)
        drawRegularPolygon(canvas, cx, cy, r * 0.195f, 6, 0f, cyan, r * 0.0022f, 0.45f)
        drawRegularPolygon(canvas, cx, cy, r * 0.150f, 8, 22.5f, cyanBright, r * 0.0018f, 0.42f)
        drawRegularPolygon(canvas, cx, cy, r * 0.125f, 4, 45f, white, r * 0.0016f, 0.36f)
        drawStar(canvas, cx, cy, r * 0.160f, r * 0.070f, 8, 22.5f, gold, r * 0.0017f, 0.38f)
        drawRegularPolygon(canvas, cx, cy, r * 0.105f, 12, 0f, white, r * 0.0012f, 0.30f)
        drawStar(canvas, cx, cy, r * 0.112f, r * 0.052f, 12, 7.5f, cyanBright, r * 0.0012f, 0.34f)

        p.style = Paint.Style.STROKE
        for (i in 0 until 24) {
            val start = polar(cx, cy, r * 0.090f, i * 15f)
            val end = polar(cx, cy, r * 0.205f, i * 15f)
            p.strokeWidth = maxOf(0.55f, r * 0.0010f)
            p.color = withAlpha(if (i % 2 == 0) goldBright else cyan, 52)
            canvas.drawLine(start.x, start.y, end.x, end.y, p)
        }

        for (i in 0 until 24) {
            val q = polar(cx, cy, r * 0.335f, i * 15f)
            p.style = Paint.Style.FILL
            p.color = withAlpha(if (i % 2 == 0) goldBright else cyanBright, 165)
            canvas.drawCircle(q.x, q.y, r * 0.0038f, p)
        }

        for (i in 0 until 36) {
            val q = polar(cx, cy, r * 0.280f, i * 10f)
            p.style = Paint.Style.FILL
            p.color = withAlpha(if (i % 3 == 0) goldBright else cyan, if (i % 3 == 0) 115 else 65)
            canvas.drawCircle(q.x, q.y, if (i % 3 == 0) r * 0.0029f else r * 0.0019f, p)
        }
    }

    private fun drawCenterPanel(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        r: Float,
        displayPercent: Float
    ) {
        val panelRadius = r * 0.205f

        drawGlowRing(
            canvas,
            cx,
            cy,
            panelRadius * 1.055f,
            cyanBright,
            r * 0.0014f,
            0.28f
        )

        p.style = Paint.Style.FILL
        p.color = Color.argb(215, 3, 9, 15)
        canvas.drawCircle(cx, cy, panelRadius, p)

        textPaint.color = goldBright
        textPaint.textSize = r * 0.225f
        textPaint.typeface = Typeface.create("sans", Typeface.NORMAL)
        textPaint.setShadowLayer(r * 0.035f, 0f, 0f, Color.argb(145, 255, 230, 160))
        val shownPercent = displayPercent.roundToInt().coerceIn(0, 100)
        canvas.drawText(shownPercent.toString(), cx - r * 0.020f, cy + r * 0.030f, textPaint)
        textPaint.clearShadowLayer()
        textPaint.textSize = r * 0.070f
        canvas.drawText("%", cx + r * 0.165f, cy + r * 0.035f, textPaint)

        p.style = Paint.Style.STROKE
        p.strokeWidth = 1f
        p.color = Color.argb(120, 220, 190, 115)
        canvas.drawLine(cx - r * 0.17f, cy + r * 0.105f, cx + r * 0.17f, cy + r * 0.105f, p)
        drawDiamond(canvas, cx, cy + r * 0.105f, r * 0.010f, goldBright, 0.85f)

        textPaint.textSize = r * 0.052f
        textPaint.color = Color.argb(235, 245, 229, 199)
        canvas.drawText(connectionText, cx, cy + r * 0.19f, textPaint)
    }

    private fun drawFooter(canvas: Canvas, w: Float, h: Float) {
        val cx = w / 2f
        val y = h * 0.805f

        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.2f
        p.color = Color.argb(135, 230, 205, 145)
        canvas.drawLine(w * 0.16f, y, w * 0.84f, y, p)
        drawDiamond(canvas, cx, y, w * 0.008f, goldBright, 0.85f)

        textPaint.textSize = w * 0.023f
        textPaint.color = Color.argb(220, 220, 202, 160)
        canvas.drawText("지혜는 더 밝은 내일을 비춘다.", cx, h * 0.847f, textPaint)

        val baseY = h * 0.895f
        drawInfo(canvas, w * 0.17f, baseY, "%.1f°C".format(batteryTempC), "배터리 온도", w)
        drawInfo(canvas, w * 0.50f, baseY, batteryHealthText, "배터리 상태", w)
        drawInfo(canvas, w * 0.83f, baseY, connectionText, "연결 방식", w)

        p.color = Color.argb(70, 255, 255, 255)
        p.strokeWidth = 1f
        canvas.drawLine(w * 0.335f, h * 0.873f, w * 0.335f, h * 0.947f, p)
        canvas.drawLine(w * 0.665f, h * 0.873f, w * 0.665f, h * 0.947f, p)

        textPaint.textSize = w * 0.019f
        textPaint.color = Color.argb(190, 245, 225, 172)
        canvas.drawText("✦  ·  ◔  ◑  ◉  ◐  ◕  ·  ✦", cx, h * 0.965f, textPaint)
    }

    private fun drawInfo(canvas: Canvas, x: Float, y: Float, value: String, label: String, w: Float) {
        textPaint.color = Color.rgb(255, 241, 210)
        textPaint.textSize = w * if (value.length > 6) 0.033f else 0.041f
        canvas.drawText(value, x, y, textPaint)
        textPaint.color = Color.rgb(205, 188, 153)
        textPaint.textSize = w * 0.022f
        canvas.drawText(label, x, y + w * 0.055f, textPaint)
    }

    private fun drawGlowRing(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int, stroke: Float, alpha: Float) {
        // Blur 없이 안전한 다중 반투명 스트로크 Glow.
        val effectiveAlpha = (alpha * dynamicGlowMultiplier).coerceIn(0f, 1f)
        p.style = Paint.Style.STROKE

        p.strokeWidth = maxOf(1f, stroke * 7.5f)
        p.color = withAlpha(color, (effectiveAlpha * 18).toInt())
        canvas.drawCircle(cx, cy, r, p)

        p.strokeWidth = maxOf(1f, stroke * 4.5f)
        p.color = withAlpha(color, (effectiveAlpha * 30).toInt())
        canvas.drawCircle(cx, cy, r, p)

        p.strokeWidth = maxOf(1f, stroke * 2.4f)
        p.color = withAlpha(color, (effectiveAlpha * 62).toInt())
        canvas.drawCircle(cx, cy, r, p)

        p.strokeWidth = maxOf(0.8f, stroke)
        p.color = withAlpha(color, (effectiveAlpha * 255).toInt())
        canvas.drawCircle(cx, cy, r, p)
    }

    private fun drawGlowDot(canvas: Canvas, x: Float, y: Float, rr: Float, color: Int, alpha: Float) {
        val effectiveAlpha = (alpha * dynamicGlowMultiplier).coerceIn(0f, 1f)
        p.style = Paint.Style.FILL

        p.color = withAlpha(color, (effectiveAlpha * 20).toInt())
        canvas.drawCircle(x, y, rr * 4.0f, p)

        p.color = withAlpha(color, (effectiveAlpha * 42).toInt())
        canvas.drawCircle(x, y, rr * 2.6f, p)

        p.color = withAlpha(color, (effectiveAlpha * 95).toInt())
        canvas.drawCircle(x, y, rr * 1.65f, p)

        p.color = withAlpha(color, (effectiveAlpha * 255).toInt())
        canvas.drawCircle(x, y, rr, p)

        p.color = withAlpha(white, (effectiveAlpha * 245).toInt())
        canvas.drawCircle(x, y, maxOf(0.5f, rr * 0.28f), p)
    }

    private fun drawRegularPolygon(
        canvas: Canvas, cx: Float, cy: Float, r: Float, sides: Int, rotation: Float,
        color: Int, width: Float, alpha: Float
    ) {
        path.reset()
        for (i in 0 until sides) {
            val q = polar(cx, cy, r, rotation + i * (360f / sides))
            if (i == 0) path.moveTo(q.x, q.y) else path.lineTo(q.x, q.y)
        }
        path.close()
        p.style = Paint.Style.STROKE
        p.color = withAlpha(color, (255 * alpha).toInt())
        p.strokeWidth = width
        canvas.drawPath(path, p)
    }

    private fun drawStar(
        canvas: Canvas, cx: Float, cy: Float, outer: Float, inner: Float, tips: Int, rotation: Float,
        color: Int, width: Float, alpha: Float
    ) {
        path.reset()
        for (i in 0 until tips * 2) {
            val rr = if (i % 2 == 0) outer else inner
            val q = polar(cx, cy, rr, rotation + i * (180f / tips))
            if (i == 0) path.moveTo(q.x, q.y) else path.lineTo(q.x, q.y)
        }
        path.close()
        p.style = Paint.Style.STROKE
        p.color = withAlpha(color, (255 * alpha).toInt())
        p.strokeWidth = width
        canvas.drawPath(path, p)
    }

    private fun drawDiamond(canvas: Canvas, x: Float, y: Float, size: Float, color: Int, alpha: Float) {
        path.reset()
        path.moveTo(x, y - size)
        path.lineTo(x + size, y)
        path.lineTo(x, y + size)
        path.lineTo(x - size, y)
        path.close()
        p.style = Paint.Style.STROKE
        p.strokeWidth = maxOf(1f, size * 0.10f)
        p.color = withAlpha(color, (255 * alpha).toInt())
        canvas.drawPath(path, p)
    }

    private fun drawStarBurst(canvas: Canvas, x: Float, y: Float, size: Float, color: Int, alpha: Float) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = maxOf(1f, size * 0.08f)
        p.color = withAlpha(color, (255 * alpha).toInt())
        canvas.drawLine(x - size, y, x + size, y, p)
        canvas.drawLine(x, y - size, x, y + size, p)
        canvas.drawLine(x - size * 0.45f, y - size * 0.45f, x + size * 0.45f, y + size * 0.45f, p)
        canvas.drawLine(x + size * 0.45f, y - size * 0.45f, x - size * 0.45f, y + size * 0.45f, p)
        p.style = Paint.Style.FILL
        p.color = withAlpha(color, 190)
        canvas.drawCircle(x, y, size * 0.12f, p)
    }

    private fun polar(cx: Float, cy: Float, r: Float, deg: Float): PointF {
        val a = (deg - 90f) * PI / 180.0
        return PointF(
            cx + cos(a).toFloat() * r,
            cy + sin(a).toFloat() * r
        )
    }

    private fun withAlpha(color: Int, a: Int): Int = Color.argb(
        a.coerceIn(0, 255),
        Color.red(color),
        Color.green(color),
        Color.blue(color)
    )
}
