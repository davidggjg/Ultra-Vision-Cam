package com.example.crazycamera

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.provider.MediaStore
import android.view.GestureDetector
import android.view.MotionEvent
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
    private var flashEnabled = false
    private var currentMode = "תמונה"
    private var currentZoom = 0.1f

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
                val point = previewView.meteringPointFactory
                    .createPoint(event.x, event.y)
                camera?.cameraControl?.startFocusAndMetering(
                    FocusMeteringAction.Builder(point).build())
            }
            true
        }

        if (allPermissionsGranted()) startCamera()
        else ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, 10)

        btnCapture.setOnClickListener {
            if (currentMode == "וידאו") toggleVideo() else takePhoto()
        }
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
            startActivity(Intent(Intent.ACTION_VIEW).apply { type = "image/*" })
        }
    }

    private fun showMoreModes() {
        val modes = arrayOf("לילה 🌙", "פנורמה 🌅", "מזון 🍔",
            "הילוך איטי 🐢", "טיים-לאפס ⏩", "מקצועי 📷",
            "פורטרט 👤", "HDR 🌈", "הקלטה כפולה 📹")
        android.app.AlertDialog.Builder(this)
            .setTitle("בחר מצב")
            .setItems(modes) { _, which ->
                Toast.makeText(this, "מצב: ${modes[which]}", Toast.LENGTH_SHORT).show()
            }.show()
    }

    private fun switchMode(mode: String) {
        currentMode = mode
        listOf(modeFocus, modePhoto, modeVideo, modeMore).forEach {
            it.setTextColor(Color.parseColor("#AAAAAA"))
            it.textSize = 13f
        }
        when (mode) {
            "דיוק" -> { modeFocus.setTextColor(Color.WHITE); modeFocus.textSize = 14f }
            "תמונה" -> {
                modePhoto.setTextColor(Color.WHITE); modePhoto.textSize = 14f
                btnCapture.setBackgroundResource(R.drawable.circle_white)
            }
            "וידאו" -> {
                modeVideo.setTextColor(Color.WHITE); modeVideo.textSize = 14f
                btnCapture.setBackgroundColor(Color.RED)
            }
            else -> { modeMore.setTextColor(Color.WHITE); modeMore.textSize = 14f }
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

        // MINIMIZE_LATENCY = מצלמה מהירה יותר
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

            // שחזור פנס אחרי החלפת מצלמה
            if (flashEnabled) {
                if (camera?.cameraInfo?.hasFlashUnit() == true) {
                    camera?.cameraControl?.enableTorch(true)
                } else {
                    flashEnabled = false
                    runOnUiThread {
                        btnFlash.setImageResource(android.R.drawable.btn_star_big_off)
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "שגיאה: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun takePhoto() {
        val ic = imageCapture ?: return
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
                }
                override fun onError(e: ImageCaptureException) {
                    Toast.makeText(baseContext, "שגיאה בצילום", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun toggleVideo() {
        if (isRecording) {
            recording?.stop()
            isRecording = false
            btnCapture.setBackgroundResource(R.drawable.circle_white)
            Toast.makeText(this, "⏹ נשמר!", Toast.LENGTH_SHORT).show()
        } else {
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
            btnCapture.setBackgroundColor(Color.RED)
            Toast.makeText(this, "🔴 מקליט...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun flipCamera() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
            CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        bindCamera()
    }

    private fun toggleFlash() {
        if (camera?.cameraInfo?.hasFlashUnit() != true) {
            Toast.makeText(this, "אין פנס במצלמה זו", Toast.LENGTH_SHORT).show()
            return
        }
        flashEnabled = !flashEnabled
        camera?.cameraControl?.enableTorch(flashEnabled)
        btnFlash.setImageResource(
            if (flashEnabled) android.R.drawable.btn_star_big_on
            else android.R.drawable.btn_star_big_off)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<String>,
        results: IntArray) {
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
