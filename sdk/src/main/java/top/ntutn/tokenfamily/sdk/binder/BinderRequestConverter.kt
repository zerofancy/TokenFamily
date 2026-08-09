package top.ntutn.tokenfamily.sdk.binder

import com.google.gson.JsonParser
import com.google.gson.JsonObject
import okhttp3.Request
import okhttp3.RequestBody
import okio.Buffer
import top.ntutn.tokenfamily.aidl.ChatCompletionRequest
import top.ntutn.tokenfamily.sdk.exception.PayloadTooLargeException
import java.util.UUID

class BinderRequestConverter {

    data class ConvertedRequest(
        val binderRequest: ChatCompletionRequest,
        val isStream: Boolean
    )

    fun toChatCompletionRequest(httpRequest: Request): ConvertedRequest {
        val requestBody = httpRequest.body ?: throw IllegalArgumentException("Request body is required")
        val bodyJson = readRequestBody(requestBody)
        val json = parseRequestBody(bodyJson)

        val request = ChatCompletionRequest()
        request.requestId = UUID.randomUUID().toString()
        request.bodyJson = bodyJson

        val names = arrayListOf<String>()
        val values = arrayListOf<String>()
        httpRequest.headers.forEach { (name, value) ->
            if (name.lowercase() !in BLOCKED_REQUEST_HEADERS) {
                names += name
                values += value
            }
        }
        val binderPayloadBytes = bodyJson.toByteArray(Charsets.UTF_8).size +
            names.zip(values).sumOf { (name, value) ->
                name.toByteArray(Charsets.UTF_8).size + value.toByteArray(Charsets.UTF_8).size
            }
        if (binderPayloadBytes > MAX_BINDER_BODY_BYTES) {
            throw PayloadTooLargeException(
                "请求超过 Binder 安全上限 ${MAX_BINDER_BODY_BYTES / 1024} KiB"
            )
        }
        request.headerNames = names
        request.headerValues = values

        return ConvertedRequest(
            binderRequest = request,
            isStream = parseStream(json)
        )
    }

    private fun readRequestBody(requestBody: RequestBody): String {
        val buffer = Buffer()
        requestBody.writeTo(buffer)
        return buffer.readUtf8()
    }

    private fun parseRequestBody(bodyJson: String): JsonObject {
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

    private fun parseStream(json: JsonObject): Boolean {
        val stream = json.get("stream") ?: return false
        if (stream.isJsonNull) return false
        if (!stream.isJsonPrimitive || !stream.asJsonPrimitive.isBoolean) {
            throw IllegalArgumentException("stream must be a boolean")
        }
        return stream.asBoolean
    }

    companion object {
        const val MAX_BINDER_BODY_BYTES = 512 * 1024

        private val BLOCKED_REQUEST_HEADERS = setOf(
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
    }
}
