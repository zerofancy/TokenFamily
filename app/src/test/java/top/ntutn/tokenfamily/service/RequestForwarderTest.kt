package top.ntutn.tokenfamily.service

import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import top.ntutn.tokenfamily.aidl.ChatCompletionRequest
import top.ntutn.tokenfamily.data.model.ApiKeyConfig

class RequestForwarderTest {

    private lateinit var server: MockWebServer
    private lateinit var forwarder: RequestForwarder

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        forwarder = RequestForwarder(OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `forwards arbitrary JSON and only fills blank model`() = runBlocking {
        val upstreamBody = """{"id":"1","choices":[{"message":{"tool_calls":[{"id":"call_1"}]}}],"vendor":true}"""
        server.enqueue(MockResponse().setResponseCode(200).setHeader("X-Request-Id", "up-1").setBody(upstreamBody))
        val input = """{"model":"","messages":[{"role":"user","content":[{"type":"text","text":"hi"}]}],"tools":[{"type":"function"}],"tool_choice":"auto","response_format":{"type":"json_object"},"vendor_option":7}"""
        val request = aidlRequest(input, listOf("OpenAI-Project", "Authorization"), listOf("p1", "leak"))

        val response = forwarder.forward(request, config())
        val recorded = server.takeRequest()

        val sent = JsonParser.parseString(recorded.body.readUtf8()).asJsonObject
        assertEquals("default-model", sent.get("model").asString)
        assertTrue(sent.has("tools"))
        assertTrue(sent.has("tool_choice"))
        assertTrue(sent.has("response_format"))
        assertEquals(7, sent.get("vendor_option").asInt)
        assertEquals("Bearer managed-key", recorded.getHeader("Authorization"))
        assertEquals("p1", recorded.getHeader("OpenAI-Project"))
        assertFalse(recorded.headers.values("Authorization").contains("leak"))
        assertEquals(200, response.statusCode)
        assertEquals(upstreamBody, response.body)
        assertTrue(response.headerNames.contains("X-Request-Id"))
    }

    @Test
    fun `preserves upstream error status content type and body`() = runBlocking {
        val errorBody = """{"error":{"code":"insufficient_quota","message":"Insufficient Balance"}}"""
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setHeader("Content-Type", "application/problem+json")
                .setHeader("Retry-After", "30")
                .setBody(errorBody)
        )

        val response = forwarder.forward(aidlRequest("""{"model":"m","messages":[]}"""), config())

        assertEquals(429, response.statusCode)
        assertEquals("application/problem+json", response.contentType)
        assertEquals(errorBody, response.body)
        assertTrue(response.headerNames.contains("Retry-After"))
    }

    @Test
    fun `returns OpenAI style 400 for invalid JSON`() = runBlocking {
        val response = forwarder.forward(aidlRequest("not-json"), config())

        assertEquals(400, response.statusCode)
        val error = JsonParser.parseString(response.body).asJsonObject.getAsJsonObject("error")
        assertEquals("INVALID_REQUEST", error.get("code").asString)
        assertEquals("tokenfamily_error", error.get("type").asString)
        assertNull(server.takeRequest(100, java.util.concurrent.TimeUnit.MILLISECONDS))
    }

    @Test
    fun `rejects non string model as invalid request`() = runBlocking {
        val response = forwarder.forward(aidlRequest("""{"model":{"name":"m"},"messages":[]}"""), config())

        assertEquals(400, response.statusCode)
        val error = JsonParser.parseString(response.body).asJsonObject.getAsJsonObject("error")
        assertEquals("INVALID_REQUEST", error.get("code").asString)
    }

    @Test
    fun `returns bounded 502 instead of oversized Binder response`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("x".repeat(513 * 1024)))

        val response = forwarder.forward(aidlRequest("""{"model":"m","messages":[]}"""), config())

        assertEquals(502, response.statusCode)
        val error = JsonParser.parseString(response.body).asJsonObject.getAsJsonObject("error")
        assertEquals("PAYLOAD_TOO_LARGE", error.get("code").asString)
    }

    private fun aidlRequest(
        body: String,
        names: List<String> = emptyList(),
        values: List<String> = emptyList()
    ) = ChatCompletionRequest().apply {
        requestId = "request-1"
        bodyJson = body
        headerNames = ArrayList(names)
        headerValues = ArrayList(values)
    }

    private fun config() = ApiKeyConfig(
        id = "1",
        providerName = "test",
        alias = "test",
        apiKey = "managed-key",
        apiBaseUrl = server.url("/").toString(),
        defaultModel = "default-model",
        isDefault = true
    )
}
