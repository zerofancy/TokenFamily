package top.ntutn.tokenfamily.demo.model

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import java.io.IOException

internal class ModelListResponseException(
    message: String,
    cause: Throwable? = null
) : IOException(message, cause)

/**
 * Parses the OpenAI-compatible models response without exposing an additional public DTO.
 */
internal fun parseModelListResponse(statusCode: Int, body: String): List<String> {
    if (statusCode !in 200..299) {
        throw ModelListResponseException(extractApiErrorMessage(body) ?: "HTTP $statusCode")
    }

    val root = try {
        JsonParser.parseString(body)
    } catch (exception: Exception) {
        throw ModelListResponseException("模型列表响应不是有效 JSON", exception)
    }

    if (!root.isJsonObject) {
        throw ModelListResponseException("模型列表响应必须是 JSON 对象")
    }

    val data = root.asJsonObject.get("data")
        ?: throw ModelListResponseException("模型列表响应缺少 data 字段")
    if (!data.isJsonArray) {
        throw ModelListResponseException("模型列表响应的 data 字段必须是数组")
    }

    return data.asJsonArray.mapIndexed { index, element ->
        parseModelId(element, index)
    }
}

private fun parseModelId(element: JsonElement, index: Int): String {
    if (!element.isJsonObject) {
        throw ModelListResponseException("模型列表第 ${index + 1} 项必须是 JSON 对象")
    }

    val idElement = element.asJsonObject.get("id")
        ?: throw ModelListResponseException("模型列表第 ${index + 1} 项缺少 id 字段")
    if (!idElement.isJsonPrimitive || !idElement.asJsonPrimitive.isString) {
        throw ModelListResponseException("模型列表第 ${index + 1} 项的 id 必须是字符串")
    }

    return idElement.asString.takeIf { it.isNotBlank() }
        ?: throw ModelListResponseException("模型列表第 ${index + 1} 项的 id 不能为空")
}

private fun extractApiErrorMessage(body: String): String? = try {
    JsonParser.parseString(body)
        .takeIf { it.isJsonObject }
        ?.asJsonObject
        ?.getAsJsonObject("error")
        ?.get("message")
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
        ?.asString
        ?.takeIf { it.isNotBlank() }
} catch (_: Exception) {
    null
}
