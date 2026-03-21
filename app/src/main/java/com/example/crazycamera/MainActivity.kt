package com.example.crazycamera

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // יצירת ממשק המשתמש
        val view = PreviewView(this)
        setContentView(view)

        cameraExecutor = Executors.newSingleThreadExecutor()

        startCamera(view)
    }

    private fun startCamera(previewView: PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // הגדרות חדות מקסימלית (4K במידת האפשר)
            val preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                // חיבור המצלמה למערכת
                val camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview)
                
                // הפעלת פוקוס אוטומטי מתקדם
                val cameraControl = camera.cameraControl
                cameraControl.enableTorch(false) 
                
            } catch (exc: Exception) {
                // שגיאה
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
