package com.example.obdreader.car

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.view.Surface
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import com.example.obdreader.data.ObdRepository

class ObdSurface(private val carContext: CarContext, private val repository: ObdRepository) : SurfaceCallback{
    private var surface: Surface? = null
    private var stableArea: Rect = Rect()
    private var visibleArea: Rect = Rect()

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
        color = Color.WHITE
        textSize = 220f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    fun render() {
        val s = surface ?: return
        val canvas = s.lockCanvas(null) ?: return
        try {
            val rpm = repository.rpm.value
            canvas.drawColor(bgFor(rpm))

            val text = if (rpm == null) "-- rpm" else "${rpm.toInt()} rpm"
            val cx = stableArea.exactCenterX()
            val fm = textPaint.fontMetrics
            val cy = stableArea.exactCenterY() - (fm.ascent + fm.descent) / 2f

            canvas.drawText(text, cx, cy, textPaint)
        } finally {
            s.unlockCanvasAndPost(canvas)
        }
    }

    private fun bgFor(rpm: Float?): Int = when {
        rpm == null  -> 0xFF202124.toInt()
        rpm < 3000   -> 0xFF1B5E20.toInt()
        rpm < 5000   -> 0xFFF9A825.toInt()
        else         -> 0xFFB71C1C.toInt()
    }
}