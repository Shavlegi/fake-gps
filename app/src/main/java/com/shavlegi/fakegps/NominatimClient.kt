package com.shavlegi.fakegps

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

@Serializable
data class NominatimResult(
    val display_name: String,
    val lat: String,
    val lon: String
)

/**
 * Free place search via OpenStreetMap Nominatim.
 * No API key needed. Nominatim usage policy requires a descriptive
 * User-Agent — we send one.
 */
object NominatimClient {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun search(query: String): List<NominatimResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val url = URL(
            "https://nominatim.openstreetmap.org/search" +
                "?format=json&addressdetails=0&limit=5&q=$encoded"
        )
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty(
            "User-Agent",
            "ShavlegiFakeGPS/1.0 (contact: locallink.ge@gmail.com)"
        )
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return@withContext emptyList()
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            json.decodeFromString<List<NominatimResult>>(body)
        } catch (e: Exception) {
            emptyList()
        } finally {
            conn.disconnect()
        }
    }
}
