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

    private val paintWhite = Paint().apply {
        color = Color.WHITE
        strokeWidth = 2f
        isAntiAlias = true
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }

    private val paintYellow = Paint().apply {
        color = Color.YELLOW
        strokeWidth = 3f
        isAntiAlias = true
        textSize = 38f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private var linearZoom = 0.1f
    private var displayRatio = 1f
    private var lastX = 0f
    private val numTicks = 200
    private val tickSpacing = 18f

    fun setLinearZoom(linear: Float, ratio: Float) {
        linearZoom = linear.coerceIn(0f, 1f)
        displayRatio = ratio
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> lastX = event.x
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastX
                lastX = event.x
                linearZoom = (linearZoom + dx / (width * 2f)).coerceIn(0f, 1f)
                onZoomChanged?.invoke(linearZoom)
                invalidate()
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0) return
        val cx = width / 2f
        val cy = height / 2f
        val currentPos = linearZoom * (numTicks * tickSpacing)

        for (i in 0..numTicks) {
            val tickPos = i * tickSpacing
            val screenX = cx + (tickPos - currentPos)
            if (screenX < 0 || screenX > width) continue
            val isMajor = (i % 10 == 0)
            val tickH = if (isMajor) cy * 0.6f else cy * 0.3f
            paintWhite.color = if (isMajor) Color.WHITE
                else Color.parseColor("#66FFFFFF")
            paintWhite.strokeWidth = if (isMajor) 3f else 1.5f
            canvas.drawLine(screenX, cy - tickH, screenX, cy + tickH, paintWhite)
        }

        paintYellow.strokeWidth = 3f
        canvas.drawLine(cx, cy * 0.2f, cx, cy * 1.8f, paintYellow)

        val label = if (displayRatio < 10f)
            String.format("%.1fx", displayRatio)
        else String.format("%.0fx", displayRatio)
        canvas.drawText(label, cx, cy * 0.15f, paintYellow)
    }
}
