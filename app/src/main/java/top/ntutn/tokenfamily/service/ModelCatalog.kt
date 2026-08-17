package top.ntutn.tokenfamily.service

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import top.ntutn.tokenfamily.aidl.ChatCompletionResponse
import top.ntutn.tokenfamily.data.model.ApiKeyConfig

internal object ModelCatalog {
    private const val JSON_CONTENT_TYPE = "application/json; charset=utf-8"

    fun buildResponse(configs: List<ApiKeyConfig>): ChatCompletionResponse {
        val seenModels = mutableSetOf<String>()
        val models = JsonArray()

        configs.forEach { config ->
            val model = config.defaultModel
            if (model.isNotBlank() && seenModels.add(model)) {
                models.add(
                    JsonObject().apply {
                        addProperty("id", model)
                        addProperty("object", "model")
                        addProperty("created", 0)
                        addProperty("owned_by", config.providerName)
                    }
                )
            }
        }

        val body = JsonObject().apply {
            addProperty("object", "list")
            add("data", models)
        }.toString()

        return ChatCompletionResponse().apply {
            statusCode = 200
            statusMessage = "OK"
            contentType = JSON_CONTENT_TYPE
            this.body = body
            headerNames = arrayListOf("Content-Type")
            headerValues = arrayListOf(JSON_CONTENT_TYPE)
        }
    }
}
