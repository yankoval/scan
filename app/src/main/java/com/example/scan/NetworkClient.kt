package com.example.scan

import android.content.Context
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber

@Serializable
data class ScanRequest(val codes: List<String>)

class NetworkClient(context: Context) {

    private val settingsManager = SettingsManager(context)
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
            })
        }
    }

    suspend fun sendData(codes: List<String>) {
        try {
            val url = settingsManager.getServiceUrl()
            val requestBody = ScanRequest(codes)
            Timber.d("Sending ${codes.size} codes to $url")
            client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }
            Timber.i("Successfully sent codes to server.")
        } catch (e: Exception) {
            Timber.e(e, "Error sending data to server")
        }
    }
}
