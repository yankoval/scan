package com.example.scan

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException

class SettingsManager(private val context: Context) {

    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val httpClient = HttpClient(CIO)
    private val json = Json { ignoreUnknownKeys = true }

    fun getServiceUrl(): String {
        return sharedPreferences.getString(KEY_SERVICE_URL, null) ?: loadSettingsFromAssets().serviceUrl
    }

    fun getDefaultCamera(): String {
        return sharedPreferences.getString(KEY_DEFAULT_CAMERA, null) ?: loadSettingsFromAssets().default_camera
    }

    fun getAggregateCodeFilterTemplate(): String {
        return sharedPreferences.getString(KEY_AGGREGATE_CODE_FILTER_TEMPLATE, null) ?: loadSettingsFromAssets().aggregateCodeFilterTemplate
    }

    fun getAggregatedCodeFilterTemplate(): String {
        return sharedPreferences.getString(KEY_AGGREGATED_CODE_FILTER_TEMPLATE, null) ?: loadSettingsFromAssets().aggregatedCodeFilterTemplate
    }

    fun getCoolingPeriodMs(): Long {
        return sharedPreferences.getLong(KEY_COOLING_PERIOD_MS, 500L)
    }

    fun isSaveImagesEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_SAVE_IMAGES, loadSettingsFromAssets().saveImages)
    }

    private fun saveSettings(settings: Settings) {
        sharedPreferences.edit()
            .putString(KEY_SERVICE_URL, settings.serviceUrl)
            .putString(KEY_DEFAULT_CAMERA, settings.default_camera)
            .putString(KEY_AGGREGATE_CODE_FILTER_TEMPLATE, settings.aggregateCodeFilterTemplate)
            .putString(KEY_AGGREGATED_CODE_FILTER_TEMPLATE, settings.aggregatedCodeFilterTemplate)
            .putLong(KEY_COOLING_PERIOD_MS, settings.coolingPeriodMs)
            .putBoolean(KEY_SAVE_IMAGES, settings.saveImages)
            .apply()
    }

    fun updateSettingsFromJson(jsonString: String) {
        try {
            val settings = json.decodeFromString<Settings>(jsonString)
            saveSettings(settings)
            Log.d(TAG, "Settings updated successfully from JSON string")
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing settings from JSON string", e)
        }
    }

    suspend fun loadSettingsFromQrCode(url: String) {
        try {
            val response: HttpResponse = httpClient.get(url)
            if (response.status.isSuccess()) {
                val jsonString = response.bodyAsText()
                val settings = json.decodeFromString<Settings>(jsonString)
                saveSettings(settings)
                Log.d(TAG, "Settings updated successfully from $url")
            } else {
                Log.e(TAG, "Failed to download settings. Status: ${response.status}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading settings", e)
        }
    }

    private fun loadSettingsFromAssets(): Settings {
        return try {
            val jsonString = context.assets.open("settings.json").bufferedReader().use { it.readText() }
            json.decodeFromString<Settings>(jsonString)
        } catch (e: IOException) {
            Log.e(TAG, "Error loading settings from assets", e)
            Settings(
                serviceUrl = "https://api.example.com/scandata",
                default_camera = "standard",
                aggregateCodeFilterTemplate = "^]C1",
                aggregatedCodeFilterTemplate = "^\\u001d",
                coolingPeriodMs = 500L,
                saveImages = true
            ) // Hardcoded fallback
        }
    }

    companion object {
        private const val KEY_SERVICE_URL = "service_url"
        private const val KEY_DEFAULT_CAMERA = "default_camera"
        private const val KEY_AGGREGATE_CODE_FILTER_TEMPLATE = "aggregate_code_filter_template"
        private const val KEY_AGGREGATED_CODE_FILTER_TEMPLATE = "aggregated_code_filter_template"
        private const val KEY_COOLING_PERIOD_MS = "cooling_period_ms"
        private const val KEY_SAVE_IMAGES = "save_images"
        private const val TAG = "SettingsManager"
    }
}

@Serializable
private data class Settings(
    val serviceUrl: String,
    val default_camera: String,
    val aggregateCodeFilterTemplate: String = "^]C1",
    val aggregatedCodeFilterTemplate: String = "^\\u001d",
    val coolingPeriodMs: Long = 500L,
    val saveImages: Boolean = true
)
