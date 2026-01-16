package com.example.scan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PointF
import android.hardware.camera2.CameraCharacteristics
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import android.hardware.camera2.CaptureRequest
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.scan.databinding.ActivityMainBinding
import com.example.scan.model.*
import android.view.View
import com.example.scan.task.AggregationTaskProcessor
import com.example.scan.task.ITaskProcessor
import io.objectbox.Box
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity(), BarcodeScannerProcessor.OnBarcodeScannedListener {

    private lateinit var viewBinding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private var barcodeScannerProcessor: BarcodeScannerProcessor? = null
    private var cameraControl: CameraControl? = null
    private lateinit var settingsManager: SettingsManager
    internal var currentTask: Task? = null
    private lateinit var taskBox: Box<TaskEntity>
    private val json = Json { ignoreUnknownKeys = true }
    var taskProcessor: ITaskProcessor? = null
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        cameraExecutor = Executors.newSingleThreadExecutor()
        settingsManager = SettingsManager(this)
        taskBox = (application as MainApplication).boxStore.boxFor(TaskEntity::class.java)

        loadTask()

        if (allPermissionsGranted()) {
            viewBinding.previewView.post {
                startCamera()
            }
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        viewBinding.closeTaskButton.setOnClickListener {
            showCloseTaskDialog()
        }

        viewBinding.afCheckbox.setOnCheckedChangeListener { _, _ ->
            updateCameraFocusMode()
        }

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let { uri ->
                try {
                    val jsonContent = readTextFromUri(uri)
                    processJsonContent(jsonContent)
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing file URI", e)
                    Toast.makeText(this, "Error processing file", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun readTextFromUri(uri: Uri): String {
        contentResolver.openInputStream(uri)?.use { inputStream ->
            val bufferedInputStream = java.io.BufferedInputStream(inputStream)
            bufferedInputStream.mark(3)
            val bom = ByteArray(3)
            // Try to read the first 3 bytes
            val bytesRead = bufferedInputStream.read(bom, 0, 3)

            // Check if the read bytes form a UTF-8 BOM.
            // If not, reset the stream to the beginning.
            if (bytesRead < 3 || !(bom[0] == 0xEF.toByte() && bom[1] == 0xBB.toByte() && bom[2] == 0xBF.toByte())) {
                bufferedInputStream.reset()
            }
            // Now, the stream is positioned correctly (after BOM or at the start).
            // Read the rest of the stream as a UTF-8 string.
            return bufferedInputStream.reader(Charsets.UTF_8).readText()
        }
        return ""
    }
    private fun loadTask() {
        val taskEntity = taskBox.get(TASK_ENTITY_ID)
        if (taskEntity != null) {
            try {
                currentTask = json.decodeFromString<Task>(taskEntity.json)
                taskProcessor = AggregationTaskProcessor((application as MainApplication).boxStore)
                updateUiForTaskMode()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse stored task JSON", e)
                taskBox.remove(TASK_ENTITY_ID)
                currentTask = null
                taskProcessor = null
            }
        } else {
            currentTask = null
            taskProcessor = null
        }
    }


    private fun processJsonContent(jsonContent: String) {
        var isFileProcessed = false
        // First, try to parse as a Task file, as it's more specific
        try {
            val task = json.decodeFromString<Task>(jsonContent)
            Log.d(TAG, "Parsed task: $task")

            if (currentTask != null) {
                Toast.makeText(this, "Another task is already active. Close it first.", Toast.LENGTH_LONG).show()
                return // Reject the new task
            }

            currentTask = task
            taskProcessor = AggregationTaskProcessor((application as MainApplication).boxStore)
            val taskEntity = TaskEntity(json = jsonContent)
            taskBox.put(taskEntity) // This will overwrite the existing task with ID 1
            Toast.makeText(this, "Task loaded: ${task.id}", Toast.LENGTH_SHORT).show()
            showSuccessFeedback()
            isFileProcessed = true
        } catch (e: Exception) {
            // It's not a task file, or it's invalid. Log the full error for debugging.
            Log.e(TAG, "Could not parse JSON as a Task", e)
        }

        // If not processed as a task, try to parse as a settings file
        if (!isFileProcessed) {
            try {
                // A simple check for a key field before attempting full deserialization
                if (jsonContent.contains("serviceUrl")) {
                    settingsManager.updateSettingsFromJson(jsonContent)
                    Toast.makeText(this, "Settings updated", Toast.LENGTH_SHORT).show()
                    showSuccessFeedback()
                    isFileProcessed = true
                }
            } catch (e: Exception) {
                // Not a settings file either.
                Log.e(TAG, "Could not parse JSON as Settings", e)
            }
        }

        // If the file was processed, update the UI. Otherwise, show an error and do nothing to the UI.
        if (isFileProcessed) {
            updateUiForTaskMode()
        } else {
            Log.e(TAG, "Failed to parse JSON content as Task or Settings")
            Toast.makeText(this, "Invalid file format", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSuccessFeedback() {
        // Show green border
        viewBinding.successFeedbackBorder.visibility = View.VISIBLE
        Handler(Looper.getMainLooper()).postDelayed({
            viewBinding.successFeedbackBorder.visibility = View.GONE
        }, 1000)

        // Play sound
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200) // Using a similar tone to A
            toneGen.release()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play success tone", e)
        }
    }

    private fun showErrorFeedback() {
        // Show red border
        viewBinding.errorFeedbackBorder.visibility = View.VISIBLE
        Handler(Looper.getMainLooper()).postDelayed({
            viewBinding.errorFeedbackBorder.visibility = View.GONE
        }, 1000)

        // Play sound
        try {
            // Using a different tone for error, e.g., a short beep
            val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            toneGen.startTone(ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE, 500) // Longer tone for G note
            toneGen.release()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play error tone", e)
        }
    }


    private fun updateUiForTaskMode() {
        val inTaskMode = currentTask != null
        viewBinding.taskModeIndicator.visibility = if (inTaskMode) View.VISIBLE else View.GONE
        viewBinding.closeTaskButton.visibility = if (inTaskMode) View.VISIBLE else View.GONE
        viewBinding.taskInfoLayout.visibility = if (inTaskMode) View.VISIBLE else View.GONE

        if (inTaskMode) {
            currentTask?.let {
                viewBinding.gtinText.text = "GTIN: ${it.gtin ?: "N/A"}"
                viewBinding.lotNoText.text = "Lot: ${it.lotNo ?: "N/A"}"
                viewBinding.expDateText.text = "Exp: ${it.expDate ?: "N/A"}"
            }
            updateAggregateCount()
            viewBinding.aggregateCountText.visibility = View.VISIBLE
        } else {
            viewBinding.aggregateCountText.visibility = View.GONE
        }
    }

    fun updateAggregateCount() {
        if (!isFinishing && !isDestroyed) {
            val aggregatePackageBox: Box<AggregatePackage> = (application as MainApplication).boxStore.boxFor(AggregatePackage::class.java)
            val count = aggregatePackageBox.count()
            runOnUiThread {
                viewBinding.aggregateCountText.text = "Aggregates: $count"
            }
        }
    }

    private fun showCloseTaskDialog() {
        AlertDialog.Builder(this)
            .setTitle("Close Task")
            .setMessage("Are you sure you want to close the current task? This will generate the final report.")
            .setPositiveButton("Close & Export") { _, _ ->
                closeTask()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun closeTask() {
        generateAndExportAggregationReport()
        clearTaskData()
    }

    private fun clearTaskData() {
        val aggregatePackageBox: Box<AggregatePackage> = (application as MainApplication).boxStore.boxFor(AggregatePackage::class.java)
        val aggregatedCodeBox: Box<AggregatedCode> = (application as MainApplication).boxStore.boxFor(AggregatedCode::class.java)

        aggregatePackageBox.removeAll()
        aggregatedCodeBox.removeAll()
        taskBox.remove(TASK_ENTITY_ID)

        currentTask = null
        taskProcessor = null
        Toast.makeText(this, "Task closed and data cleared", Toast.LENGTH_SHORT).show()
        updateUiForTaskMode()
    }

    private fun generateAndExportAggregationReport() {
        val aggregatePackageBox: Box<AggregatePackage> = (application as MainApplication).boxStore.boxFor(AggregatePackage::class.java)
        val allPackages = aggregatePackageBox.all

        if (currentTask == null) {
            Log.e(TAG, "Cannot generate report without an active task.")
            return
        }

        val readyBoxes = allPackages.mapIndexed { index, pkg ->
            val productCodes = pkg.codes.map { it.fullCode }
            ReadyBox(
                Number = index,
                boxNumber = pkg.sscc,
                boxTime = formatInstant(pkg.timestamp),
                productNumbersFull = productCodes
            )
        }

        val report = AggregationReport(
            id = currentTask!!.id,
            startTime = currentTask!!.startTime,
            endTime = formatInstant(Instant.now().toEpochMilli()),
            readyBox = readyBoxes
        )

        val jsonString = Json.encodeToString(report)
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "aggregation_report_$timeStamp.json"
        shareFile(jsonString, fileName, "application/json", "Export Aggregation Report")
    }

    private fun formatInstant(timestamp: Long): String {
        val instant = Instant.ofEpochMilli(timestamp)
        val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault())
        return formatter.format(instant)
    }



    private fun shareFile(content: String, fileName: String, mimeType: String, chooserTitle: String) {
        try {
            val file = File(cacheDir, fileName)
            FileOutputStream(file).use {
                it.write(content.toByteArray())
            }
            val contentUri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.provider", file)
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, contentUri)
                type = mimeType
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, chooserTitle))
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing file", e)
            Toast.makeText(this, "Error sharing file", Toast.LENGTH_SHORT).show()
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
                    viewBinding.graphicOverlay.setCameraInfo(imageProxy.width, imageProxy.height, CameraSelector.LENS_FACING_BACK)
                    barcodeScannerProcessor?.processImageProxy(imageProxy, currentTask)
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
                updateCameraFocusMode()
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

    @androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
    private fun updateCameraFocusMode() {
        val control = cameraControl ?: return
        val camera2Control = Camera2CameraControl.from(control)

        val afMode = if (viewBinding.afCheckbox.isChecked) {
            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
        } else {
            CaptureRequest.CONTROL_AF_MODE_OFF
        }

        val options = CaptureRequestOptions.Builder()
            .setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, afMode)
            .build()

        camera2Control.setCaptureRequestOptions(options)

        if (!viewBinding.afCheckbox.isChecked) {
            control.cancelFocusAndMetering()
        }
    }

    override fun onFocusRequired(point: PointF, imageWidth: Int, imageHeight: Int) {
        if (!viewBinding.afCheckbox.isChecked) return
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

    override fun onCheckSucceeded() {
        runOnUiThread {
            updateAggregateCount()
            showSuccessFeedback()
        }
    }

    override fun onCheckFailed(reason: String) {
        runOnUiThread {
            showErrorFeedback()
        }
    }


    private fun setupTapToFocus(cameraControl: CameraControl) {
        viewBinding.previewView.setOnTouchListener { _, event ->
            if (viewBinding.afCheckbox.isChecked && event.action == MotionEvent.ACTION_DOWN) {
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
        // Perform cleanup in the background to avoid blocking the main thread.
        cameraExecutor.execute {
            barcodeScannerProcessor?.close()
        }
    }

    companion object {
        private const val TAG = "CameraX-MLKit"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private const val TASK_ENTITY_ID = 1L
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.CAMERA
            ).toTypedArray()
    }
}
