package com.example.scan

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException

class SettingsManager(private val context: Context) {

    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val httpClient = HttpClient(CIO)
    private val json = Json { ignoreUnknownKeys = true }

    fun getServiceUrl(): String {
        // Try to get from SharedPreferences first, then fallback to assets
        return sharedPreferences.getString(KEY_SERVICE_URL, null) ?: loadUrlFromAssets()
    }

    private fun saveServiceUrl(url: String) {
        sharedPreferences.edit().putString(KEY_SERVICE_URL, url).apply()
    }

    suspend fun loadSettingsFromQrCode(url: String) {
        try {
            val response: HttpResponse = httpClient.get(url)
            if (response.status.isSuccess()) {
                val jsonString = response.bodyAsText()
                val settings = json.decodeFromString<Settings>(jsonString)
                saveServiceUrl(settings.serviceUrl)
                Log.d(TAG, "Settings updated successfully from $url")
            } else {
                Log.e(TAG, "Failed to download settings. Status: ${response.status}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading settings", e)
        }
    }

    private fun loadUrlFromAssets(): String {
        return try {
            val jsonString = context.assets.open("settings.json").bufferedReader().use { it.readText() }
            val settings = json.decodeFromString<Settings>(jsonString)
            settings.serviceUrl
        } catch (e: IOException) {
            e.printStackTrace()
            "https://api.example.com/scandata" // Hardcoded fallback
        }
    }

    companion object {
        private const val KEY_SERVICE_URL = "service_url"
        private const val TAG = "SettingsManager"
    }
}

@Serializable
private data class Settings(val serviceUrl: String)
