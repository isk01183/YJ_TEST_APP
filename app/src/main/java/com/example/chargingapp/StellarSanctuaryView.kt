package com.example.chargingapp

import android.content.Context
import android.content.Intent
import android.graphics.BlurMaskFilter
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
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
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

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        setBackgroundColor(bg)
        isClickable = true
    }

    fun updateFromBatteryIntent(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level >= 0 && scale > 0) {
            batteryPercent = ((level * 100f) / scale).toInt().coerceIn(0, 100)
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

        canvas.drawColor(bg)
        drawBackdrop(canvas, w, h)
        drawHeader(canvas, w, h)

        val cx = w * 0.5f
        val cy = h * 0.49f
        val r = min(w * 0.46f, h * 0.31f)

        drawCircleSystem(canvas, cx, cy, r)
        drawCenterPanel(canvas, cx, cy, r)
        drawFooter(canvas, w, h)
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
    }

    private fun drawHeader(canvas: Canvas, w: Float, h: Float) {
        val cx = w / 2f
        drawStarBurst(canvas, cx, h * 0.055f, w * 0.018f, goldBright, 0.95f)

        textPaint.color = goldBright
        textPaint.textSize = w * 0.057f
        textPaint.setShadowLayer(w * 0.014f, 0f, 0f, Color.argb(135, 255, 225, 150))
        canvas.drawText("별을 읽는 성역", cx, h * 0.12f, textPaint)
        textPaint.clearShadowLayer()

        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.2f
        p.color = Color.argb(110, 220, 195, 125)
        canvas.drawLine(w * 0.16f, h * 0.145f, w * 0.84f, h * 0.145f, p)
        drawDiamond(canvas, cx, h * 0.145f, w * 0.009f, goldBright, 0.9f)

        textPaint.textSize = w * 0.028f
        textPaint.color = Color.argb(220, 220, 202, 160)
        canvas.drawText("지혜는 내일을 비춘다.", cx, h * 0.178f, textPaint)
    }

    private fun drawCircleSystem(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        // 8중 광륜
        drawGlowRing(canvas, cx, cy, r * 1.000f, gold, r * 0.0042f, 0.95f)
        drawGlowRing(canvas, cx, cy, r * 0.966f, goldBright, r * 0.0025f, 0.74f)
        drawGlowRing(canvas, cx, cy, r * 0.922f, cyan, r * 0.0038f, 0.92f)
        drawGlowRing(canvas, cx, cy, r * 0.875f, cyanBright, r * 0.0020f, 0.50f)
        drawGlowRing(canvas, cx, cy, r * 0.810f, gold, r * 0.0026f, 0.50f)
        drawGlowRing(canvas, cx, cy, r * 0.742f, cyan, r * 0.0022f, 0.45f)
        drawGlowRing(canvas, cx, cy, r * 0.664f, gold, r * 0.0018f, 0.28f)
        drawGlowRing(canvas, cx, cy, r * 0.586f, cyanDim, r * 0.0015f, 0.24f)

        // 192/144/120 미세 눈금
        drawTickRing(canvas, cx, cy, r * 0.982f, r * 0.048f, 192, 16)
        drawTickRing(canvas, cx, cy, r * 0.855f, r * 0.036f, 144, 12)
        drawTickRing(canvas, cx, cy, r * 0.725f, r * 0.030f, 120, 10)

        // 이중 룬 밴드 + 발광 점 밴드
        drawRuneBand(canvas, cx, cy, r * 0.905f, 64, goldBright, 0)
        drawRuneBand(canvas, cx, cy, r * 0.777f, 56, cyan, 1)
        for (i in 0 until 240) {
            val q = polar(cx, cy, r * 0.835f, i * 1.5f)
            val rr = if (i % 12 == 0) r * 0.0048f else r * 0.0024f
            p.style = Paint.Style.FILL
            p.color = withAlpha(if (i % 12 == 0) goldBright else cyanBright, if (i % 12 == 0) 205 else 100)
            canvas.drawCircle(q.x, q.y, rr, p)
        }

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

        drawDecorativeArcs(canvas, cx, cy, r)
        drawAuxiliarySigils(canvas, cx, cy, r)
        drawOrbits(canvas, cx, cy, r)
        drawCore(canvas, cx, cy, r)
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

    private fun drawDecorativeArcs(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        p.style = Paint.Style.STROKE
        for (i in 0 until 8) {
            val outer = RectF(cx - r * 0.60f, cy - r * 0.60f, cx + r * 0.60f, cy + r * 0.60f)
            p.color = withAlpha(if (i % 2 == 0) gold else cyan, 80)
            p.strokeWidth = r * 0.0018f
            canvas.drawArc(outer, i * 45f + 10f, 24f, false, p)

            val inner = RectF(cx - r * 0.52f, cy - r * 0.52f, cx + r * 0.52f, cy + r * 0.52f)
            p.color = withAlpha(if (i % 2 == 0) cyan else gold, 52)
            p.strokeWidth = r * 0.0014f
            canvas.drawArc(inner, i * 45f + 18f, 18f, false, p)
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

        for (i in 0 until 24) {
            val q = polar(cx, cy, r * 0.335f, i * 15f)
            p.style = Paint.Style.FILL
            p.color = withAlpha(if (i % 2 == 0) goldBright else cyanBright, 165)
            canvas.drawCircle(q.x, q.y, r * 0.0038f, p)
        }
    }

    private fun drawCenterPanel(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        p.style = Paint.Style.FILL
        p.color = Color.argb(205, 3, 9, 15)
        canvas.drawCircle(cx, cy, r * 0.205f, p)

        textPaint.color = goldBright
        textPaint.textSize = r * 0.20f
        textPaint.typeface = Typeface.create("sans", Typeface.NORMAL)
        textPaint.setShadowLayer(r * 0.035f, 0f, 0f, Color.argb(145, 255, 230, 160))
        canvas.drawText(batteryPercent.toString(), cx - r * 0.025f, cy + r * 0.025f, textPaint)
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
        val y = h * 0.815f

        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.2f
        p.color = Color.argb(95, 218, 190, 120)
        canvas.drawLine(w * 0.16f, y, w * 0.84f, y, p)
        drawDiamond(canvas, cx, y, w * 0.008f, goldBright, 0.85f)

        textPaint.textSize = w * 0.027f
        textPaint.color = Color.argb(220, 220, 202, 160)
        canvas.drawText("지혜는 더 밝은 내일을 비춘다.", cx, h * 0.847f, textPaint)

        val baseY = h * 0.905f
        drawInfo(canvas, w * 0.17f, baseY, "%.1f°C".format(batteryTempC), "배터리 온도", w)
        drawInfo(canvas, w * 0.50f, baseY, batteryHealthText, "배터리 상태", w)
        drawInfo(canvas, w * 0.83f, baseY, connectionText, "연결 방식", w)

        p.color = Color.argb(70, 255, 255, 255)
        p.strokeWidth = 1f
        canvas.drawLine(w * 0.335f, h * 0.873f, w * 0.335f, h * 0.947f, p)
        canvas.drawLine(w * 0.665f, h * 0.873f, w * 0.665f, h * 0.947f, p)

        textPaint.textSize = w * 0.019f
        textPaint.color = Color.argb(190, 245, 225, 172)
        canvas.drawText("·  ◔  ◑  ◉  ◐  ◕  ·", cx, h * 0.975f, textPaint)
    }

    private fun drawInfo(canvas: Canvas, x: Float, y: Float, value: String, label: String, w: Float) {
        textPaint.color = Color.rgb(255, 241, 210)
        textPaint.textSize = w * if (value.length > 6) 0.031f else 0.039f
        canvas.drawText(value, x, y, textPaint)
        textPaint.color = Color.rgb(186, 170, 139)
        textPaint.textSize = w * 0.022f
        canvas.drawText(label, x, y + w * 0.055f, textPaint)
    }

    private fun drawGlowRing(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int, stroke: Float, alpha: Float) {
        p.style = Paint.Style.STROKE
        p.maskFilter = BlurMaskFilter(maxOf(1f, stroke * 7f), BlurMaskFilter.Blur.NORMAL)
        p.strokeWidth = stroke * 4.5f
        p.color = withAlpha(color, (alpha * 45).toInt())
        canvas.drawCircle(cx, cy, r, p)
        p.maskFilter = null
        p.strokeWidth = stroke * 2.3f
        p.color = withAlpha(color, (alpha * 70).toInt())
        canvas.drawCircle(cx, cy, r, p)
        p.strokeWidth = stroke
        p.color = withAlpha(color, (alpha * 255).toInt())
        canvas.drawCircle(cx, cy, r, p)
    }

    private fun drawGlowDot(canvas: Canvas, x: Float, y: Float, rr: Float, color: Int, alpha: Float) {
        p.style = Paint.Style.FILL
        p.maskFilter = BlurMaskFilter(maxOf(1f, rr * 2.8f), BlurMaskFilter.Blur.NORMAL)
        p.color = withAlpha(color, (alpha * 100).toInt())
        canvas.drawCircle(x, y, rr * 1.7f, p)
        p.maskFilter = null
        p.color = withAlpha(color, (alpha * 255).toInt())
        canvas.drawCircle(x, y, rr, p)
        p.color = withAlpha(white, (alpha * 245).toInt())
        canvas.drawCircle(x, y, rr * 0.28f, p)
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
