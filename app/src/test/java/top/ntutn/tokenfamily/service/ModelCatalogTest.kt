package top.ntutn.tokenfamily.service

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import top.ntutn.tokenfamily.data.model.ApiKeyConfig

class ModelCatalogTest {

    @Test
    fun `returns an empty OpenAI model list when no configured model is available`() {
        val response = ModelCatalog.buildResponse(
            listOf(
                config(id = "empty", providerName = "Empty Provider", defaultModel = ""),
                config(id = "blank", providerName = "Blank Provider", defaultModel = "   ")
            )
        )

        assertEquals(200, response.statusCode)
        assertEquals("OK", response.statusMessage)
        assertEquals("application/json; charset=utf-8", response.contentType)
        assertEquals(listOf("Content-Type"), response.headerNames)
        assertEquals(listOf("application/json; charset=utf-8"), response.headerValues)

        val body = JsonParser.parseString(response.body).asJsonObject
        assertEquals("list", body.get("object").asString)
        assertTrue(body.getAsJsonArray("data").isEmpty)
    }

    @Test
    fun `keeps model order and first provider while excluding sensitive config fields`() {
        val configs = listOf(
            config(id = "first", providerName = "Provider A", defaultModel = "model-a"),
            config(id = "duplicate", providerName = "Provider B", defaultModel = "model-a"),
            config(id = "spaced", providerName = "Provider S", defaultModel = " model-a "),
            config(id = "second", providerName = "Provider C", defaultModel = "model-b")
        )

        val response = ModelCatalog.buildResponse(configs)
        val bodyText = response.body
        val models = JsonParser.parseString(bodyText)
            .asJsonObject
            .getAsJsonArray("data")

        assertEquals(3, models.size())
        assertEquals("model-a", models[0].asJsonObject.get("id").asString)
        assertEquals("Provider A", models[0].asJsonObject.get("owned_by").asString)
        assertEquals("model", models[0].asJsonObject.get("object").asString)
        assertEquals(0, models[0].asJsonObject.get("created").asInt)
        assertEquals(setOf("id", "object", "created", "owned_by"), models[0].asJsonObject.keySet())
        assertEquals(" model-a ", models[1].asJsonObject.get("id").asString)
        assertEquals("Provider S", models[1].asJsonObject.get("owned_by").asString)
        assertEquals("model-b", models[2].asJsonObject.get("id").asString)
        assertEquals("Provider C", models[2].asJsonObject.get("owned_by").asString)

        assertFalse(bodyText.contains("secret-first"))
        assertFalse(bodyText.contains("https://first.example.com/v1"))
        assertFalse(bodyText.contains("alias-first"))
        assertFalse(bodyText.contains("duplicate"))
    }

    private fun config(
        id: String,
        providerName: String,
        defaultModel: String
    ) = ApiKeyConfig(
        id = id,
        providerName = providerName,
        alias = "alias-$id",
        apiKey = "secret-$id",
        apiBaseUrl = "https://$id.example.com/v1",
        defaultModel = defaultModel
    )
}
