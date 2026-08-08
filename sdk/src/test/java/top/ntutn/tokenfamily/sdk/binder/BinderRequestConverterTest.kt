package top.ntutn.tokenfamily.sdk.binder

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Test
import org.junit.Assert.*

class BinderRequestConverterTest {

    private val converter = BinderRequestConverter()

    @Test
    fun `test convert non-streaming request`() {
        val json = """
        {
            "model": "gpt-4",
            "messages": [
                {"role": "user", "content": "Hello"}
            ],
            "temperature": 0.7,
            "top_p": 0.9,
            "max_tokens": 100,
            "stream": false
        }
        """.trimIndent()

        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()

        val result = converter.toChatCompletionRequest(request)

        assertNotNull(result.requestId)
        assertEquals("gpt-4", result.model)
        assertEquals(false, result.stream)
        assertEquals(0.7, result.temperature, 0.001)
        assertEquals(0.9, result.topP, 0.001)
        assertEquals(100, result.maxTokens)
        assertEquals(1, result.messages?.size)
        assertEquals("user", result.messages?.get(0)?.role)
        assertEquals("Hello", result.messages?.get(0)?.content)
    }

    @Test
    fun `test convert streaming request`() {
        val json = """
        {
            "model": "gpt-3.5-turbo",
            "messages": [],
            "stream": true
        }
        """.trimIndent()

        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()

        val result = converter.toChatCompletionRequest(request)

        assertTrue(result.stream)
        assertEquals("gpt-3.5-turbo", result.model)
        assertTrue(result.messages?.isEmpty() ?: false)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test empty body throws exception`() {
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .get()
            .build()

        converter.toChatCompletionRequest(request)
    }
}
