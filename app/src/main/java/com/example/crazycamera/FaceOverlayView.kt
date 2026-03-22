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

    enum class Filter {
        NONE, DOG, GLASSES, CROWN, BEARD, BUNNY, HEART_EYES
    }

    var currentFilter = Filter.NONE

    private val boxPaint = Paint().apply {
        color = Color.parseColor("#44FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    private val emojiPaint = Paint().apply {
        isAntiAlias = true
        textSize = 80f
    }

    private val smilePaint = Paint().apply {
        isAntiAlias = true
        textSize = 60f
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
            val faceWidth = right - left
            val faceHeight = bottom - top
            val cx = (left + right) / 2f

            when (currentFilter) {
                Filter.NONE -> {
                    // רק אימוג'י רגשות
                    val smile = face.smilingProbability ?: 0f
                    val leftEye = face.leftEyeOpenProbability ?: 1f
                    val rightEye = face.rightEyeOpenProbability ?: 1f
                    val emoji = when {
                        leftEye < 0.3f && rightEye < 0.3f -> "😴"
                        smile > 0.8f -> "😁"
                        smile > 0.5f -> "😊"
                        leftEye < 0.3f -> "😉"
                        else -> ""
                    }
                    if (emoji.isNotEmpty()) {
                        canvas.drawText(emoji, left, top - 10f, emojiPaint)
                    }
                }

                Filter.DOG -> {
                    // אוזני כלב
                    emojiPaint.textSize = faceWidth * 0.5f
                    canvas.drawText("🐾", left - faceWidth * 0.1f, top, emojiPaint)
                    canvas.drawText("🐾", right - faceWidth * 0.4f, top, emojiPaint)
                    // אף כלב
                    emojiPaint.textSize = faceWidth * 0.3f
                    canvas.drawText("🐽", cx - faceWidth * 0.15f,
                        top + faceHeight * 0.6f, emojiPaint)
                    // לשון
                    emojiPaint.textSize = faceWidth * 0.25f
                    canvas.drawText("👅", cx - faceWidth * 0.12f,
                        top + faceHeight * 0.85f, emojiPaint)
                }

                Filter.GLASSES -> {
                    emojiPaint.textSize = faceWidth * 0.5f
                    canvas.drawText("🕶️", left, top + faceHeight * 0.35f, emojiPaint)
                }

                Filter.CROWN -> {
                    emojiPaint.textSize = faceWidth * 0.6f
                    canvas.drawText("👑", left, top, emojiPaint)
                }

                Filter.BEARD -> {
                    emojiPaint.textSize = faceWidth * 0.5f
                    canvas.drawText("🧔", left, top + faceHeight * 0.7f, emojiPaint)
                }

                Filter.BUNNY -> {
                    // אוזני ארנב
                    emojiPaint.textSize = faceWidth * 0.4f
                    canvas.drawText("🐰", left, top - faceHeight * 0.2f, emojiPaint)
                }

                Filter.HEART_EYES -> {
                    emojiPaint.textSize = faceWidth * 0.6f
                    canvas.drawText("😍", left, top + faceHeight * 0.5f, emojiPaint)
                }
            }

            // נקודת מיקוד עדינה
            face.getLandmark(FaceLandmark.NOSE_BASE)?.let {
                val nx = if (isFront) width - it.position.x * scaleX
                    else it.position.x * scaleX
                val ny = it.position.y * scaleY
                canvas.drawCircle(nx, ny, 4f, boxPaint)
            }
        }
    }
}
