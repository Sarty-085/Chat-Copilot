package com.chatpilot.app.data

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
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

data class ContactProfile(
    @SerializedName("contact_name") var contactName: String = "",
    @SerializedName("total_messages_analyzed") var totalMessagesAnalyzed: Int = 0,
    @SerializedName("avg_chars_per_reply") var avgCharsPerReply: Float = 0f,
    @SerializedName("avg_words_per_reply") var avgWordsPerReply: Float = 0f,
    @SerializedName("emoji_density") var emojiDensity: Float = 0f,
    @SerializedName("top_emojis") var topEmojis: List<String> = emptyList(),
    @SerializedName("formality_score") var formalityScore: Float = 0.3f,
    @SerializedName("capitalization_rate") var capitalizationRate: Float = 0.5f,
    @SerializedName("punctuation_style") var punctuationStyle: String = "balanced",
    @SerializedName("common_phrases") var commonPhrases: List<String> = emptyList(),
    @SerializedName("common_greetings") var commonGreetings: List<String> = emptyList(),
    @SerializedName("common_signoffs") var commonSignoffs: List<String> = emptyList(),
    @SerializedName("custom_tone_notes") var customToneNotes: String = ""
)

data class ImportResponse(
    @SerializedName("status") val status: String,
    @SerializedName("contact_name") val contactName: String,
    @SerializedName("platform") val platform: String,
    @SerializedName("total_messages") val totalMessages: Int,
    @SerializedName("total_turns") val totalTurns: Int,
    @SerializedName("indexed_exchange_pairs") val indexedExchangePairs: Int,
    @SerializedName("style_profile") val styleProfile: ContactProfile? = null
)

fun normalizeBaseUrl(raw: String, defaultPort: String = "8000"): String {
    var url = raw.trim()
    if (url.isEmpty()) {
        return "http://192.168.1.2:$defaultPort"
    }
    if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
        url = "http://$url"
    }
    val schemeEnd = url.indexOf("://") + 3
    val pathStart = url.indexOf('/', schemeEnd)
    val hostPort = if (pathStart != -1) url.substring(schemeEnd, pathStart) else url.substring(schemeEnd)

    if (!hostPort.contains(":")) {
        val path = if (pathStart != -1) url.substring(pathStart) else ""
        url = "${url.substring(0, schemeEnd)}$hostPort:$defaultPort$path"
    }
    return url.trimEnd('/')
}

class ApiClient(private val getBaseUrl: () -> String) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun sanitizedBaseUrl(): String = normalizeBaseUrl(getBaseUrl())

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
                .url("${sanitizedBaseUrl()}/v1/suggestions")
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
                .url("${sanitizedBaseUrl()}/v1/health")
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

    suspend fun uploadWhatsAppExport(
        fileBytes: ByteArray,
        fileName: String = "chat.txt",
        contactName: String? = null,
        userName: String? = null
    ): Result<ImportResponse> = withContext(Dispatchers.IO) {
        try {
            val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    fileName,
                    fileBytes.toRequestBody("text/plain".toMediaTypeOrNull())
                )
            if (!contactName.isNullOrBlank()) {
                builder.addFormDataPart("contact_name", contactName.trim())
            }
            if (!userName.isNullOrBlank()) {
                builder.addFormDataPart("user_name", userName.trim())
            }

            val request = Request.Builder()
                .url("${sanitizedBaseUrl()}/api/import/whatsapp")
                .post(builder.build())
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Server error ${response.code}: $body"))
                }
                val result = gson.fromJson(body, ImportResponse::class.java)
                Result.success(result)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadInstagramExport(
        fileBytes: ByteArray,
        fileName: String = "messages.json",
        contactName: String? = null,
        userName: String? = null
    ): Result<ImportResponse> = withContext(Dispatchers.IO) {
        try {
            val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    fileName,
                    fileBytes.toRequestBody("application/json".toMediaTypeOrNull())
                )
            if (!contactName.isNullOrBlank()) {
                builder.addFormDataPart("contact_name", contactName.trim())
            }
            if (!userName.isNullOrBlank()) {
                builder.addFormDataPart("user_name", userName.trim())
            }

            val request = Request.Builder()
                .url("${sanitizedBaseUrl()}/api/import/instagram")
                .post(builder.build())
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Server error ${response.code}: $body"))
                }
                val result = gson.fromJson(body, ImportResponse::class.java)
                Result.success(result)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchProfiles(): Result<List<ContactProfile>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("${sanitizedBaseUrl()}/api/profiles")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Failed to fetch profiles: ${response.code}"))
                }
                val type = object : TypeToken<List<ContactProfile>>() {}.type
                val profiles: List<ContactProfile> = gson.fromJson(body, type) ?: emptyList()
                Result.success(profiles)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateProfile(contactName: String, profile: ContactProfile): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val json = gson.toJson(profile)
            val requestBody = json.toRequestBody(jsonMediaType)
            val encodedContact = URLEncoder.encode(contactName, "UTF-8")
            val request = Request.Builder()
                .url("${sanitizedBaseUrl()}/api/profiles/$encodedContact")
                .put(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Failed to update profile: ${response.code}"))
                }
                Result.success(true)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
