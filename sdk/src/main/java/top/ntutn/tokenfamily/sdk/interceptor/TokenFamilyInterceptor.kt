package top.ntutn.tokenfamily.sdk.interceptor

import android.os.DeadObjectException
import android.os.RemoteException
import com.google.gson.Gson
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import top.ntutn.tokenfamily.aidl.ChatCompletionRequest
import top.ntutn.tokenfamily.aidl.ChatCompletionResponse
import top.ntutn.tokenfamily.aidl.IChatStreamCallback
import top.ntutn.tokenfamily.sdk.binder.BinderRequestConverter
import top.ntutn.tokenfamily.sdk.binder.ServiceConnector
import top.ntutn.tokenfamily.sdk.binder.StreamResponseWrapper
import top.ntutn.tokenfamily.sdk.exception.PayloadTooLargeException
import top.ntutn.tokenfamily.sdk.exception.TokenFamilyException

class TokenFamilyInterceptor(
    private val serviceConnector: ServiceConnector
) : Interceptor {

    private val requestConverter = BinderRequestConverter()

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        if (!isChatCompletionRequest(originalRequest)) return chain.proceed(originalRequest)

        val converted = try {
            requestConverter.toChatCompletionRequest(originalRequest)
        } catch (e: PayloadTooLargeException) {
            throw e
        } catch (e: IllegalArgumentException) {
            return localErrorResponse(originalRequest, 400, "INVALID_REQUEST", e.message ?: "Invalid request")
        }

        serviceConnector.connect()
        return if (converted.isStream) {
            handleStreamRequest(originalRequest, converted.binderRequest) {
                chain.call().isCanceled()
            }
        } else {
            handleNonStreamRequest(originalRequest, converted.binderRequest)
        }
    }

    private fun handleNonStreamRequest(
        originalRequest: Request,
        request: ChatCompletionRequest
    ): Response {
        val response = binderCall { serviceConnector.getService().chat(request) }
        return response.toHttpResponse(originalRequest)
    }

    private fun handleStreamRequest(
        originalRequest: Request,
        request: ChatCompletionRequest,
        isCallCancelled: () -> Boolean
    ): Response {
        lateinit var wrapper: StreamResponseWrapper
        val callback = object : IChatStreamCallback.Stub() {
            override fun onChunk(requestId: String?, chunk: String?) {
                if (chunk != null) wrapper.onChunk(chunk)
            }

            override fun onComplete(requestId: String?) {
                wrapper.onComplete()
            }

            override fun onError(requestId: String?, errorCode: String?, errorMessage: String?) {
                wrapper.onError(errorCode ?: "STREAM_ERROR", errorMessage ?: "Stream interrupted")
            }
        }

        wrapper = StreamResponseWrapper(
            cancelStream = {
                serviceConnector.getService().cancelStream(request.requestId)
            },
            isCallCancelled = isCallCancelled,
            mediaType = "text/event-stream".toMediaTypeOrNull()
        )

        val metadata = binderCall {
            serviceConnector.getService().streamChat(request, callback)
        }
        if (metadata.statusCode !in 200..299) {
            return metadata.toHttpResponse(originalRequest)
        }

        return metadata.toHttpResponse(
            originalRequest = originalRequest,
            streamingBody = wrapper.createResponseBody()
        )
    }

    private fun <T> binderCall(block: () -> T): T = try {
        block()
    } catch (e: DeadObjectException) {
        serviceConnector.invalidateConnection()
        throw TokenFamilyException("IPC_DISCONNECTED", "词元芯核服务连接已中断", e)
    } catch (e: RemoteException) {
        serviceConnector.invalidateConnection()
        throw TokenFamilyException("IPC_ERROR", e.message ?: "Binder 调用失败", e)
    }

    private fun ChatCompletionResponse.toHttpResponse(
        originalRequest: Request,
        streamingBody: okhttp3.ResponseBody? = null
    ): Response {
        val code = statusCode.takeIf { it in 100..599 } ?: 502
        val mediaType = contentType?.toMediaTypeOrNull()
        val responseBody = streamingBody ?: (body ?: "").toResponseBody(mediaType)
        return Response.Builder()
            .request(originalRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(statusMessage?.takeIf { it.isNotBlank() } ?: "HTTP $code")
            .headers(safeHeaders(headerNames, headerValues))
            .body(responseBody)
            .build()
    }

    private fun safeHeaders(names: List<String>?, values: List<String>?): Headers {
        val builder = Headers.Builder()
        (names ?: emptyList()).zip(values ?: emptyList()).forEach { (name, value) ->
            try {
                builder.add(name, value)
            } catch (_: IllegalArgumentException) {
            }
        }
        return builder.build()
    }

    private fun localErrorResponse(
        request: Request,
        statusCode: Int,
        code: String,
        message: String
    ): Response {
        val body = Gson().toJson(
            mapOf(
                "error" to mapOf(
                    "type" to "tokenfamily_error",
                    "code" to code,
                    "message" to message
                )
            )
        )
        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(statusCode)
            .message(if (statusCode == 400) "Bad Request" else "HTTP $statusCode")
            .header("Content-Type", mediaType.toString())
            .body(body.toResponseBody(mediaType))
            .build()
    }

    private fun isChatCompletionRequest(request: Request): Boolean {
        val path = request.url.encodedPath
        return path.endsWith("/v1/chat/completions") || path.endsWith("/chat/completions")
    }
}
