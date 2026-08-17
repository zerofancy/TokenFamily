package top.ntutn.tokenfamily.demo.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ModelListResponseParserTest {

    @Test
    fun `parses model ids in response order`() {
        val body = """
            {
              "object": "list",
              "data": [
                {"id": "gpt-4o-mini", "object": "model", "created": 0, "owned_by": "OpenAI"},
                {"id": "claude-3-5-sonnet", "object": "model", "created": 0, "owned_by": "Anthropic"}
              ]
            }
        """.trimIndent()

        assertEquals(
            listOf("gpt-4o-mini", "claude-3-5-sonnet"),
            parseModelListResponse(statusCode = 200, body = body)
        )
    }

    @Test
    fun `parses an empty model list`() {
        assertEquals(
            emptyList<String>(),
            parseModelListResponse(statusCode = 200, body = """{"object":"list","data":[]}""")
        )
    }

    @Test
    fun `uses OpenAI error message for unsuccessful response`() {
        val exception = assertThrows(ModelListResponseException::class.java) {
            parseModelListResponse(
                statusCode = 403,
                body = """{"error":{"message":"应用未授权"}}"""
            )
        }

        assertEquals("应用未授权", exception.message)
    }

    @Test
    fun `rejects malformed successful response`() {
        val exception = assertThrows(ModelListResponseException::class.java) {
            parseModelListResponse(statusCode = 200, body = """{"object":"list","data":{}}""")
        }

        assertEquals("模型列表响应的 data 字段必须是数组", exception.message)
    }
}
