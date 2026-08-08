package top.ntutn.tokenfamily.service

import android.os.IBinder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import top.ntutn.tokenfamily.aidl.ChatCompletionRequest
import top.ntutn.tokenfamily.aidl.IChatStreamCallback
import top.ntutn.tokenfamily.data.model.ApiKeyConfig
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class StreamForwarder {

    private data class StreamSession(
        val requestId: String,
        val callback: IChatStreamCallback,
        val deathRecipient: IBinder.DeathRecipient,
        var httpCall: okhttp3.Call? = null,
        var job: Job? = null
    )

    private val sessions = ConcurrentHashMap<String, StreamSession>()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    @kotlinx.coroutines.ExperimentalCoroutinesApi
    fun startStream(
        requestId: String,
        request: ChatCompletionRequest,
        apiKeyConfig: ApiKeyConfig,
        callback: IChatStreamCallback
    ) {
        val deathRecipient = IBinder.DeathRecipient {
            cancelStream(requestId)
        }

        try {
            (callback as android.os.IInterface).asBinder().linkToDeath(deathRecipient, 0)
        } catch (e: Exception) {
            callback.onError(requestId, "CLIENT_DEAD", "Client already disconnected")
            return
        }

        val session = StreamSession(
            requestId = requestId,
            callback = callback,
            deathRecipient = deathRecipient
        )

        sessions[requestId] = session

        val job = CoroutineScope(Dispatchers.IO).launch {
            try {
                executeStream(requestId, request, apiKeyConfig, session)
            } catch (e: CancellationException) {
                cleanupSession(requestId)
            } catch (e: Exception) {
                try {
                    callback.onError(requestId, "STREAM_ERROR", e.message ?: "Stream error")
                } catch (_: Exception) {}
                cleanupSession(requestId)
            }
        }

        session.job = job
    }

    private suspend fun executeStream(
        requestId: String,
        request: ChatCompletionRequest,
        apiKeyConfig: ApiKeyConfig,
        session: StreamSession
    ) = withContext(Dispatchers.IO) {
        val requestBody = buildStreamRequestBody(request, apiKeyConfig)
        val httpRequest = Request.Builder()
            .url(ChatCompletionUrl.normalize(apiKeyConfig.apiBaseUrl))
            .addHeader("Authorization", "Bearer ${apiKeyConfig.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        val call = okHttpClient.newCall(httpRequest)
        session.httpCall = call

        val response = call.execute()

        if (!response.isSuccessful) {
            val errorBody = response.body?.string()
            session.callback.onError(requestId, "UPSTREAM_ERROR", errorBody ?: "HTTP ${response.code}")
            cleanupSession(requestId)
            return@withContext
        }

        val source = response.body?.source() ?: run {
            session.callback.onError(requestId, "EMPTY_RESPONSE", "Empty response body")
            cleanupSession(requestId)
            return@withContext
        }

        source.use { bufferedSource ->
            while (!bufferedSource.exhausted() && currentCoroutineContext().isActive) {
                val line = bufferedSource.readUtf8Line() ?: break

                if (currentCoroutineContext().isActive) {
                    try {
                        session.callback.onChunk(requestId, "$line\n")
                    } catch (e: Exception) {
                        cleanupSession(requestId)
                        return@withContext
                    }
                } else {
                    break
                }
                yield()
            }
        }

        if (currentCoroutineContext().isActive) {
            try {
                session.callback.onComplete(requestId)
            } catch (_: Exception) {}
        }

        cleanupSession(requestId)
    }

    private fun buildStreamRequestBody(
        request: ChatCompletionRequest,
        apiKeyConfig: ApiKeyConfig
    ) = com.google.gson.Gson().toJson(
        mapOf<String, Any>(
            "model" to request.model.ifEmpty { apiKeyConfig.defaultModel },
            "messages" to (request.messages?.map {
                mapOf("role" to it.role, "content" to it.content)
            } ?: emptyList<Map<String, String>>()),
            "temperature" to request.temperature,
            "top_p" to request.topP,
            "max_tokens" to request.maxTokens,
            "stream" to true
        )
    ).toRequestBody("application/json; charset=utf-8".toMediaType())

    fun cancelStream(requestId: String) {
        val session = sessions.remove(requestId) ?: return
        unlinkDeath(session)
        session.httpCall?.cancel()
        session.job?.cancel()
    }

    fun cancelAllStreams() {
        sessions.keys.toList().forEach { requestId ->
            cancelStream(requestId)
        }
    }

    private fun cleanupSession(requestId: String) {
        val session = sessions.remove(requestId) ?: return
        unlinkDeath(session)
        session.job?.cancel()
    }

    private fun unlinkDeath(session: StreamSession) {
        try {
            (session.callback as android.os.IInterface).asBinder().unlinkToDeath(session.deathRecipient, 0)
        } catch (_: Exception) {}
    }
}
