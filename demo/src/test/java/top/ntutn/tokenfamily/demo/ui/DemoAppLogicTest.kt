package top.ntutn.tokenfamily.demo.ui

import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class DemoAppLogicTest {

    @Test
    fun `selected model is written to chat request body`() {
        val body = buildChatRequestBody(
            modelName = "configured-\"model",
            messages = listOf(ChatMessage(role = "user", content = "hello")),
            isStreamMode = true
        )

        val json = JsonParser.parseString(body).asJsonObject
        assertEquals("configured-\"model", json.get("model").asString)
        assertTrue(json.get("stream").asBoolean)
        assertEquals("hello", json.getAsJsonArray("messages")[0].asJsonObject.get("content").asString)
    }

    @Test
    fun `blank model remains blank so service can choose default`() {
        val body = buildChatRequestBody(
            modelName = "",
            messages = emptyList(),
            isStreamMode = false
        )

        val json = JsonParser.parseString(body).asJsonObject
        assertEquals("", json.get("model").asString)
        assertEquals(false, json.get("stream").asBoolean)
    }

    @Test
    fun `failed refresh preserves existing models and current input`() = runBlocking {
        var availableModels = listOf("existing-model")
        val modelName = "typed-model"
        val loadingStates = mutableListOf<Boolean>()
        var status = ""

        refreshAvailableModels(
            loadModels = { throw IOException("network unavailable") },
            onLoadingChange = { loadingStates += it },
            onModelsLoaded = { availableModels = it },
            onStatusChange = { status = it }
        )

        assertEquals(listOf("existing-model"), availableModels)
        assertEquals("typed-model", modelName)
        assertEquals(listOf(true, false), loadingStates)
        assertTrue(status.startsWith("模型列表加载失败:"))
    }
}
