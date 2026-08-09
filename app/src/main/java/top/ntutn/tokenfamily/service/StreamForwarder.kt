package top.ntutn.tokenfamily.service

import android.os.IBinder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Response
import top.ntutn.tokenfamily.aidl.ChatCompletionRequest
import top.ntutn.tokenfamily.aidl.ChatCompletionResponse
import top.ntutn.tokenfamily.aidl.IChatStreamCallback
import top.ntutn.tokenfamily.data.model.ApiKeyConfig
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class StreamForwarder(
    private val okHttpClient: OkHttpClient = RequestForwarder.defaultClient(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    private data class StreamSession(
        val requestId: String,
        val callback: IChatStreamCallback,
        val deathRecipient: IBinder.DeathRecipient,
        var httpCall: Call? = null,
        var response: Response? = null,
        var job: Job? = null
    )

    private val sessions = ConcurrentHashMap<String, StreamSession>()
    private val requestForwarder = RequestForwarder(okHttpClient)

    fun openStream(
        request: ChatCompletionRequest,
        apiKeyConfig: ApiKeyConfig,
        callback: IChatStreamCallback
    ): ChatCompletionResponse {
        val requestId = request.requestId.ifBlank { java.util.UUID.randomUUID().toString() }
        val bodyJson = try {
            requestForwarder.prepareBody(request.bodyJson, apiKeyConfig.defaultModel)
        } catch (e: IllegalArgumentException) {
            return RequestForwarder.errorResponse(
                400,
                "INVALID_REQUEST",
                e.message ?: "Invalid JSON request"
            )
        }

        val deathRecipient = IBinder.DeathRecipient { cancelStream(requestId) }
        try {
            callback.asBinder().linkToDeath(deathRecipient, 0)
        } catch (e: Exception) {
            return RequestForwarder.errorResponse(503, "CLIENT_DEAD", "Client already disconnected")
        }

        val session = StreamSession(requestId, callback, deathRecipient)
        sessions[requestId] = session

        val call = okHttpClient.newCall(
            requestForwarder.buildHttpRequest(request, apiKeyConfig, bodyJson)
        )
        session.httpCall = call
        if (sessions[requestId] !== session) {
            call.cancel()
            return RequestForwarder.errorResponse(503, "CLIENT_DEAD", "Client already disconnected")
        }

        val response = try {
            call.execute()
        } catch (e: IOException) {
            cleanupSession(requestId)
            return RequestForwarder.errorResponse(
                502,
                "NETWORK_ERROR",
                e.message ?: "Upstream network error"
            )
        }

        if (!response.isSuccessful) {
            response.use {
                val result = with(RequestForwarder) {
                    it.toAidlResponse(it.body?.string().orEmpty())
                }
                cleanupSession(requestId)
                return result
            }
        }

        if (response.body == null) {
            response.close()
            cleanupSession(requestId)
            return RequestForwarder.errorResponse(502, "EMPTY_RESPONSE", "Empty upstream response body")
        }

        session.response = response
        val metadata = with(RequestForwarder) { response.toAidlResponse() }
        session.job = scope.launch { relayStream(session) }
        return metadata
    }

    private suspend fun relayStream(session: StreamSession) {
        val response = session.response ?: return
        try {
            response.body!!.source().use { source ->
                while (!source.exhausted() && currentCoroutineContext().isActive) {
                    val line = source.readUtf8Line() ?: break
                    emitChunked(session, "$line\n")
                }
            }
            if (currentCoroutineContext().isActive) {
                session.callback.onComplete(session.requestId)
            }
        } catch (_: CancellationException) {
            // Explicit cancellation is a normal terminal state.
        } catch (e: Exception) {
            try {
                session.callback.onError(
                    session.requestId,
                    "STREAM_ERROR",
                    e.message ?: "Stream interrupted"
                )
            } catch (_: Exception) {
            }
        } finally {
            cleanupSession(session.requestId)
        }
    }

    private fun emitChunked(session: StreamSession, value: String) {
        var start = 0
        while (start < value.length) {
            var end = minOf(start + MAX_STREAM_CHUNK_CHARS, value.length)
            if (end < value.length && end > start && value[end - 1].isHighSurrogate()) {
                end--
            }
            session.callback.onChunk(session.requestId, value.substring(start, end))
            start = end
        }
    }

    fun cancelStream(requestId: String) {
        val session = sessions.remove(requestId) ?: return
        unlinkDeath(session)
        session.httpCall?.cancel()
        session.response?.close()
        session.job?.cancel()
    }

    fun cancelAllStreams() {
        sessions.keys.toList().forEach(::cancelStream)
    }

    private fun cleanupSession(requestId: String) {
        val session = sessions.remove(requestId) ?: return
        unlinkDeath(session)
        session.response?.close()
    }

    private fun unlinkDeath(session: StreamSession) {
        try {
            session.callback.asBinder().unlinkToDeath(session.deathRecipient, 0)
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val MAX_STREAM_CHUNK_CHARS = 16 * 1024
    }
}
