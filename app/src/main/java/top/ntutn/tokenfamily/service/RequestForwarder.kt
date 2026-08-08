package top.ntutn.tokenfamily.service

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import top.ntutn.tokenfamily.aidl.ChatCompletionRequest
import top.ntutn.tokenfamily.aidl.ChatCompletionResponse
import top.ntutn.tokenfamily.data.model.ApiKeyConfig
import java.util.concurrent.TimeUnit

class RequestForwarder(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun forward(
        request: ChatCompletionRequest,
        apiKeyConfig: ApiKeyConfig
    ): ChatCompletionResponse = withContext(Dispatchers.IO) {
        try {
            val requestBody = buildRequestBody(request, apiKeyConfig)
            val httpRequest = Request.Builder()
                .url("${apiKeyConfig.apiBaseUrl.trimEnd('/')}/v1/chat/completions")
                .addHeader("Authorization", "Bearer ${apiKeyConfig.apiKey}")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(httpRequest).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                parseSuccessResponse(responseBody, apiKeyConfig)
            } else {
                parseErrorResponse(responseBody, response.code)
            }
        } catch (e: Exception) {
            buildErrorResponse("NETWORK_ERROR", e.message ?: "Network error occurred")
        }
    }

    private fun buildRequestBody(
        request: ChatCompletionRequest,
        apiKeyConfig: ApiKeyConfig
    ) = JsonObject().apply {
        addProperty("model", request.model.ifEmpty { apiKeyConfig.defaultModel })
        addProperty("temperature", request.temperature)
        addProperty("top_p", request.topP)
        if (request.maxTokens > 0) {
            addProperty("max_tokens", request.maxTokens)
        }
        addProperty("stream", request.stream)
        add("messages", request.messages?.let { messages ->
            JsonArray().also { array ->
                messages.forEach { msg ->
                    array.add(JsonObject().apply {
                        addProperty("role", msg.role)
                        addProperty("content", msg.content)
                    })
                }
            }
        } ?: JsonArray())
    }.toString().toRequestBody(jsonMediaType)

    private fun parseSuccessResponse(
        body: String,
        apiKeyConfig: ApiKeyConfig
    ): ChatCompletionResponse {
        val json = gson.fromJson(body, JsonObject::class.java)
        val choices = json.getAsJsonArray("choices") ?: JsonArray()
        val firstChoice = choices.firstOrNull()?.asJsonObject
        val message = firstChoice?.getAsJsonObject("message")
        val content = message?.get("content")?.asString ?: ""
        val usage = json.getAsJsonObject("usage")

        return ChatCompletionResponse().apply {
            id = json.get("id")?.asString ?: ""
            model = json.get("model")?.asString ?: apiKeyConfig.defaultModel
            this.content = content
            promptTokens = usage?.get("prompt_tokens")?.asInt ?: 0
            completionTokens = usage?.get("completion_tokens")?.asInt ?: 0
            totalTokens = usage?.get("total_tokens")?.asInt ?: 0
            errorCode = ""
            errorMessage = ""
        }
    }

    private fun parseErrorResponse(body: String?, code: Int): ChatCompletionResponse {
        val errorMsg = try {
            body?.let {
                val json = gson.fromJson(it, JsonObject::class.java)
                json.getAsJsonObject("error")?.get("message")?.asString ?: "HTTP $code"
            } ?: "HTTP $code"
        } catch (e: Exception) {
            "HTTP $code"
        }

        return buildErrorResponse("UPSTREAM_ERROR", errorMsg)
    }

    private fun buildErrorResponse(code: String, message: String) =
        ChatCompletionResponse().apply {
            id = ""
            model = ""
            content = ""
            promptTokens = 0
            completionTokens = 0
            totalTokens = 0
            errorCode = code
            errorMessage = message
        }
}
