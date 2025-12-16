package com.example.scan

import android.content.Context
import androidx.preference.PreferenceManager
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.IOException

class SettingsManager(private val context: Context) {

    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val httpClient = HttpClient(CIO)
    private val json = Json { ignoreUnknownKeys = true }

    fun getServiceUrl(): String {
        return sharedPreferences.getString(KEY_SERVICE_URL, null) ?: loadSettingsFromAssets().serviceUrl
    }

    fun getMaxLogSizeMb(): Long {
        return sharedPreferences.getLong(KEY_MAX_LOG_SIZE_MB, -1L).takeIf { it != -1L }
            ?: loadSettingsFromAssets().maxLogSizeMb
    }

    private fun saveSettings(settings: Settings) {
        sharedPreferences.edit()
            .putString(KEY_SERVICE_URL, settings.serviceUrl)
            .putLong(KEY_MAX_LOG_SIZE_MB, settings.maxLogSizeMb)
            .apply()
    }

    suspend fun loadSettingsFromQrCode(url: String) {
        try {
            val response: HttpResponse = httpClient.get(url)
            if (response.status.isSuccess()) {
                val jsonString = response.bodyAsText()
                val settings = json.decodeFromString<Settings>(jsonString)
                saveSettings(settings)
                Timber.d("Settings updated successfully from $url")
            } else {
                Timber.e("Failed to download settings. Status: ${response.status}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error downloading settings")
        }
    }

    private fun loadSettingsFromAssets(): Settings {
        return try {
            val jsonString = context.assets.open("settings.json").bufferedReader().use { it.readText() }
            json.decodeFromString<Settings>(jsonString)
        } catch (e: IOException) {
            Timber.e(e, "Error loading settings from assets")
            // Hardcoded fallback
            Settings("https://api.example.com/scandata", 10L)
        }
    }

    companion object {
        private const val KEY_SERVICE_URL = "service_url"
        private const val KEY_MAX_LOG_SIZE_MB = "max_log_size_mb"
    }
}

@Serializable
private data class Settings(
    val serviceUrl: String,
    val maxLogSizeMb: Long = 10L
)
