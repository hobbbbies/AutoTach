package com.example.obdreader.car

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Log
import android.view.Surface
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import com.example.obdreader.R
import com.example.obdreader.data.ObdRepository
import kotlin.math.cos
import kotlin.math.sin

class ObdSurface(private val carContext: CarContext, private val repository: ObdRepository) : SurfaceCallback{
    private var surface: Surface? = null
    private var stableArea: Rect = Rect()
    private var visibleArea: Rect = Rect()
    private val idleRpm = 900
    private val maxRpm = 6000
    private val maxSpeed = 180
    private val segmentCount = 20
    private val segmentGap = 6f

    override fun onVisibleAreaChanged(area: Rect) { visibleArea = area; render() }
    override fun onStableAreaChanged(area: Rect)  { stableArea = area;  render() }

    override fun onSurfaceDestroyed(container: SurfaceContainer) {
        surface = null
    }

    override fun onSurfaceAvailable(container: SurfaceContainer) {
        surface = container.surface
        render()
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 24f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.MONOSPACE
    }

    private val gaugePaint = Paint().apply {
        color = Color.BLACK
    }

    private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 12f
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
    }

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }

    private val barTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x40000000.toInt()
        style = Paint.Style.FILL
    }

    private var bgBitmap: Bitmap? = null

    private fun getBackground(): Bitmap {
        return bgBitmap ?: BitmapFactory.decodeResource(
            carContext.resources,
            R.drawable.dashboard_bg
        ).also { bgBitmap = it }
    }

    private fun drawMeter(canvas: Canvas) {
        val bg = getBackground()
        val cw = canvas.width.toFloat()
        val ch = canvas.height.toFloat()
        val scale = maxOf(cw / bg.width, ch / bg.height)
        val drawW = bg.width * scale
        val drawH = bg.height * scale
        val left = (cw - drawW) / 2f
        val top  = (ch - drawH) / 2f

        canvas.drawBitmap(
            bg,
            null,                                       // src = whole bitmap
            RectF(left, top, left + drawW, top + drawH), // dst = scaled & centered
            null
        )
    }

    private fun drawBars(canvas: Canvas, rpm: Int, speed: Int) {
        val cw = canvas.width.toFloat()
        val ch = canvas.height.toFloat()
        val barHeight = ch * 0.18f
        val rowGap = ch * 0.04f      // between the two rows
        val textGap = 8f             // between header bottom and its bar
        val sideInset = cw * 0.05f
        val barWidth = cw - sideInset * 2f

        val fm = textPaint.fontMetrics
        val textHeight = fm.descent - fm.ascent

        val rowHeight = textHeight + textGap + barHeight
        val topY = (ch - (rowHeight * 2 + rowGap)) / 2f
        val centerX = sideInset + barWidth / 2f

        val rpmPct = ((rpm - idleRpm).coerceIn(0, maxRpm - idleRpm)).toFloat() / (maxRpm - idleRpm)
        val speedPct = speed.coerceIn(0, maxSpeed).toFloat() / maxSpeed

        // Row 1: RPM header + bar
        drawHeaderText(canvas, "RPM", centerX, topY - fm.ascent)
        drawSegmentedBar(canvas, sideInset, topY + textHeight + textGap, barWidth, barHeight, rpmPct)

        // Row 2: SPEED header + bar
        val row2Y = topY + rowHeight + rowGap
        drawHeaderText(canvas, "SPEED", centerX, row2Y - fm.ascent)
        drawSegmentedBar(canvas, sideInset, row2Y + textHeight + textGap, barWidth, barHeight, speedPct)
    }

    private fun drawSegmentedBar(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, pct: Float) {
        val totalGap = segmentGap * (segmentCount - 1)
        val segW = (w - totalGap) / segmentCount
        val litCount = (pct * segmentCount).toInt().coerceIn(0, segmentCount)
        for (i in 0 until segmentCount) {
            val left = x + i * (segW + segmentGap)
            val paint = if (i < litCount) {
                barPaint.apply { color = segmentColor(i.toFloat() / (segmentCount - 1)) }
            } else {
                barTrackPaint
            }
            canvas.drawRect(left, y, left + segW, y + h, paint)
        }
    }

    private fun segmentColor(pct: Float): Int {
        val shifted = (pct + 2f / (segmentCount - 1)).coerceAtMost(1f)
        val hue = 120f * (1f - shifted)                // 120 green -> 0 red, shifted 2 segs toward red
        return Color.HSVToColor(floatArrayOf(hue, 1f, 0.9f))
    }

    private fun drawHeaderText(canvas: Canvas, text: String, x: Float, y: Float) {
        canvas.drawText(text, x, y, textPaint)
    }

    private fun drawNeedle(canvas: Canvas, rpm: Int) {
        val cx = stableArea.exactCenterX()
        val cy = stableArea.exactCenterY() + 125f
        val radius = minOf(stableArea.width(), stableArea.height()) * 0.8f

        val pct = ((rpm - idleRpm).coerceIn(0, maxRpm - idleRpm)).toFloat() / (maxRpm - idleRpm)
        val angleRad = Math.toRadians((180f + pct * 180f).toDouble())

        val tipX = cx + (radius * cos(angleRad)).toFloat()
        val tipY = cy + (radius * sin(angleRad)).toFloat()

        canvas.drawLine(cx, cy, tipX, tipY, needlePaint)
    }

    fun render() {
        val s = surface ?: return
        if (!s.isValid) return

        val canvas = try {
            s.lockCanvas(null)
        } catch (e: IllegalArgumentException) {
            null
        } ?: return

        try {
            var rpm = repository.rpm.value ?: 900
            var speed = repository.speed.value ?: 0
            if (rpm > maxRpm) rpm = maxRpm - 1
            canvas.drawColor(bgFor(rpm))
            drawBars(canvas, rpm, speed)

//            val text = if (rpm == null) "-- rpm" else "$rpm rpm"
//            val cx = stableArea.exactCenterX()
//            val fm = textPaint.fontMetrics
//            val cy = stableArea.exactCenterY() - (fm.ascent + fm.descent) / 2f
//
//            canvas.drawText(text, cx, cy, textPaint)
//
//            val radius = 50f
//            val gaugeRect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
//            val sweep = (rpm.toFloat() / 6000f) * 240f  // 0–6000 RPM maps to 0–240°
//            canvas.drawArc(gaugeRect, 150f, sweep, false, gaugePaint)
        } finally {
            try {
                s.unlockCanvasAndPost(canvas)
            } catch (e: IllegalArgumentException) {
                // Ignore if surface was destroyed in the meantime
            }
        }
    }

    private fun bgFor(rpm: Int?): Int = 0xFF202124.toInt()

//    private fun bgFor(rpm: Int?): Int = when {
//        rpm == null  -> 0xFF202124.toInt()
//        rpm < 3000   -> 0xFF1B5E20.toInt()
//        rpm < 5000   -> 0xFFF9A825.toInt()
//        else         -> 0xFFB71C1C.toInt()
//    }
}