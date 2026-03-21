package com.example.crazycamera

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.google.mlkit.vision.face.Face

class FaceOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint().apply {
        color = Color.CYAN
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val textPaint = Paint().apply {
        color = Color.YELLOW
        textSize = 48f
        typeface = Typeface.DEFAULT_BOLD
    }

    private var faces: List<Face> = emptyList()
    private var imageWidth = 1
    private var imageHeight = 1

    fun setFaces(faces: List<Face>, width: Int, height: Int) {
        this.faces = faces
        this.imageWidth = width
        this.imageHeight = height
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scaleX = width.toFloat() / imageHeight
        val scaleY = height.toFloat() / imageWidth

        for (face in faces) {
            val bounds = face.boundingBox
            val left = bounds.left * scaleX
            val top = bounds.top * scaleY
            val right = bounds.right * scaleX
            val bottom = bounds.bottom * scaleY

            canvas.drawRect(left, top, right, bottom, paint)

            val smileProb = face.smilingProbability ?: 0f
            if (smileProb > 0.7f) {
                canvas.drawText("😊", left, top - 10, textPaint)
            }

            face.allLandmarks.forEach { landmark ->
                val x = landmark.position.x * scaleX
                val y = landmark.position.y * scaleY
                canvas.drawCircle(x, y, 6f, paint)
            }
        }
    }
}
