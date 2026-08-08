package top.ntutn.tokenfamily.sdk.binder

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okio.BufferedSource
import okio.ByteString.Companion.encodeUtf8
import okio.Source
import okio.buffer
import okio.source
import top.ntutn.tokenfamily.sdk.exception.StreamCancelledException
import java.io.IOException
import java.io.InterruptedIOException

class StreamResponseWrapper(
    private val requestId: String,
    private val serviceConnector: ServiceConnector
) {

    private val channel = Channel<StreamEvent>(Channel.UNLIMITED)

    private sealed class StreamEvent {
        data class Data(val chunk: String) : StreamEvent()
        data class Error(val code: String, val message: String) : StreamEvent()
        object Complete : StreamEvent()
    }

    fun onChunk(chunk: String) {
        channel.trySend(StreamEvent.Data(chunk))
    }

    fun onComplete() {
        channel.trySend(StreamEvent.Complete)
        channel.close()
    }

    fun onError(errorCode: String, errorMessage: String) {
        channel.trySend(StreamEvent.Error(errorCode, errorMessage))
        channel.close()
    }

    fun createResponseBody(): ResponseBody {
        return object : ResponseBody() {

            override fun contentType() =
                "text/event-stream".toMediaType()

            override fun contentLength() = -1L

            override fun source(): BufferedSource {
                val pipeSource = PipeSource(channel, requestId, serviceConnector)
                return pipeSource.source().buffer()
            }
        }
    }

    fun cancel() {
        channel.close(StreamCancelledException())
        try {
            serviceConnector.getService().cancelStream(requestId)
        } catch (_: Exception) {}
    }

    private class PipeSource(
        private val channel: Channel<StreamEvent>,
        private val requestId: String,
        private val serviceConnector: ServiceConnector
    ) {
        fun source(): okio.Source {
            return object : okio.Source {

                private var currentData: ByteArray? = null
                private var currentPos = 0
                private var closed = false

                override fun read(sink: okio.Buffer, byteCount: Long): Long {
                    if (closed) return -1L

                    if (currentData == null || currentPos >= currentData!!.size) {
                        currentData = nextChunk() ?: return -1L
                        currentPos = 0
                    }

                    val remaining = currentData!!.size - currentPos
                    val toWrite = minOf(byteCount.toInt(), remaining).toLong()

                    if (toWrite <= 0) return -1L

                    sink.write(currentData!!, currentPos, toWrite.toInt())
                    currentPos += toWrite.toInt()
                    return toWrite
                }

                override fun timeout() = okio.Timeout.NONE

                override fun close() {
                    if (!closed) {
                        closed = true
                        cancelStream()
                    }
                }

                private fun nextChunk(): ByteArray? {
                    val event = try {
                        kotlinx.coroutines.runBlocking {
                            channel.receive()
                        }
                    } catch (e: Exception) {
                        return null
                    }

                    return when (event) {
                        is StreamEvent.Data -> event.chunk.encodeUtf8().toByteArray()
                        is StreamEvent.Error -> {
                            val errorJson = """{"error":{"code":"${event.code}","message":"${event.message}"}}"""
                            "data: $errorJson\n\n".encodeUtf8().toByteArray()
                        }
                        is StreamEvent.Complete -> {
                            closed = true
                            "data: [DONE]\n\n".encodeUtf8().toByteArray()
                        }
                    }
                }

                private fun cancelStream() {
                    try {
                        serviceConnector.getService().cancelStream(requestId)
                    } catch (_: Exception) {}
                }
            }
        }
    }
}
