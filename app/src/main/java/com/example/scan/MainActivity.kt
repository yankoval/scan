package com.example.scan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PointF
import android.hardware.camera2.CameraCharacteristics
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.scan.databinding.ActivityMainBinding
import com.example.scan.model.ScannedCode
import io.objectbox.Box
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), BarcodeScannerProcessor.OnBarcodeScannedListener {

    private lateinit var viewBinding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private var barcodeScannerProcessor: BarcodeScannerProcessor? = null
    private var cameraControl: CameraControl? = null
    private lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        cameraExecutor = Executors.newSingleThreadExecutor()
        settingsManager = SettingsManager(this)

        if (allPermissionsGranted()) {
            viewBinding.previewView.post {
                startCamera()
            }
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        viewBinding.shareButton.setOnClickListener {
            exportCodesToCsv()
        }
    }

    private fun exportCodesToCsv() {
        val box: Box<ScannedCode> = (application as MainApplication).boxStore.boxFor(ScannedCode::class.java)
        val codes = box.all
        val csvBuilder = StringBuilder()
        csvBuilder.append("code,timestamp\n")
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        for (scannedCode in codes) {
            val formattedDate = sdf.format(Date(scannedCode.timestamp))
            csvBuilder.append("\"${scannedCode.code}\",\"$formattedDate\"\n")
        }

        try {
            val file = File(cacheDir, "scanned_codes.csv")
            FileOutputStream(file).use {
                it.write(csvBuilder.toString().toByteArray())
            }
            val contentUri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.provider", file)
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, contentUri)
                type = "text/csv"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share CSV"))
            box.removeAll()
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing CSV", e)
            Toast.makeText(this, "Error sharing CSV", Toast.LENGTH_SHORT).show()
        }
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

            val boxStore = (application as MainApplication).boxStore
            barcodeScannerProcessor = BarcodeScannerProcessor(viewBinding.graphicOverlay, this, this, boxStore)
            imageAnalyzer.also {
                it.setAnalyzer(cameraExecutor) { imageProxy ->
                    barcodeScannerProcessor?.processImageProxy(imageProxy)
                }
            }

            val cameraSelector = if (settingsManager.getDefaultCamera() == "telephoto") {
                getTelephotoCameraSelector(cameraProvider)
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

            try {
                cameraProvider.unbindAll()

                val camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )

                this.cameraControl = camera.cameraControl
                this.cameraControl?.setZoomRatio(1.0f)
                setupTapToFocus(this.cameraControl!!)

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    @androidx.camera.camera2.interop.ExperimentalCamera2Interop
    private fun getTelephotoCameraSelector(cameraProvider: ProcessCameraProvider): CameraSelector {
        val backCameras = cameraProvider.availableCameraInfos.filter {
            val camera2Info = Camera2CameraInfo.from(it)
            camera2Info.getCameraCharacteristic(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }

        if (backCameras.size <= 1) {
            return CameraSelector.DEFAULT_BACK_CAMERA
        }

        val telephotoCameraInfo = backCameras.maxByOrNull { cameraInfo ->
            val camera2Info = Camera2CameraInfo.from(cameraInfo)
            val focalLengths = camera2Info.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            focalLengths?.maxOrNull() ?: 0f
        }

        return if (telephotoCameraInfo != null) {
            CameraSelector.Builder().addCameraFilter { cameraInfoList ->
                cameraInfoList.filter {
                    Camera2CameraInfo.from(it).cameraId == Camera2CameraInfo.from(telephotoCameraInfo).cameraId
                }
            }.build()
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
    }

    override fun onFocusRequired(point: PointF, imageWidth: Int, imageHeight: Int) {
        if (viewBinding.previewView.width == 0 || viewBinding.previewView.height == 0) {
            return
        }
        runOnUiThread {
            // The translatePoint is now stateless and doesn't need rotation.
            // But we still need to provide the image dimensions.
            // The logic in GraphicOverlay is now simplified, let's call the simplified translatePoint
             val translatedPoint = viewBinding.graphicOverlay.translatePoint(point)
            val factory = viewBinding.previewView.meteringPointFactory
            val meteringPoint = factory.createPoint(translatedPoint.x, translatedPoint.y)
            val action = FocusMeteringAction.Builder(meteringPoint).build()
            cameraControl?.startFocusAndMetering(action)
            Log.d(TAG, "Auto-focus triggered at translated point: $translatedPoint")
        }
    }

    override fun onBarcodeCountUpdated(totalCount: Long) {
        runOnUiThread {
            viewBinding.barcodeCountText.text = getString(R.string.barcode_count, totalCount)
        }
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
        private const val TAG = "CameraX-MLKit"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.CAMERA
            ).toTypedArray()
    }
}
