package com.example.crazycamera

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.provider.MediaStore
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var btnCapture: ImageButton
    private lateinit var btnVideo: ImageButton
    private lateinit var btnFlip: ImageButton
    private lateinit var btnFlash: ImageButton
    private lateinit var btnGallery: ImageButton
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
    private var recording: Recording? = null
    private var cameraExecutor = Executors.newSingleThreadExecutor()
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var isRecording = false
    private var flashEnabled = false
    private var currentMode = "תמונה"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        btnCapture = findViewById(R.id.btnCapture)
        btnVideo = findViewById(R.id.btnVideo)
        btnFlip = findViewById(R.id.btnFlip)
        btnFlash = findViewById(R.id.btnFlash)
        btnGallery = findViewById(R.id.btnGallery)
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

        if (allPermissionsGranted()) startCamera()
        else ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, 10)

        btnCapture.setOnClickListener {
            if (currentMode == "וידאו") toggleVideo()
            else takePhoto()
        }

        btnVideo.setOnClickListener { toggleVideo() }
        btnFlip.setOnClickListener { flipCamera() }
        btnFlash.setOnClickListener { toggleFlash() }

        // מצבים
        modeFocus.setOnClickListener { switchMode("דיוק") }
        modePhoto.setOnClickListener { switchMode("תמונה") }
        modeVideo.setOnClickListener { switchMode("וידאו") }
        modeMore.setOnClickListener { switchMode("עוד") }

        // זום
        zoomPt6.setOnClickListener { setZoom(0.0f, ".6") }
        zoom1x.setOnClickListener { setZoom(0.1f, "1×") }
        zoom2x.setOnClickListener { setZoom(0.3f, "2") }
        zoom3x.setOnClickListener { setZoom(0.5f, "3") }
        zoom10x.setOnClickListener { setZoom(0.9f, "10") }

        // גלריה
        btnGallery.setOnClickListener {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
            intent.type = "image/*"
            startActivity(intent)
        }
    }

    private fun switchMode(mode: String) {
        currentMode = mode
        val allModes = listOf(modeFocus, modePhoto, modeVideo, modeMore)
        allModes.forEach { it.setTextColor(android.graphics.Color.parseColor("#AAAAAA")) }
        when (mode) {
            "דיוק" -> modeFocus.setTextColor(android.graphics.Color.WHITE)
            "תמונה" -> modePhoto.setTextColor(android.graphics.Color.WHITE)
            "וידאו" -> {
                modeVideo.setTextColor(android.graphics.Color.WHITE)
                btnCapture.setBackgroundColor(android.graphics.Color.RED)
            }
            "עוד" -> modeMore.setTextColor(android.graphics.Color.WHITE)
        }
        if (mode != "וידאו") btnCapture.setBackgroundColor(android.graphics.Color.WHITE)
    }

    private fun setZoom(zoom: Float, label: String) {
        camera?.cameraControl?.setLinearZoom(zoom)
        val allZooms = listOf(zoomPt6, zoom1x, zoom2x, zoom3x, zoom10x)
        allZooms.forEach {
            it.setTextColor(android.graphics.Color.parseColor("#AAAAAA"))
            it.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        val selected = allZooms.find { it.text == label }
        selected?.setTextColor(android.graphics.Color.WHITE)
        selected?.setBackgroundColor(android.graphics.Color.parseColor("#44FFFFFF"))
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
                .build()

            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing).build()

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, videoCapture)
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
                    Toast.makeText(baseContext, "📸 תמונה נשמרה לגלריה!", Toast.LENGTH_SHORT).show()
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
            btnVideo.setImageResource(android.R.drawable.ic_media_play)
            Toast.makeText(this, "⏹ וידאו נשמר לגלריה!", Toast.LENGTH_SHORT).show()
        } else {
            val name = "UltraVision_${System.currentTimeMillis()}"
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/UltraVision")
            }
            val mediaStoreOutput = MediaStoreOutputOptions.Builder(
                contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                .setContentValues(contentValues).build()

            recording = videoCapture?.output?.prepareRecording(this, mediaStoreOutput)
                ?.apply {
                    if (ContextCompat.checkSelfPermission(this@MainActivity,
                            Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                        withAudioEnabled()
                }?.start(ContextCompat.getMainExecutor(this)) { }

            isRecording = true
            btnVideo.setImageResource(android.R.drawable.ic_media_pause)
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
        Toast.makeText(this, if (flashEnabled) "פלאש פעיל" else "פלאש כבוי", Toast.LENGTH_SHORT).show()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (code == 10 && allPermissionsGranted()) startCamera()
        else Toast.makeText(this, "נדרשות הרשאות מצלמה", Toast.LENGTH_LONG).show()
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
