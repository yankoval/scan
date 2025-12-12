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

    fun getScanTunePeriod(): Long {
        // -1 is a sentinel value indicating it's not in prefs, so we load from assets
        val period = sharedPreferences.getLong(KEY_SCAN_TUNE_PERIOD, -1L)
        return if (period != -1L) period else loadSettingsFromAssets().scan_tune_period
    }

    private fun saveSettings(settings: Settings) {
        sharedPreferences.edit()
            .putString(KEY_SERVICE_URL, settings.serviceUrl)
            .putLong(KEY_SCAN_TUNE_PERIOD, settings.scan_tune_period)
            .apply()
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
            // Hardcoded fallback
            Settings("https://api.example.com/scandata", 3L)
        }
    }

    companion object {
        private const val KEY_SERVICE_URL = "service_url"
        private const val KEY_SCAN_TUNE_PERIOD = "scan_tune_period"
        private const val TAG = "SettingsManager"
    }
}

@Serializable
private data class Settings(
    val serviceUrl: String,
    val scan_tune_period: Long = 3L // Default value
)
