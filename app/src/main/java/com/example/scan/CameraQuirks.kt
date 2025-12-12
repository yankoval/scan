package com.example.scan

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.params.StreamConfigurationMap
import android.os.Build
import android.util.Size
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraInfo

object CameraQuirks {

    fun getSupportedResolutions(cameraInfo: CameraInfo): List<Size> {
        val camera2CameraInfo = Camera2CameraInfo.from(cameraInfo)
        val characteristics = camera2CameraInfo.getCameraCharacteristics(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

        val streamConfigurationMap: StreamConfigurationMap? = characteristics?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

        // The cast to Array<Size> is necessary for API levels before 23.
        @Suppress("USELESS_CAST")
        val outputSizes: Array<Size> = streamConfigurationMap?.getOutputSizes(android.graphics.ImageFormat.JPEG) as Array<Size>

        return outputSizes.toList()
    }
}
