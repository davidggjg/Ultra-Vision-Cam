package com.example.crazycamera

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark

class FaceOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val boxPaint = Paint().apply {
        color = Color.parseColor("#44FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val emojiPaint = Paint().apply {
        textSize = 60f
        isAntiAlias = true
    }

    private var faces: List<Face> = emptyList()
    private var imgWidth = 1
    private var imgHeight = 1
    private var isFront = false

    fun setFaces(faces: List<Face>, w: Int, h: Int, front: Boolean) {
        this.faces = faces
        this.imgWidth = w
        this.imgHeight = h
        this.isFront = front
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (faces.isEmpty()) return

        val scaleX = width.toFloat() / imgHeight
        val scaleY = height.toFloat() / imgWidth

        for (face in faces) {
            val b = face.boundingBox
            val left = if (isFront) width - b.right * scaleX else b.left * scaleX
            val right = if (isFront) width - b.left * scaleX else b.right * scaleX
            val top = b.top * scaleY
            val bottom = b.bottom * scaleY

            // מסגרת עדינה
            canvas.drawRoundRect(left, top, right, bottom, 20f, 20f, boxPaint)

            val smile = face.smilingProbability ?: 0f
            val leftEye = face.leftEyeOpenProbability ?: 1f
            val rightEye = face.rightEyeOpenProbability ?: 1f

            // אימוג'י מעל הפנים
            val emoji = when {
                leftEye < 0.3f && rightEye < 0.3f -> "😴"
                smile > 0.8f -> "😁"
                smile > 0.5f -> "😊"
                leftEye < 0.3f -> "😉"
                else -> "😐"
            }
            canvas.drawText(emoji, left, top - 10f, emojiPaint)

            // נקודת אף
            face.getLandmark(FaceLandmark.NOSE_BASE)?.let {
                val nx = if (isFront) width - it.position.x * scaleX else it.position.x * scaleX
                val ny = it.position.y * scaleY
                canvas.drawCircle(nx, ny, 8f, boxPaint)
            }
        }
    }
}
