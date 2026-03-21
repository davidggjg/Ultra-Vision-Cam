package com.example.crazycamera

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.extensions.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.provider.MediaStore
import android.graphics.*
import android.view.ScaleGestureDetector
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.*
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var btnCapture: ImageButton
    private lateinit var btnVideo: ImageButton
    private lateinit var btnFlip: ImageButton
    private lateinit var btnFlash: ImageButton
    private lateinit var btnGallery: ImageButton
    private lateinit var overlayView: FaceOverlayView
    private lateinit var zoomSeekBar: SeekBar

    private lateinit var modeFocus: TextView
    private lateinit var modePhoto: TextView
    private lateinit var modeVideo: TextView
    private lateinit var modeMore: TextView

    private lateinit var zoomPt6: TextView
    private lateinit var zoom1x: TextView
    private lateinit var zoom2x: TextView
    private lateinit var zoom3x: TextView
    private lateinit var zoom10x: TextView

    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var recording: Recording? = null
    private var cameraExecutor = Executors.newSingleThreadExecutor()
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var isRecording = false
    private var flashEnabled = false
    private var currentMode = "תמונה"
    private var currentZoom = 0.1f

    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private lateinit var faceDetector: FaceDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // הסתר סרגל עליון
        supportActionBar?.hide()
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN)

        previewView = findViewById(R.id.previewView)
        btnCapture = findViewById(R.id.btnCapture)
        btnVideo = findViewById(R.id.btnVideo)
        btnFlip = findViewById(R.id.btnFlip)
        btnFlash = findViewById(R.id.btnFlash)
        btnGallery = findViewById(R.id.btnGallery)
        overlayView = findViewById(R.id.overlayView)
        zoomSeekBar = findViewById(R.id.zoomSeekBar)

        modeFocus = findViewById(R.id.modeFocus)
        modePhoto = findViewById(R.id.modePhoto)
        modeVideo = findViewById(R.id.modeVideo)
        modeMore = findViewById(R.id.modeMore)

        zoomPt6 = findViewById(R.id.zoomPt6)
        zoom1x = findViewById(R.id.zoom1x)
        zoom2x = findViewById(R.id.zoom2x)
        zoom3x = findViewById(R.id.zoom3x)
        zoom10x = findViewById(R.id.zoom10x)

        // ML Kit זיהוי פנים
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()
        faceDetector = FaceDetection.getClient(options)

        // זום בתנועת אצבעות
        scaleGestureDetector = ScaleGestureDetector(this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val zoom = camera?.cameraInfo?.zoomState?.value
                    val currentZoomRatio = zoom?.zoomRatio ?: 1f
                    val newZoom = currentZoomRatio * detector.scaleFactor
                    camera?.cameraControl?.setZoomRatio(
                        newZoom.coerceIn(zoom?.minZoomRatio ?: 1f, zoom?.maxZoomRatio ?: 1f))
                    return true
                }
            })

        previewView.setOnTouchListener { view, event ->
            scaleGestureDetector.onTouchEvent(event)
            // התמקדות בלחיצה
            if (event.action == MotionEvent.ACTION_UP) {
                val factory = previewView.meteringPointFactory
                val point = factory.createPoint(event.x, event.y)
                val action = FocusMeteringAction.Builder(point).build()
                camera?.cameraControl?.startFocusAndMetering(action)
            }
            true
        }

        if (allPermissionsGranted()) startCamera()
        else ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, 10)

        btnCapture.setOnClickListener {
            if (currentMode == "וידאו") toggleVideo() else takePhoto()
        }
        btnVideo.setOnClickListener { toggleVideo() }
        btnFlip.setOnClickListener { flipCamera() }
        btnFlash.setOnClickListener { toggleFlash() }

        modeFocus.setOnClickListener { switchMode("דיוק") }
        modePhoto.setOnClickListener { switchMode("תמונה") }
        modeVideo.setOnClickListener { switchMode("וידאו") }
        modeMore.setOnClickListener { showMoreModes() }

        zoomPt6.setOnClickListener { setZoom(0.0f); highlightZoom(zoomPt6) }
        zoom1x.setOnClickListener { setZoom(0.1f); highlightZoom(zoom1x) }
        zoom2x.setOnClickListener { setZoom(0.3f); highlightZoom(zoom2x) }
        zoom3x.setOnClickListener { setZoom(0.5f); highlightZoom(zoom3x) }
        zoom10x.setOnClickListener { setZoom(0.9f); highlightZoom(zoom10x) }

        btnGallery.setOnClickListener {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
            intent.type = "image/*"
            startActivity(intent)
        }
    }

    private fun showMoreModes() {
        val modes = arrayOf("לילה", "פנורמה", "מזון", "הילוך איטי", "טיים-לאפס", "מקצועי")
        android.app.AlertDialog.Builder(this)
            .setTitle("מצבים נוספים")
            .setItems(modes) { _, which ->
                switchMode(modes[which])
                Toast.makeText(this, "מצב: ${modes[which]}", Toast.LENGTH_SHORT).show()
            }.show()
    }

    private fun switchMode(mode: String) {
        currentMode = mode
        listOf(modeFocus, modePhoto, modeVideo, modeMore).forEach {
            it.setTextColor(Color.parseColor("#AAAAAA"))
            it.textSize = 15f
        }
        when (mode) {
            "דיוק" -> { modeFocus.setTextColor(Color.WHITE); modeFocus.textSize = 16f }
            "תמונה" -> { modePhoto.setTextColor(Color.WHITE); modePhoto.textSize = 16f
                btnCapture.setBackgroundColor(Color.WHITE) }
            "וידאו" -> { modeVideo.setTextColor(Color.WHITE); modeVideo.textSize = 16f
                btnCapture.setBackgroundColor(Color.RED) }
            else -> { modeMore.setTextColor(Color.WHITE); modeMore.textSize = 16f }
        }
        startCamera()
    }

    private fun setZoom(zoom: Float) {
        currentZoom = zoom
        camera?.cameraControl?.setLinearZoom(zoom)
    }

    private fun highlightZoom(selected: TextView) {
        listOf(zoomPt6, zoom1x, zoom2x, zoom3x, zoom10x).forEach {
            it.setTextColor(Color.parseColor("#AAAAAA"))
            it.setBackgroundColor(Color.TRANSPARENT)
        }
        selected.setTextColor(Color.WHITE)
        selected.setBackgroundColor(Color.parseColor("#44FFFFFF"))
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setFlashMode(if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF)
                .build()

            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            // ניתוח פנים
            imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            imageAnalysis!!.setAnalyzer(cameraExecutor) { imageProxy ->
                val mediaImage = imageProxy.image
                if (mediaImage != null) {
                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                    faceDetector.process(image)
                        .addOnSuccessListener { faces ->
                            overlayView.setFaces(faces, imageProxy.width, imageProxy.height)
                        }
                        .addOnCompleteListener { imageProxy.close() }
                } else imageProxy.close()
            }

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing).build()

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, videoCapture, imageAnalysis)
                camera?.cameraControl?.setLinearZoom(currentZoom)
            } catch (e: Exception) {
                Toast.makeText(this, "שגיאה: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        val name = "UltraVision_${System.currentTimeMillis()}"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/UltraVision")
        }
        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues).build()

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Toast.makeText(baseContext, "📸 נשמר!", Toast.LENGTH_SHORT).show()
                }
                override fun onError(e: ImageCaptureException) {
                    Toast.makeText(baseContext, "שגיאה: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun toggleVideo() {
        if (isRecording) {
            recording?.stop()
            isRecording = false
            btnCapture.setBackgroundColor(Color.WHITE)
            Toast.makeText(this, "⏹ נשמר!", Toast.LENGTH_SHORT).show()
        } else {
            val name = "UltraVision_${System.currentTimeMillis()}"
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/UltraVision")
            }
            val output = MediaStoreOutputOptions.Builder(
                contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                .setContentValues(contentValues).build()

            recording = videoCapture?.output?.prepareRecording(this, output)
                ?.apply {
                    if (ContextCompat.checkSelfPermission(this@MainActivity,
                            Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                        withAudioEnabled()
                }?.start(ContextCompat.getMainExecutor(this)) { }

            isRecording = true
            btnCapture.setBackgroundColor(Color.RED)
            Toast.makeText(this, "🔴 מקליט...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun flipCamera() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
            CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        startCamera()
    }

    private fun toggleFlash() {
        flashEnabled = !flashEnabled
        camera?.cameraControl?.enableTorch(flashEnabled)
        btnFlash.alpha = if (flashEnabled) 1.0f else 0.5f
        Toast.makeText(this, if (flashEnabled) "💡 פנס פעיל" else "פנס כבוי", Toast.LENGTH_SHORT).show()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (code == 10 && allPermissionsGranted()) startCamera()
        else Toast.makeText(this, "נדרשות הרשאות", Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    }
}
