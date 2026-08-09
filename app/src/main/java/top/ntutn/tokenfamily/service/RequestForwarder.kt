package top.ntutn.tokenfamily.service

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import top.ntutn.tokenfamily.aidl.ChatCompletionRequest
import top.ntutn.tokenfamily.aidl.ChatCompletionResponse
import top.ntutn.tokenfamily.data.model.ApiKeyConfig
import java.io.IOException
import java.util.concurrent.TimeUnit

class RequestForwarder(
    private val okHttpClient: OkHttpClient = defaultClient()
) {

    suspend fun forward(
        request: ChatCompletionRequest,
        apiKeyConfig: ApiKeyConfig
    ): ChatCompletionResponse = withContext(Dispatchers.IO) {
        val bodyJson = try {
            prepareBody(request.bodyJson, apiKeyConfig.defaultModel)
        } catch (e: IllegalArgumentException) {
            return@withContext errorResponse(400, "INVALID_REQUEST", e.message ?: "Invalid JSON request")
        }

        try {
            val httpRequest = buildHttpRequest(request, apiKeyConfig, bodyJson)
            okHttpClient.newCall(httpRequest).execute().use { response ->
                response.toAidlResponse(response.body?.string().orEmpty())
            }
        } catch (e: IOException) {
            errorResponse(502, "NETWORK_ERROR", e.message ?: "Upstream network error")
        }
    }

    fun requestedModel(bodyJson: String): String {
        val json = parseObject(bodyJson)
        return readModel(json)
    }

    internal fun prepareBody(bodyJson: String, defaultModel: String): String {
        val json = parseObject(bodyJson)
        val model = readModel(json)
        if (model.isNullOrBlank()) {
            json.addProperty("model", defaultModel)
            return GSON.toJson(json)
        }
        return bodyJson
    }

    internal fun buildHttpRequest(
        request: ChatCompletionRequest,
        apiKeyConfig: ApiKeyConfig,
        bodyJson: String
    ): Request {
        val builder = Request.Builder()
            .url(ChatCompletionUrl.normalize(apiKeyConfig.apiBaseUrl))
            .header("Authorization", "Bearer ${apiKeyConfig.apiKey}")
            .header("Content-Type", JSON_MEDIA_TYPE.toString())

        request.headers().forEach { (name, value) -> builder.addHeader(name, value) }

        return builder.post(bodyJson.toRequestBody(JSON_MEDIA_TYPE)).build()
    }

    private fun parseObject(bodyJson: String): JsonObject {
        val element = try {
            JsonParser.parseString(bodyJson)
        } catch (e: Exception) {
            throw IllegalArgumentException("Request body must be valid JSON", e)
        }
        if (!element.isJsonObject) {
            throw IllegalArgumentException("Request body must be a JSON object")
        }
        return element.asJsonObject
    }

    private fun readModel(json: JsonObject): String {
        val model = json.get("model") ?: return ""
        if (model.isJsonNull) return ""
        if (!model.isJsonPrimitive || !model.asJsonPrimitive.isString) {
            throw IllegalArgumentException("model must be a string")
        }
        return model.asString
    }

    companion object {
        private val GSON = Gson()
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val HOP_BY_HOP_HEADERS = setOf(
            "connection",
            "keep-alive",
            "proxy-authenticate",
            "proxy-authorization",
            "te",
            "trailer",
            "transfer-encoding",
            "upgrade",
            "content-length",
            "set-cookie"
        )

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        fun errorResponse(statusCode: Int, code: String, message: String): ChatCompletionResponse {
            val body = GSON.toJson(
                mapOf(
                    "error" to mapOf(
                        "type" to "tokenfamily_error",
                        "code" to code,
                        "message" to message
                    )
                )
            )
            return ChatCompletionResponse().apply {
                this.statusCode = statusCode
                statusMessage = defaultStatusMessage(statusCode)
                contentType = JSON_MEDIA_TYPE.toString()
                this.body = body
                headerNames = arrayListOf("Content-Type")
                headerValues = arrayListOf(JSON_MEDIA_TYPE.toString())
            }
        }

        fun Response.toAidlResponse(body: String = ""): ChatCompletionResponse {
            if (body.toByteArray(Charsets.UTF_8).size > MAX_BINDER_RESPONSE_BYTES) {
                return errorResponse(
                    502,
                    "PAYLOAD_TOO_LARGE",
                    "上游响应超过 Binder 安全上限 ${MAX_BINDER_RESPONSE_BYTES / 1024} KiB"
                )
            }
            val names = arrayListOf<String>()
            val values = arrayListOf<String>()
            headers.forEach { (name, value) ->
                if (name.lowercase() !in HOP_BY_HOP_HEADERS) {
                    names += name
                    values += value
                }
            }
            return ChatCompletionResponse().apply {
                statusCode = code
                statusMessage = message.ifBlank { defaultStatusMessage(code) }
                contentType = this@toAidlResponse.body?.contentType()?.toString()
                    ?: header("Content-Type").orEmpty()
                this.body = body
                headerNames = names
                headerValues = values
            }
        }

        private fun defaultStatusMessage(code: Int): String = when (code) {
            200 -> "OK"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            403 -> "Forbidden"
            404 -> "Not Found"
            429 -> "Too Many Requests"
            500 -> "Internal Server Error"
            502 -> "Bad Gateway"
            503 -> "Service Unavailable"
            else -> "HTTP $code"
        }

        private const val MAX_BINDER_RESPONSE_BYTES = 512 * 1024
    }
}

internal fun ChatCompletionRequest.headers(): Headers {
    val builder = Headers.Builder()
    val names = headerNames ?: emptyList()
    val values = headerValues ?: emptyList()
    names.zip(values).forEach { (name, value) ->
        if (name.lowercase() in BLOCKED_SERVER_REQUEST_HEADERS) return@forEach
        try {
            builder.add(name, value)
        } catch (_: IllegalArgumentException) {
            // SDK already filters headers. Ignore malformed input from a custom Binder client.
        }
    }
    return builder.build()
}

private val BLOCKED_SERVER_REQUEST_HEADERS = setOf(
    "authorization",
    "proxy-authorization",
    "cookie",
    "host",
    "content-length",
    "content-type",
    "transfer-encoding",
    "connection",
    "keep-alive",
    "upgrade",
    "te",
    "trailer"
)
