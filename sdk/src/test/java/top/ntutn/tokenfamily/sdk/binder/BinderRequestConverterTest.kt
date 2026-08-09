package top.ntutn.tokenfamily.sdk.binder

import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import top.ntutn.tokenfamily.sdk.exception.PayloadTooLargeException

class BinderRequestConverterTest {

    private val converter = BinderRequestConverter()

    @Test
    fun `preserves agent request JSON and filters sensitive headers`() {
        val json = """
            {
              "model":"agent-model",
              "messages":[
                {"role":"user","content":[{"type":"text","text":"write"}]},
                {"role":"tool","tool_call_id":"call_1","content":"done"}
              ],
              "tools":[{"type":"function","function":{"name":"write_chapter","parameters":{"type":"object"}}}],
              "tool_choice":"auto",
              "response_format":{"type":"json_object"},
              "stream_options":{"include_usage":true},
              "provider_extension":{"enabled":true},
              "stream":true
            }
        """.trimIndent()
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .header("Authorization", "Bearer must-not-leak")
            .header("Cookie", "secret=1")
            .header("OpenAI-Project", "project-1")
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()

        val converted = converter.toChatCompletionRequest(request)

        assertTrue(converted.isStream)
        assertEquals(
            JsonParser.parseString(json),
            JsonParser.parseString(converted.binderRequest.bodyJson)
        )
        assertEquals(listOf("OpenAI-Project"), converted.binderRequest.headerNames)
        assertEquals(listOf("project-1"), converted.binderRequest.headerValues)
    }

    @Test
    fun `defaults stream to false without changing body`() {
        val json = """{"model":"gpt-4","messages":[],"unknown":42}"""
        val request = Request.Builder()
            .url("https://api.openai.com/chat/completions")
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()

        val converted = converter.toChatCompletionRequest(request)

        assertFalse(converted.isStream)
        assertEquals(json, converted.binderRequest.bodyJson)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects non object JSON`() {
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .post("[]".toRequestBody("application/json".toMediaType()))
            .build()
        converter.toChatCompletionRequest(request)
    }

    @Test(expected = PayloadTooLargeException::class)
    fun `rejects payload above Binder safety limit`() {
        val json = """{"messages":[{"role":"user","content":"${"x".repeat(BinderRequestConverter.MAX_BINDER_BODY_BYTES)}"}]}"""
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()
        converter.toChatCompletionRequest(request)
    }
}
