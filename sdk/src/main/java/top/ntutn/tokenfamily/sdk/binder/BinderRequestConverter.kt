package top.ntutn.tokenfamily.sdk.binder

import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.Request
import okhttp3.RequestBody
import okio.Buffer
import top.ntutn.tokenfamily.aidl.ChatCompletionRequest
import top.ntutn.tokenfamily.aidl.ChatMessage
import java.util.UUID

class BinderRequestConverter {

    private val gson = Gson()

    fun toChatCompletionRequest(httpRequest: Request): ChatCompletionRequest {
        val requestBody = httpRequest.body ?: throw IllegalArgumentException("Request body is required")
        val json = parseRequestBody(requestBody)

        val request = ChatCompletionRequest()

        request.requestId = UUID.randomUUID().toString()
        request.model = json.get("model")?.asString ?: ""
        request.temperature = json.get("temperature")?.asDouble ?: 1.0
        request.topP = json.get("top_p")?.asDouble ?: 1.0
        request.maxTokens = json.get("max_tokens")?.asInt ?: 0
        request.stream = json.get("stream")?.asBoolean ?: false

        val messagesArray = json.getAsJsonArray("messages")
        if (messagesArray != null) {
            val messages = mutableListOf<ChatMessage>()
            messagesArray.forEach { element ->
                val msgObj = element.asJsonObject
                val chatMsg = ChatMessage()
                chatMsg.role = msgObj.get("role")?.asString ?: "user"
                chatMsg.content = msgObj.get("content")?.asString ?: ""
                messages.add(chatMsg)
            }
            request.messages = messages
        }

        return request
    }

    private fun parseRequestBody(requestBody: RequestBody): JsonObject {
        val buffer = Buffer()
        requestBody.writeTo(buffer)
        val bodyString = buffer.readUtf8()
        return gson.fromJson(bodyString, JsonObject::class.java)
    }
}
