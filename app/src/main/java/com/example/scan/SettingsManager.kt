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

    private fun saveSettings(settings: Settings) {
        sharedPreferences.edit()
            .putString(KEY_SERVICE_URL, settings.serviceUrl)
            .putString(KEY_DEFAULT_CAMERA, settings.default_camera)
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
            e.printStackTrace()
            Settings("https://api.example.com/scandata", "standard") // Hardcoded fallback
        }
    }

    companion object {
        private const val KEY_SERVICE_URL = "service_url"
        private const val KEY_DEFAULT_CAMERA = "default_camera"
        private const val TAG = "SettingsManager"
    }
}

@Serializable
private data class Settings(val serviceUrl: String, val default_camera: String)
