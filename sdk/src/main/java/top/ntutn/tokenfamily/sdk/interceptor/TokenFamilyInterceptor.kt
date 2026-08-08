package top.ntutn.tokenfamily.sdk.interceptor

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import top.ntutn.tokenfamily.aidl.ChatCompletionResponse
import top.ntutn.tokenfamily.aidl.IChatStreamCallback
import top.ntutn.tokenfamily.sdk.binder.BinderRequestConverter
import top.ntutn.tokenfamily.sdk.binder.ServiceConnector
import top.ntutn.tokenfamily.sdk.binder.StreamResponseWrapper
import top.ntutn.tokenfamily.sdk.exception.TokenFamilyException

class TokenFamilyInterceptor(
    private val serviceConnector: ServiceConnector
) : Interceptor {

    private val gson = Gson()
    private val requestConverter = BinderRequestConverter()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val sseMediaType = "text/event-stream".toMediaType()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (!isChatCompletionRequest(request)) {
            return chain.proceed(request)
        }

        ensureServiceBound()

        val binderRequest = requestConverter.toChatCompletionRequest(request)

        return if (binderRequest.stream) {
            handleStreamRequest(request, binderRequest)
        } else {
            handleNonStreamRequest(request, binderRequest)
        }
    }

    private fun handleNonStreamRequest(
        originalRequest: okhttp3.Request,
        request: top.ntutn.tokenfamily.aidl.ChatCompletionRequest
    ): Response {
        val service = serviceConnector.getService()
        val response: ChatCompletionResponse = service.chat(request)

        if (response.errorCode.isNotEmpty()) {
            throw TokenFamilyException(response.errorCode, response.errorMessage)
        }

        val jsonResponse = buildSuccessJson(response)
        val responseBody = jsonResponse.toResponseBody(jsonMediaType)

        return Response.Builder()
            .request(originalRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(responseBody)
            .build()
    }

    private fun handleStreamRequest(
        originalRequest: okhttp3.Request,
        request: top.ntutn.tokenfamily.aidl.ChatCompletionRequest
    ): Response {
        val service = serviceConnector.getService()
        val wrapper = StreamResponseWrapper(request.requestId, serviceConnector)

        val callback = object : IChatStreamCallback.Stub() {
            override fun onChunk(requestId: String?, chunk: String?) {
                if (chunk != null) {
                    wrapper.onChunk(chunk)
                }
            }

            override fun onComplete(requestId: String?) {
                wrapper.onComplete()
            }

            override fun onError(requestId: String?, errorCode: String?, errorMessage: String?) {
                wrapper.onError(errorCode ?: "UNKNOWN", errorMessage ?: "Unknown error")
            }
        }

        service.streamChat(request, callback)

        val responseBody = wrapper.createResponseBody()

        return Response.Builder()
            .request(originalRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .header("Content-Type", "text/event-stream")
            .body(responseBody)
            .build()
    }

    private fun ensureServiceBound() {
        serviceConnector.connect()
    }

    private fun isChatCompletionRequest(request: okhttp3.Request): Boolean {
        val url = request.url.toString()
        return url.contains("/v1/chat/completions") ||
                url.contains("/chat/completions")
    }

    private fun buildSuccessJson(response: ChatCompletionResponse): String {
        val json = JsonObject().apply {
            addProperty("id", response.id)
            addProperty("object", "chat.completion")
            addProperty("model", response.model)
            addProperty("created", System.currentTimeMillis() / 1000)
            add("choices", com.google.gson.JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("index", 0)
                    add("message", JsonObject().apply {
                        addProperty("role", "assistant")
                        addProperty("content", response.content)
                    })
                    addProperty("finish_reason", "stop")
                })
            })
            add("usage", JsonObject().apply {
                addProperty("prompt_tokens", response.promptTokens)
                addProperty("completion_tokens", response.completionTokens)
                addProperty("total_tokens", response.totalTokens)
            })
        }
        return gson.toJson(json)
    }
}
