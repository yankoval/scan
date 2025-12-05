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
import java.io.IOException

@Serializable
data class ScanRequest(val codes: List<String>)

class NetworkClient(private val context: Context) {

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
            })
        }
    }

    private fun getServiceUrl(): String {
        return try {
            val jsonString = context.assets.open("settings.json").bufferedReader().use { it.readText() }
            val json = Json { ignoreUnknownKeys = true }
            val settings = json.decodeFromString<Settings>(jsonString)
            settings.serviceUrl
        } catch (e: IOException) {
            e.printStackTrace()
            "https://api.example.com/scandata" // Fallback URL
        }
    }

    suspend fun sendData(codes: List<String>) {
        try {
            val url = getServiceUrl()
            val requestBody = ScanRequest(codes)
            client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Serializable
private data class Settings(val serviceUrl: String)
