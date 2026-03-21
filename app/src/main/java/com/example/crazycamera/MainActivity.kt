package com.example.crazycamera

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.Executors
import android.view.ScaleGestureDetector
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var btnCapture: ImageButton
    private lateinit var btnFlip: ImageButton
    private lateinit var btnFlash: ImageButton
    private lateinit var btnGallery: ImageButton
    private lateinit var btnRecord: ImageButton
    private lateinit var btnPause: ImageButton
    private lateinit var btnStop: ImageButton
    private lateinit var btnFlipVideo: ImageButton
    private lateinit var bottomBar: LinearLayout
    private lateinit var videoBar: LinearLayout
    private lateinit var flashOverlay: View
    private lateinit var recordingTimer: TextView
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
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var recording: Recording? = null
    private var cameraExecutor = Executors.newSingleThreadExecutor()
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var isRecording = false
    private var isPaused = false
    private var flashEnabled = false
    private var currentMode = "תמונה"
    private var currentZoom = 0.1f

    private val handler = Handler(Looper.getMainLooper())
    private var timerSeconds = 0
    private var timerRunnable: Runnable? = null

    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private lateinit var gestureDetector: GestureDetector
    private lateinit var faceDetector: FaceDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        supportActionBar?.hide()
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN)

        previewView = findViewById(R.id.previewView)
        btnCapture = findViewById(R.id.btnCapture)
        btnFlip = findViewById(R.id.btnFlip)
        btnFlash = findViewById(R.id.btnFlash)
        btnGallery = findViewById(R.id.btnGallery)
        btnRecord = findViewById(R.id.btnRecord)
        btnPause = findViewById(R.id.btnPause)
        btnStop = findViewById(R.id.btnStop)
        btnFlipVideo = findViewById(R.id.btnFlipVideo)
        bottomBar = findViewById(R.id.bottomBar)
        videoBar = findViewById(R.id.videoBar)
        flashOverlay = findViewById(R.id.flashOverlay)
        recordingTimer = findViewById(R.id.recordingTimer)
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

        faceDetector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .build()
        )

        scaleGestureDetector = ScaleGestureDetector(this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val zoom = camera?.cameraInfo?.zoomState?.value
                    val newZoom = (zoom?.zoomRatio ?: 1f) * detector.scaleFactor
                    camera?.cameraControl?.setZoomRatio(
                        newZoom.coerceIn(zoom?.minZoomRatio ?: 1f, zoom?.maxZoomRatio ?: 1f))
                    return true
                }
            })

        gestureDetector = GestureDetector(this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onFling(e1: MotionEvent?, e2: MotionEvent,
                    vx: Float, vy: Float): Boolean {
                    val dy = (e2.y) - (e1?.y ?: 0f)
                    val dx = (e2.x) - (e1?.x ?: 0f)
                    if (abs(dy) > abs(dx) && abs(dy) > 150) {
                        flipCamera()
                        return true
                    }
                    return false
                }
            })

        previewView.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_UP &&
                !scaleGestureDetector.isInProgress) {
                val point = previewView.meteringPointFactory.createPoint(event.x, event.y)
                camera?.cameraControl?.startFocusAndMetering(
                    FocusMeteringAction.Builder(point).build())
            }
            true
        }

        if (allPermissionsGranted()) startCamera()
        else ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, 10)

        // כפתורי תמונה
        btnCapture.setOnClickListener { takePhoto() }
        btnFlip.setOnClickListener { flipCamera() }
        btnFlash.setOnClickListener { toggleFlash() }

        // כפתורי וידאו
        btnRecord.setOnClickListener { startRecording() }
        btnPause.setOnClickListener { pauseRecording() }
        btnStop.setOnClickListener { stopRecording() }
        btnFlipVideo.setOnClickListener { flipCamera() }

        // הגדרות
        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener { showSettings() }

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
            try {
                val intent = Intent(Intent.ACTION_PICK,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "שגיאה בפתיחת גלריה", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showSettings() {
        val items = arrayOf(
            "🖼️ יחס תמונה: 4:3",
            "🖼️ יחס תמונה: 16:9",
            "⏱️ טיימר: כבוי",
            "⏱️ טיימר: 3 שניות",
            "⏱️ טיימר: 10 שניות",
            "📊 רשת קומפוזיציה: כן/לא",
            "🔊 צליל צילום: כן/לא"
        )
        android.app.AlertDialog.Builder(this)
            .setTitle("⚙️ הגדרות מצלמה")
            .setItems(items) { _, which ->
                Toast.makeText(this, items[which], Toast.LENGTH_SHORT).show()
            }.show()
    }

    private fun showMoreModes() {
        val modes = arrayOf(
            "לילה 🌙",
            "פנורמה 🌅",
            "מזון 🍔",
            "הילוך איטי 🐢",
            "טיים-לאפס ⏩",
            "מקצועי 📷",
            "פורטרט 👤",
            "HDR 🌈"
        )
        val videoModes = setOf("הילוך איטי 🐢", "טיים-לאפס ⏩")

        android.app.AlertDialog.Builder(this)
            .setTitle("בחר מצב")
            .setItems(modes) { _, which ->
                val selected = modes[which]
                if (selected in videoModes) {
                    // מצבי וידאו - עבור לוידאו אוטומטית
                    switchMode("וידאו")
                    modeVideo.setTextColor(Color.WHITE)
                    Toast.makeText(this, "מצב: $selected", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "מצב: $selected", Toast.LENGTH_SHORT).show()
                }
            }.show()
    }

    private fun switchMode(mode: String) {
        currentMode = mode
        listOf(modeFocus, modePhoto, modeVideo, modeMore).forEach {
            it.setTextColor(Color.parseColor("#AAAAAA"))
            it.textSize = 13f
        }
        when (mode) {
            "דיוק" -> {
                modeFocus.setTextColor(Color.WHITE); modeFocus.textSize = 14f
                bottomBar.visibility = View.VISIBLE
                videoBar.visibility = View.GONE
            }
            "תמונה" -> {
                modePhoto.setTextColor(Color.WHITE); modePhoto.textSize = 14f
                bottomBar.visibility = View.VISIBLE
                videoBar.visibility = View.GONE
            }
            "וידאו" -> {
                modeVideo.setTextColor(Color.WHITE); modeVideo.textSize = 14f
                bottomBar.visibility = View.GONE
                videoBar.visibility = View.VISIBLE
                // כפתור הקלטה אדום
                btnRecord.setBackgroundColor(Color.RED)
            }
            else -> {
                modeMore.setTextColor(Color.WHITE); modeMore.textSize = 14f
                bottomBar.visibility = View.VISIBLE
                videoBar.visibility = View.GONE
            }
        }
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
        ProcessCameraProvider.getInstance(this).also { future ->
            future.addListener({
                cameraProvider = future.get()
                bindCamera()
            }, ContextCompat.getMainExecutor(this))
        }
    }

    private fun bindCamera() {
        val cp = cameraProvider ?: return
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setJpegQuality(95)
            .build()
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
            .build()
        videoCapture = VideoCapture.withOutput(recorder)
        imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        imageAnalysis!!.setAnalyzer(cameraExecutor) { proxy ->
            val img = proxy.image
            if (img != null) {
                faceDetector.process(InputImage.fromMediaImage(img,
                    proxy.imageInfo.rotationDegrees))
                    .addOnSuccessListener { faces ->
                        overlayView.setFaces(faces, proxy.width, proxy.height,
                            lensFacing == CameraSelector.LENS_FACING_FRONT)
                    }
                    .addOnCompleteListener { proxy.close() }
            } else proxy.close()
        }
        try {
            cp.unbindAll()
            camera = cp.bindToLifecycle(this,
                CameraSelector.Builder().requireLensFacing(lensFacing).build(),
                preview, imageCapture, videoCapture, imageAnalysis)
            camera?.cameraControl?.setLinearZoom(currentZoom)
            if (flashEnabled) camera?.cameraControl?.enableTorch(true)
        } catch (e: Exception) {
            Toast.makeText(this, "שגיאה: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun takePhoto() {
        val ic = imageCapture ?: return
        // אנימציית פלאש
        flashOverlay.visibility = View.VISIBLE
        handler.postDelayed({ flashOverlay.visibility = View.GONE }, 150)

        val name = "UltraVision_${System.currentTimeMillis()}"
        val cv = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/UltraVision")
        }
        ic.takePicture(
            ImageCapture.OutputFileOptions.Builder(contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv).build(),
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(o: ImageCapture.OutputFileResults) {
                    Toast.makeText(baseContext, "📸 נשמר!", Toast.LENGTH_SHORT).show()
                    // עדכן תמונה אחרונה בגלריה
                    updateGalleryThumbnail()
                }
                override fun onError(e: ImageCaptureException) {
                    Toast.makeText(baseContext, "שגיאה בצילום", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun updateGalleryThumbnail() {
        val cursor = contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            null, null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )
        cursor?.use {
            if (it.moveToFirst()) {
                val id = it.getLong(0)
                val uri = android.net.Uri.withAppendedPath(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                runOnUiThread {
                    btnGallery.setImageURI(uri)
                }
            }
        }
    }

    private fun startRecording() {
        val name = "UltraVision_${System.currentTimeMillis()}"
        val cv = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/UltraVision")
        }
        recording = videoCapture?.output?.prepareRecording(this,
            MediaStoreOutputOptions.Builder(contentResolver,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                .setContentValues(cv).build())
            ?.apply {
                if (ContextCompat.checkSelfPermission(this@MainActivity,
                        Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED)
                    withAudioEnabled()
            }?.start(ContextCompat.getMainExecutor(this)) { }
        isRecording = true
        btnRecord.setBackgroundColor(Color.parseColor("#FF4444"))
        // הפעל טיימר
        timerSeconds = 0
        recordingTimer.visibility = View.VISIBLE
        startTimer()
        Toast.makeText(this, "🔴 מקליט...", Toast.LENGTH_SHORT).show()
    }

    private fun pauseRecording() {
        if (!isRecording) return
        if (!isPaused) {
            recording?.pause()
            isPaused = true
            timerRunnable?.let { handler.removeCallbacks(it) }
            btnPause.setImageResource(android.R.drawable.ic_media_play)
        } else {
            recording?.resume()
            isPaused = false
            startTimer()
            btnPause.setImageResource(android.R.drawable.ic_media_pause)
        }
    }

    private fun stopRecording() {
        recording?.stop()
        isRecording = false
        isPaused = false
        timerRunnable?.let { handler.removeCallbacks(it) }
        recordingTimer.visibility = View.GONE
        btnRecord.setBackgroundColor(Color.RED)
        btnPause.setImageResource(android.R.drawable.ic_media_pause)
        Toast.makeText(this, "⏹ נשמר!", Toast.LENGTH_SHORT).show()
    }

    private fun startTimer() {
        timerRunnable = object : Runnable {
            override fun run() {
                timerSeconds++
                val h = timerSeconds / 3600
                val m = (timerSeconds % 3600) / 60
                val s = timerSeconds % 60
                recordingTimer.text = String.format("%02d:%02d:%02d", h, m, s)
                handler.postDelayed(this, 1000)
            }
        }
        handler.postDelayed(timerRunnable!!, 1000)
    }

    private fun flipCamera() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
            CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        bindCamera()
    }

    private fun toggleFlash() {
        flashEnabled = !flashEnabled
        camera?.cameraControl?.enableTorch(flashEnabled)
            ?.addListener({
                runOnUiThread {
                    btnFlash.setImageResource(
                        if (flashEnabled) android.R.drawable.ic_menu_view
                        else android.R.drawable.ic_menu_camera)
                }
            }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) ==
            PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<String>,
        results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (code == 10 && allPermissionsGranted()) startCamera()
        else Toast.makeText(this, "נדרשות הרשאות", Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        camera?.cameraControl?.enableTorch(false)
        timerRunnable?.let { handler.removeCallbacks(it) }
        cameraExecutor.shutdown()
    }

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    }
}
