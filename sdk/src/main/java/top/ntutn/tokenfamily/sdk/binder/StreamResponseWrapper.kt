package top.ntutn.tokenfamily.sdk.binder

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType
import okhttp3.ResponseBody
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
import top.ntutn.tokenfamily.sdk.exception.TokenFamilyException
import top.ntutn.tokenfamily.sdk.exception.StreamCancelledException
import java.util.concurrent.atomic.AtomicBoolean

class StreamResponseWrapper(
    private val cancelStream: () -> Unit,
    private val isCallCancelled: () -> Boolean = { false },
    private val mediaType: MediaType?
) {

    private sealed interface StreamEvent {
        data class Data(val chunk: String) : StreamEvent
        data class Error(val code: String, val message: String) : StreamEvent
        data object Complete : StreamEvent
    }

    private val channel = Channel<StreamEvent>(Channel.UNLIMITED)
    private val terminal = AtomicBoolean(false)

    fun onChunk(chunk: String) {
        if (!terminal.get()) channel.trySend(StreamEvent.Data(chunk))
    }

    fun onComplete() {
        if (terminal.compareAndSet(false, true)) {
            channel.trySend(StreamEvent.Complete)
            channel.close()
        }
    }

    fun onError(errorCode: String, errorMessage: String) {
        if (terminal.compareAndSet(false, true)) {
            channel.trySend(StreamEvent.Error(errorCode, errorMessage))
            channel.close()
        }
    }

    fun createResponseBody(): ResponseBody = object : ResponseBody() {
        override fun contentType(): MediaType? = mediaType

        override fun contentLength(): Long = -1L

        override fun source(): BufferedSource = PipeSource().buffer()
    }

    private inner class PipeSource : Source {
        private var currentData: ByteArray? = null
        private var currentPosition = 0
        private var closed = false

        override fun read(sink: okio.Buffer, byteCount: Long): Long {
            require(byteCount >= 0) { "byteCount < 0: $byteCount" }
            if (closed) throw IllegalStateException("closed")
            if (byteCount == 0L) return 0L
            if (isCallCancelled()) {
                terminal.set(true)
                try {
                    cancelStream()
                } catch (_: Exception) {
                }
                throw StreamCancelledException()
            }

            if (currentData == null || currentPosition >= currentData!!.size) {
                currentData = nextChunk() ?: return -1L
                currentPosition = 0
            }

            val remaining = currentData!!.size - currentPosition
            val toWrite = minOf(byteCount, remaining.toLong()).toInt()
            sink.write(currentData!!, currentPosition, toWrite)
            currentPosition += toWrite
            return toWrite.toLong()
        }

        override fun timeout(): Timeout = Timeout.NONE

        override fun close() {
            if (closed) return
            closed = true
            channel.cancel()
            if (!terminal.get()) {
                try {
                    cancelStream()
                } catch (_: Exception) {
                }
            }
        }

        private fun nextChunk(): ByteArray? {
            var event: StreamEvent? = null
            while (event == null) {
                if (isCallCancelled()) {
                    terminal.set(true)
                    try {
                        cancelStream()
                    } catch (_: Exception) {
                    }
                    throw StreamCancelledException()
                }
                val result = kotlinx.coroutines.runBlocking {
                    withTimeoutOrNull(CANCEL_POLL_INTERVAL_MS) {
                        channel.receiveCatching()
                    }
                } ?: continue
                if (result.isClosed) return null
                event = result.getOrThrow()
            }

            return when (event) {
                is StreamEvent.Data -> event.chunk.toByteArray(Charsets.UTF_8)
                is StreamEvent.Error -> throw TokenFamilyException(event.code, event.message)
                StreamEvent.Complete -> null
            }
        }
    }

    companion object {
        private const val CANCEL_POLL_INTERVAL_MS = 250L
    }
}
