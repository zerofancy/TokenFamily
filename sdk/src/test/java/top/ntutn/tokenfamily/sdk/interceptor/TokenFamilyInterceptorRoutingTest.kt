package top.ntutn.tokenfamily.sdk.interceptor

import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenFamilyInterceptorRoutingTest {

    @Test
    fun `intercepts both supported models paths`() {
        assertTrue(isModelsListRequest(request("https://example.com/v1/models")))
        assertTrue(isModelsListRequest(request("https://example.com/models")))
    }

    @Test
    fun `ignores query parameters when matching models path`() {
        assertTrue(
            isModelsListRequest(
                request("https://example.com/v1/models?limit=10&after=model-1")
            )
        )
    }

    @Test
    fun `does not intercept non GET models requests`() {
        val postRequest = Request.Builder()
            .url("https://example.com/v1/models")
            .post("".toRequestBody())
            .build()

        assertFalse(isModelsListRequest(postRequest))
    }

    @Test
    fun `does not intercept model detail or unrelated paths`() {
        assertFalse(isModelsListRequest(request("https://example.com/v1/models/model-1")))
        assertFalse(isModelsListRequest(request("https://example.com/v1/chat/completions")))
        assertFalse(isModelsListRequest(request("https://example.com/model-catalog")))
    }

    private fun request(url: String): Request = Request.Builder()
        .url(url)
        .get()
        .build()
}
