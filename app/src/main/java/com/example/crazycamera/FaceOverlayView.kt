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
        NONE, DOG, GLASSES, CROWN, BUNNY, HEART_EYES, BEARD
    }

    var currentFilter = Filter.NONE

    private val emojiPaint = Paint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
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

    private fun scaleX(x: Float): Float =
        if (isFront) width - x * width.toFloat() / imgHeight
        else x * width.toFloat() / imgHeight

    private fun scaleY(y: Float): Float =
        y * height.toFloat() / imgWidth

    private fun getLandmark(face: Face, type: Int): PointF? {
        val lm = face.getLandmark(type) ?: return null
        return PointF(scaleX(lm.position.x), scaleY(lm.position.y))
    }

    private fun drawEmoji(canvas: Canvas, emoji: String, x: Float, y: Float, size: Float) {
        emojiPaint.textSize = size
        canvas.drawText(emoji, x, y, emojiPaint)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (faces.isEmpty()) return

        for (face in faces) {
            val b = face.boundingBox
            val left = scaleX(b.left.toFloat())
            val right = scaleX(b.right.toFloat())
            val top = scaleY(b.top.toFloat())
            val bottom = scaleY(b.bottom.toFloat())
            val faceW = Math.abs(right - left)
            val faceH = bottom - top
            val cx = (left + right) / 2f

            // נקודות landmark מדויקות
            val leftEye = getLandmark(face, FaceLandmark.LEFT_EYE)
            val rightEye = getLandmark(face, FaceLandmark.RIGHT_EYE)
            val noseBase = getLandmark(face, FaceLandmark.NOSE_BASE)
            val leftEar = getLandmark(face, FaceLandmark.LEFT_EAR)
            val rightEar = getLandmark(face, FaceLandmark.RIGHT_EAR)
            val mouthLeft = getLandmark(face, FaceLandmark.MOUTH_LEFT)
            val mouthRight = getLandmark(face, FaceLandmark.MOUTH_RIGHT)
            val mouthBottom = getLandmark(face, FaceLandmark.MOUTH_BOTTOM)
            val leftCheek = getLandmark(face, FaceLandmark.LEFT_CHEEK)
            val rightCheek = getLandmark(face, FaceLandmark.RIGHT_CHEEK)

            val smile = face.smilingProbability ?: 0f
            val leftEyeOpen = face.leftEyeOpenProbability ?: 1f
            val rightEyeOpen = face.rightEyeOpenProbability ?: 1f

            when (currentFilter) {
                Filter.NONE -> {
                    // אימוג'י רגשות מעל הפנים
                    val emoji = when {
                        leftEyeOpen < 0.2f && rightEyeOpen < 0.2f -> "😴"
                        smile > 0.85f -> "😁"
                        smile > 0.6f -> "😊"
                        leftEyeOpen < 0.3f -> "😉"
                        else -> ""
                    }
                    if (emoji.isNotEmpty()) {
                        drawEmoji(canvas, emoji, cx, top - faceW * 0.1f, faceW * 0.5f)
                    }
                }

                Filter.DOG -> {
                    // אוזני כלב על האוזניים
                    leftEar?.let {
                        drawEmoji(canvas, "🐾", it.x, it.y - faceW * 0.3f, faceW * 0.45f)
                    }
                    rightEar?.let {
                        drawEmoji(canvas, "🐾", it.x, it.y - faceW * 0.3f, faceW * 0.45f)
                    }
                    // אף כלב על הבסיס
                    noseBase?.let {
                        drawEmoji(canvas, "🐽", it.x, it.y + faceW * 0.05f, faceW * 0.3f)
                    }
                    // לשון על הפה
                    mouthBottom?.let {
                        drawEmoji(canvas, "👅", it.x, it.y + faceW * 0.15f, faceW * 0.25f)
                    }
                    // לחיות
                    leftCheek?.let {
                        drawEmoji(canvas, "・", it.x, it.y, faceW * 0.2f)
                    }
                    rightCheek?.let {
                        drawEmoji(canvas, "・", it.x, it.y, faceW * 0.2f)
                    }
                }

                Filter.GLASSES -> {
                    // משקפיים בדיוק על העיניים
                    if (leftEye != null && rightEye != null) {
                        val eyesCx = (leftEye.x + rightEye.x) / 2f
                        val eyesCy = (leftEye.y + rightEye.y) / 2f
                        val eyesDist = Math.abs(rightEye.x - leftEye.x)
                        drawEmoji(canvas, "🕶️", eyesCx, eyesCy + eyesDist * 0.3f,
                            eyesDist * 1.4f)
                    }
                }

                Filter.CROWN -> {
                    // כתר מעל הראש
                    drawEmoji(canvas, "👑", cx, top - faceW * 0.05f, faceW * 0.7f)
                    // ניצוצות
                    leftEar?.let {
                        drawEmoji(canvas, "✨", it.x - faceW * 0.1f, top, faceW * 0.25f)
                    }
                    rightEar?.let {
                        drawEmoji(canvas, "✨", it.x + faceW * 0.1f, top, faceW * 0.25f)
                    }
                }

                Filter.BUNNY -> {
                    // אוזני ארנב מעל הראש
                    drawEmoji(canvas, "🐰", cx - faceW * 0.15f, top - faceW * 0.5f,
                        faceW * 0.35f)
                    drawEmoji(canvas, "🐰", cx + faceW * 0.15f, top - faceW * 0.5f,
                        faceW * 0.35f)
                    // אף ארנב
                    noseBase?.let {
                        drawEmoji(canvas, "🩷", it.x, it.y, faceW * 0.2f)
                    }
                }

                Filter.HEART_EYES -> {
                    // לבבות על העיניים
                    leftEye?.let {
                        drawEmoji(canvas, "❤️", it.x, it.y + faceW * 0.05f, faceW * 0.28f)
                    }
                    rightEye?.let {
                        drawEmoji(canvas, "❤️", it.x, it.y + faceW * 0.05f, faceW * 0.28f)
                    }
                    // אם מחייך - פה מחייך
                    if (smile > 0.5f) {
                        mouthBottom?.let {
                            drawEmoji(canvas, "😊", it.x, it.y + faceW * 0.15f, faceW * 0.3f)
                        }
                    }
                }

                Filter.BEARD -> {
                    // זקן מתחת לפה
                    mouthBottom?.let {
                        drawEmoji(canvas, "🧔", it.x, it.y + faceW * 0.25f, faceW * 0.55f)
                    }
                    // שפם
                    noseBase?.let {
                        drawEmoji(canvas, "👨", it.x, it.y + faceW * 0.1f, faceW * 0.4f)
                    }
                }
            }
        }
    }
}
