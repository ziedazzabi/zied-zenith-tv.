package com.zenith.iptv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

object Net {
    const val DEFAULT_UA = "Mozilla/5.0 (Linux; Android 12; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36 ZenithTV/1.0"

    val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true; explicitNulls = false }

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    /** Long-running client for recordings: no read timeout. */
    val streamClient: OkHttpClient = client.newBuilder().readTimeout(0, TimeUnit.SECONDS).build()

    fun request(url: String, headers: Map<String, String> = emptyMap(), ua: String = ""): Request {
        val b = Request.Builder().url(url)
        b.header("User-Agent", ua.ifBlank { DEFAULT_UA })
        headers.forEach { (k, v) -> b.header(k, v) }
        return b.build()
    }

    suspend fun getText(url: String, headers: Map<String, String> = emptyMap(), ua: String = ""): String =
        withContext(Dispatchers.IO) {
            client.newCall(request(url, headers, ua)).execute().use { r -> checkOk(r); r.body?.string() ?: "" }
        }

    suspend fun getBytes(url: String, headers: Map<String, String> = emptyMap(), ua: String = ""): ByteArray =
        withContext(Dispatchers.IO) {
            client.newCall(request(url, headers, ua)).execute().use { r -> checkOk(r); r.body?.bytes() ?: ByteArray(0) }
        }

    suspend fun getJson(url: String, headers: Map<String, String> = emptyMap(), ua: String = ""): JsonElement {
        val text = getText(url, headers, ua).trim()
        if (text.isEmpty()) return JsonNull
        return try { json.parseToJsonElement(text) } catch (e: Exception) {
            throw IOException("Invalid server response: ${text.take(120)}")
        }
    }

    private fun checkOk(r: Response) {
        if (!r.isSuccessful) throw IOException("HTTP ${r.code} ${r.message}")
    }
}

// ---- Tolerant JSON helpers (IPTV panels mix strings, numbers and nulls freely) ----

fun JsonElement?.obj(): JsonObject? = this as? JsonObject
fun JsonElement?.arr(): JsonArray? = when (this) {
    is JsonArray -> this
    is JsonObject -> JsonArray(this.values.toList()) // some panels return {"1":{..},"2":{..}}
    else -> null
}

fun JsonObject?.str(key: String): String {
    val e = this?.get(key) ?: return ""
    return when (e) {
        is JsonPrimitive -> e.contentOrNull ?: ""
        JsonNull -> ""
        else -> e.toString()
    }
}

fun JsonObject?.int(key: String): Int = str(key).toDoubleOrNull()?.toInt() ?: 0
fun JsonObject?.long(key: String): Long = str(key).toDoubleOrNull()?.toLong() ?: 0L
fun JsonObject?.child(key: String): JsonElement? = this?.get(key)
