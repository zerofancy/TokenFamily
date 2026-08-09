package top.ntutn.tokenfamily.sdk.binder

import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import top.ntutn.tokenfamily.sdk.exception.TokenFamilyException
import java.util.concurrent.atomic.AtomicBoolean

class StreamResponseWrapperTest {

    @Test
    fun `preserves SSE comments blank lines data and a single done marker`() {
        val wrapper = wrapper()
        wrapper.onChunk(": OPENROUTER PROCESSING\n")
        wrapper.onChunk("\n")
        wrapper.onChunk("data: {\"choices\":[]}\n")
        wrapper.onChunk("\n")
        wrapper.onChunk("data: [DONE]\n")
        wrapper.onChunk("\n")
        wrapper.onComplete()

        val body = wrapper.createResponseBody().string()

        assertEquals(
            ": OPENROUTER PROCESSING\n\ndata: {\"choices\":[]}\n\ndata: [DONE]\n\n",
            body
        )
        assertEquals(1, Regex("data: [DONE]", RegexOption.LITERAL).findAll(body).count())
    }

    @Test
    fun `normal completion without done marker ends at EOF`() {
        val wrapper = wrapper()
        wrapper.onChunk("data: {\"choices\":[]}\n\n")
        wrapper.onComplete()

        assertEquals("data: {\"choices\":[]}\n\n", wrapper.createResponseBody().string())
    }

    @Test
    fun `stream error is thrown while reading instead of encoded as SSE`() {
        val wrapper = wrapper()
        wrapper.onChunk("data: {\"choices\":[]}\n\n")
        wrapper.onError("STREAM_ERROR", "socket closed")
        val source = wrapper.createResponseBody().source()

        val firstChunk = "data: {\"choices\":[]}\n\n"
        assertEquals(firstChunk, source.readUtf8(firstChunk.toByteArray().size.toLong()))
        val error = try {
            source.readUtf8()
            null
        } catch (e: TokenFamilyException) {
            e
        }
        assertEquals("STREAM_ERROR", error?.errorCode)
        assertEquals("socket closed", error?.message)
    }

    @Test
    fun `closing active body cancels upstream but completed body does not`() {
        val activeCancelled = AtomicBoolean(false)
        val active = wrapper { activeCancelled.set(true) }
        active.createResponseBody().close()
        assertTrue(activeCancelled.get())

        val completedCancelled = AtomicBoolean(false)
        val completed = wrapper { completedCancelled.set(true) }
        completed.onComplete()
        completed.createResponseBody().close()
        assertFalse(completedCancelled.get())
    }

    @Test
    fun `cancelled OkHttp call cancels upstream on next read`() {
        val cancelled = AtomicBoolean(false)
        val upstreamCancelled = AtomicBoolean(false)
        val wrapper = StreamResponseWrapper(
            cancelStream = { upstreamCancelled.set(true) },
            isCallCancelled = { cancelled.get() },
            mediaType = "text/event-stream".toMediaType()
        )
        wrapper.onChunk("data: {}\n\n")
        cancelled.set(true)

        val error = try {
            wrapper.createResponseBody().source().readByte()
            null
        } catch (e: top.ntutn.tokenfamily.sdk.exception.StreamCancelledException) {
            e
        }

        assertEquals("STREAM_CANCELLED", error?.errorCode)
        assertTrue(upstreamCancelled.get())
    }

    private fun wrapper(cancel: () -> Unit = {}) = StreamResponseWrapper(
        cancelStream = cancel,
        mediaType = "text/event-stream".toMediaType()
    )
}
