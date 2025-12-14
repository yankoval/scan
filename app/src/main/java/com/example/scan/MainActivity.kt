package com.example.scan

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.scan.databinding.ActivityMainBinding
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), BarcodeScannerProcessor.BarcodeCountListener {

    private lateinit var viewBinding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private var barcodeScannerProcessor: BarcodeScannerProcessor? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>

    private lateinit var settingsManager: SettingsManager
    private var resolutionManager: ResolutionManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        cameraExecutor = Executors.newSingleThreadExecutor()
        settingsManager = SettingsManager(this)

        if (allPermissionsGranted()) {
            setupCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
    }

    private fun setupCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            // Initialize ResolutionManager here after camera provider is ready
            initializeResolutionManager()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun initializeResolutionManager() {
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        try {
            val cameraInfo = cameraProvider?.availableCameraInfos?.find {
                CameraSelector.DEFAULT_BACK_CAMERA.filter(listOf(it)).isNotEmpty()
            }
            val supportedResolutions = CameraQuirks.getSupportedResolutions(cameraInfo!!)

            resolutionManager = ResolutionManager(supportedResolutions) { newResolution ->
                // This lambda is called when a new resolution is selected
                bindCameraUseCases(newResolution)
            }
            // Start the manager which will trigger the first resolution selection
            resolutionManager?.start(settingsManager.getScanTunePeriod())

        } catch (e: Exception) {
            Log.e(TAG, "Could not get supported resolutions", e)
            // Fallback to a default resolution if something goes wrong
            bindCameraUseCases(Size(1280, 720))
        }
    }

    private fun bindCameraUseCases(resolution: Size) {
        if (cameraProvider == null) {
            Log.e(TAG, "Camera provider not initialized.")
            return
        }

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        val preview = Preview.Builder()
            .setTargetResolution(resolution)
            .build()
            .also {
                it.setSurfaceProvider(viewBinding.previewView.surfaceProvider)
            }

        val imageAnalyzer = ImageAnalysis.Builder()
            .setTargetResolution(resolution)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        barcodeScannerProcessor = BarcodeScannerProcessor(viewBinding.graphicOverlay, this, this)
        imageAnalyzer.setAnalyzer(cameraExecutor) { imageProxy ->
            barcodeScannerProcessor?.processImageProxy(imageProxy)
        }

        try {
            cameraProvider?.unbindAll()
            val camera = cameraProvider?.bindToLifecycle(
                this, cameraSelector, preview, imageAnalyzer
            )
            setupTapToFocus(camera!!.cameraControl)
            viewBinding.graphicOverlay.setCameraInfo(resolution.width, resolution.height, cameraSelector.lensFacing!!)
            Log.d(TAG, "Camera use cases bound with resolution: $resolution")
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    override fun onBarcodeCount(count: Int) {
       resolutionManager?.onBarcodeCountUpdate(count)
    }

    private fun setupTapToFocus(cameraControl: CameraControl) {
        viewBinding.previewView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val factory = viewBinding.previewView.meteringPointFactory
                val point = factory.createPoint(event.x, event.y)
                val action = FocusMeteringAction.Builder(point).build()
                cameraControl.startFocusAndMetering(action)
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
                setupCamera()
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

    override fun onPause() {
        super.onPause()
        resolutionManager?.stop()
    }

    override fun onResume() {
        super.onResume()
        // Restart manager only if it has been initialized
        if (resolutionManager != null) {
             resolutionManager?.start(settingsManager.getScanTunePeriod())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        resolutionManager?.stop()
    }

    companion object {
        private const val TAG = "CameraX-MLKit"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.CAMERA
            ).toTypedArray()
    }
}
