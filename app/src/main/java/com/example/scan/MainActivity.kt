package com.example.scan

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PointF
import android.os.Bundle
import android.util.Size
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.scan.databinding.ActivityMainBinding
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), BarcodeScannerProcessor.OnFocusRequiredListener {

    private lateinit var viewBinding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private var barcodeScannerProcessor: BarcodeScannerProcessor? = null
    private var cameraControl: CameraControl? = null

    private val exportLogsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    val logFile = getLogFile()
                    if (logFile.exists()) {
                        contentResolver.openOutputStream(uri)?.use { outputStream ->
                            logFile.inputStream().use { inputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                        Toast.makeText(this, "Logs exported successfully.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Log file not found.", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error exporting logs")
                    Toast.makeText(this, "Error exporting logs.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setupFileLogging()
        cameraExecutor = Executors.newSingleThreadExecutor()

        if (allPermissionsGranted()) {
            viewBinding.previewView.post { startCamera() }
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        viewBinding.exportLogsButton.setOnClickListener {
            exportLogs()
        }
    }

    private fun exportLogs() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, "scan_debug.log")
        }
        exportLogsLauncher.launch(intent)
    }

    private fun getLogFile(): File {
        val documentsDir = getExternalFilesDir("Documents")
        val logDir = File(documentsDir, "scan")
        return File(logDir, "debug.log")
    }

    private fun setupFileLogging() {
        val settingsManager = SettingsManager(this)
        Timber.plant(FileLoggingTree(this, settingsManager))
        Timber.i("File logging initialized.")
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .setTargetResolution(Size(3840, 2160))
                .build()
                .also {
                    it.setSurfaceProvider(viewBinding.previewView.surfaceProvider)
                }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(Size(3840, 2160))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            barcodeScannerProcessor = BarcodeScannerProcessor(viewBinding.graphicOverlay, this, this)
            imageAnalyzer.also {
                it.setAnalyzer(cameraExecutor) { imageProxy ->
                    barcodeScannerProcessor?.processImageProxy(imageProxy)
                }
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()

                val camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )

                this.cameraControl = camera.cameraControl
                setupTapToFocus(this.cameraControl!!)
                Timber.d("MainActivity, startCamera, Camera started successfully.")

            } catch (exc: Exception) {
                Timber.e(exc, "MainActivity, startCamera, Use case binding failed")
            }

        }, ContextCompat.getMainExecutor(this))
    }

    override fun onFocusRequired(point: PointF, imageWidth: Int, imageHeight: Int) {
        if (viewBinding.previewView.width == 0 || viewBinding.previewView.height == 0) {
            return
        }
        runOnUiThread {
            val translatedPoint = viewBinding.graphicOverlay.translatePoint(point)
            val factory = viewBinding.previewView.meteringPointFactory
            val meteringPoint = factory.createPoint(translatedPoint.x, translatedPoint.y)
            val action = FocusMeteringAction.Builder(meteringPoint).build()
            cameraControl?.startFocusAndMetering(action)
            Timber.d("MainActivity, onFocusRequired, Auto-focus triggered at point: $point")
        }
    }

    private fun setupTapToFocus(cameraControl: CameraControl) {
        viewBinding.previewView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val factory = viewBinding.previewView.meteringPointFactory
                val point = factory.createPoint(event.x, event.y)
                val action = FocusMeteringAction.Builder(point).build()
                cameraControl.startFocusAndMetering(action)
                Timber.d("MainActivity, setupTapToFocus, Manual focus triggered at point: ($point.x, $point.y)")
                return@setOnTouchListener true
            }
            return@setOnTouchListener false
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                viewBinding.previewView.post {
                    startCamera()
                }
            } else {
                Toast.makeText(
                    this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.CAMERA
            ).toTypedArray()
    }
}
