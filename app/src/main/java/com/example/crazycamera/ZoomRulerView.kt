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

    private val paintTick = Paint().apply {
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }

    private val paintYellow = Paint().apply {
        color = Color.YELLOW
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val paintBg = Paint().apply {
        color = Color.parseColor("#88000000")
    }

    private var linearZoom = 0.1f
    private var displayRatio = 1f
    private var lastX = 0f
    private val totalTicks = 300
    private val tickSpacing = 14f

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
                linearZoom = (linearZoom + dx / (width * 2.5f)).coerceIn(0f, 1f)
                onZoomChanged?.invoke(linearZoom)
                invalidate()
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0) return

        // רקע שקוף
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(),
            20f, 20f, paintBg)

        val cx = width / 2f
        val cy = height / 2f
        val currentPos = linearZoom * (totalTicks * tickSpacing)

        // ציור פסים
        for (i in 0..totalTicks) {
            val tickPos = i * tickSpacing
            val screenX = cx + (tickPos - currentPos)
            if (screenX < 4f || screenX > width - 4f) continue

            val isMajor = (i % 25 == 0)
            val isMedium = (i % 5 == 0)
            val tickH = when {
                isMajor -> cy * 0.65f
                isMedium -> cy * 0.45f
                else -> cy * 0.25f
            }
            paintTick.color = when {
                isMajor -> Color.WHITE
                isMedium -> Color.parseColor("#BBFFFFFF")
                else -> Color.parseColor("#55FFFFFF")
            }
            paintTick.strokeWidth = if (isMajor) 3f else if (isMedium) 2f else 1f
            canvas.drawLine(screenX, cy - tickH, screenX, cy + tickH, paintTick)
        }

        // קו אינדיקטור צהוב במרכז
        paintYellow.strokeWidth = 3f
        paintYellow.style = Paint.Style.STROKE
        canvas.drawLine(cx, cy * 0.15f, cx, cy * 1.85f, paintYellow)

        // טקסט זום
        paintYellow.style = Paint.Style.FILL
        paintYellow.textSize = height * 0.32f
        val label = if (displayRatio < 10f)
            String.format("%.1fx", displayRatio)
        else
            String.format("%.0fx", displayRatio)
        canvas.drawText(label, cx, cy * 0.38f, paintYellow)
    }
}
