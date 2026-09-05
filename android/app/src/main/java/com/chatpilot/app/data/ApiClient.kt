package com.chatpilot.app.data

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

data class SuggestionPayload(
    @SerializedName("contact_name") val contactName: String,
    @SerializedName("platform") val platform: String,
    @SerializedName("recent_messages") val recentMessages: List<MessageTurn>,
    @SerializedName("max_suggestions") val maxSuggestions: Int = 3
)

data class MessageTurn(
    @SerializedName("speaker") val speaker: String,
    @SerializedName("text") val text: String,
    @SerializedName("is_user") val isUser: Boolean = false
)

data class SuggestionResult(
    @SerializedName("contact_name") val contactName: String,
    @SerializedName("suggestions") val suggestions: List<String>,
    @SerializedName("backend") val backend: String,
    @SerializedName("latency_ms") val latencyMs: Float,
    @SerializedName("style_applied") val styleApplied: Boolean
)

data class HealthResponse(
    @SerializedName("status") val status: String,
    @SerializedName("backend_mode") val backendMode: String,
    @SerializedName("local_model") val localModel: String,
    @SerializedName("ollama") val ollama: Map<String, Any>?
)

class ApiClient(private val getBaseUrl: () -> String) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun fetchSuggestions(
        contactName: String,
        platform: String,
        recentMessages: List<MessageTurn>
    ): Result<SuggestionResult> = withContext(Dispatchers.IO) {
        try {
            val payload = SuggestionPayload(
                contactName = contactName,
                platform = platform,
                recentMessages = recentMessages
            )
            val requestBody = gson.toJson(payload).toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url("${getBaseUrl().trimEnd('/')}/v1/suggestions")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Server error: ${response.code}"))
                }
                val body = response.body?.string() ?: return@withContext Result.failure(IOException("Empty response"))
                val result = gson.fromJson(body, SuggestionResult::class.java)
                Result.success(result)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun checkHealth(): Result<HealthResponse> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("${getBaseUrl().trimEnd('/')}/v1/health")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Health check failed: ${response.code}"))
                }
                val body = response.body?.string() ?: return@withContext Result.failure(IOException("Empty body"))
                val result = gson.fromJson(body, HealthResponse::class.java)
                Result.success(result)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
