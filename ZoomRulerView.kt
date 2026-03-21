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

    // זום: 0.6x עד 100x
    private val zoomLevels = listOf(0.6f, 1f, 2f, 3f, 5f, 10f, 30f, 100f)
    private var currentZoom = 1f
    private var scrollX2 = 0f
    private var lastTouchX = 0f
    private val totalWidth = 2000f

    fun setZoom(zoom: Float) {
        currentZoom = zoom.coerceIn(0.6f, 100f)
        // חשב מיקום ב-scroll
        scrollX2 = zoomToScroll(currentZoom)
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
                scrollX2 = (scrollX2 + dx * 2f).coerceIn(0f, totalWidth)
                lastTouchX = event.x
                currentZoom = scrollToZoom(scrollX2)
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
        val segmentWidth = totalWidth / (zoomLevels.size - 1)
        val pixelsPerUnit = width / 200f

        // צייר פסים
        for (i in 0..200) {
            val scrollPos = scrollX2 - 100 + i
            val x = i * pixelsPerUnit
            val isMainTick = zoomLevels.any { zoomToScroll(it) in (scrollPos - 0.5f)..(scrollPos + 0.5f) }
            val tickHeight = if (isMainTick) cy * 0.7f else cy * 0.35f
            tickPaint.color = if (isMainTick) Color.WHITE else Color.parseColor("#88FFFFFF")
            canvas.drawLine(x, cy - tickHeight, x, cy + tickHeight, tickPaint)
        }

        // ציור מספרי זום
        for (zoom in zoomLevels) {
            val scrollPos = zoomToScroll(zoom)
            val x = (scrollPos - scrollX2 + 100) * pixelsPerUnit
            if (x in 0f..width.toFloat()) {
                val label = if (zoom < 1f) ".6" else zoom.toInt().toString()
                canvas.drawText(label, x, height - 8f, textPaint)
            }
        }

        // אינדיקטור מרכזי צהוב
        canvas.drawLine(cx, 0f, cx, cy * 1.5f, indicatorPaint)

        // הצג זום נוכחי
        val zoomLabel = if (currentZoom < 1f) String.format("%.1fx", currentZoom)
            else String.format("%.1fx", currentZoom)
        canvas.drawText(zoomLabel, cx, cy - cy * 0.8f, zoomTextPaint)
    }
}
