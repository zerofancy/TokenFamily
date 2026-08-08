package top.ntutn.tokenfamily.service

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatCompletionUrlTest {

    @Test
    fun `OpenRouter with v1 suffix produces correct URL`() {
        val result = ChatCompletionUrl.normalize("https://openrouter.ai/api/v1")
        assertEquals("https://openrouter.ai/api/v1/chat/completions", result)
    }

    @Test
    fun `OpenAI with v1 suffix produces correct URL`() {
        val result = ChatCompletionUrl.normalize("https://api.openai.com/v1")
        assertEquals("https://api.openai.com/v1/chat/completions", result)
    }

    @Test
    fun `OpenAI without v1 produces correct URL`() {
        val result = ChatCompletionUrl.normalize("https://api.openai.com")
        assertEquals("https://api.openai.com/v1/chat/completions", result)
    }

    @Test
    fun `trailing slash is handled`() {
        val result = ChatCompletionUrl.normalize("https://openrouter.ai/api/v1/")
        assertEquals("https://openrouter.ai/api/v1/chat/completions", result)
    }

    @Test
    fun `already-complete URL is not duplicated`() {
        val result = ChatCompletionUrl.normalize("https://api.openai.com/v1/chat/completions")
        assertEquals("https://api.openai.com/v1/chat/completions", result)
    }

    @Test
    fun `URL with query parameters preserved`() {
        val result = ChatCompletionUrl.normalize("https://openrouter.ai/api/v1?foo=bar")
        assertEquals("https://openrouter.ai/api/v1/chat/completions?foo=bar", result)
    }
}
