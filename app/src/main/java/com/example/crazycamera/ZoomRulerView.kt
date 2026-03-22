package com.example.crazycamera

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class ZoomRulerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var onZoomChanged: ((Float) -> Unit)? = null

    private val tickPaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 2f
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 28f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    private val indicatorPaint = Paint().apply {
        color = Color.YELLOW
        strokeWidth = 4f
        isAntiAlias = true
    }

    private val zoomTextPaint = Paint().apply {
        color = Color.YELLOW
        textSize = 36f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val zoomLevels = listOf(0.6f, 1f, 2f, 3f, 5f, 10f, 30f, 100f)
    private var currentZoom = 1f
    private var scrollOffset = 0f
    private var lastTouchX = 0f
    private val totalWidth = 2000f

    fun setZoom(zoom: Float) {
        currentZoom = zoom.coerceIn(0.6f, 100f)
        scrollOffset = zoomToScroll(currentZoom)
        invalidate()
    }

    private fun zoomToScroll(zoom: Float): Float {
        val idx = zoomLevels.indexOfFirst { zoom <= it }
        if (idx <= 0) return 0f
        if (idx >= zoomLevels.size) return totalWidth
        val low = zoomLevels[idx - 1]
        val high = zoomLevels[idx]
        val t = (zoom - low) / (high - low)
        val segmentWidth = totalWidth / (zoomLevels.size - 1)
        return (idx - 1 + t) * segmentWidth
    }

    private fun scrollToZoom(scroll: Float): Float {
        val segmentWidth = totalWidth / (zoomLevels.size - 1)
        val segIdx = (scroll / segmentWidth).toInt().coerceIn(0, zoomLevels.size - 2)
        val t = (scroll / segmentWidth - segIdx).coerceIn(0f, 1f)
        return zoomLevels[segIdx] + t * (zoomLevels[segIdx + 1] - zoomLevels[segIdx])
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> lastTouchX = event.x
            MotionEvent.ACTION_MOVE -> {
                val dx = lastTouchX - event.x
                scrollOffset = (scrollOffset + dx * 2f).coerceIn(0f, totalWidth)
                lastTouchX = event.x
                currentZoom = scrollToZoom(scrollOffset)
                onZoomChanged?.invoke(currentZoom)
                invalidate()
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val pixelsPerUnit = width / 200f

        for (i in 0..200) {
            val scrollPos = scrollOffset - 100 + i
            val x = i * pixelsPerUnit
            val isMain = zoomLevels.any { lvl ->
                val s = zoomToScroll(lvl)
                s in (scrollPos - 0.5f)..(scrollPos + 0.5f)
            }
            val tickH = if (isMain) cy * 0.7f else cy * 0.35f
            tickPaint.color = if (isMain) Color.WHITE else Color.parseColor("#88FFFFFF")
            canvas.drawLine(x, cy - tickH, x, cy + tickH, tickPaint)
        }

        for (zoom in zoomLevels) {
            val s = zoomToScroll(zoom)
            val x = (s - scrollOffset + 100) * pixelsPerUnit
            if (x in 0f..width.toFloat()) {
                val label = if (zoom < 1f) ".6" else zoom.toInt().toString()
                canvas.drawText(label, x, height - 8f, textPaint)
            }
        }

        canvas.drawLine(cx, 0f, cx, cy * 1.5f, indicatorPaint)

        val label = String.format("%.1fx", currentZoom)
        canvas.drawText(label, cx, cy * 0.4f, zoomTextPaint)
    }
}
