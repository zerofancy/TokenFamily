package top.ntutn.tokenfamily.demo.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import top.ntutn.tokenfamily.demo.model.parseModelListResponse
import top.ntutn.tokenfamily.sdk.TokenFamily
import top.ntutn.tokenfamily.sdk.exception.TokenFamilyException
import top.ntutn.tokenfamily.sdk.interceptor.TokenFamilyInterceptor

data class ChatMessage(val role: String, val content: String, val isStreaming: Boolean = false)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoApp() {
    MaterialTheme {
        val scope = rememberCoroutineScope()
        val focusManager = LocalFocusManager.current
        val listState = rememberLazyListState()

        var input by remember { mutableStateOf("") }
        var modelName by remember { mutableStateOf("") }
        var availableModels by remember { mutableStateOf<List<String>>(emptyList()) }
        var isModelListLoading by remember { mutableStateOf(false) }
        var modelListStatusText by remember { mutableStateOf("正在加载本地可用模型...") }
        var isModelMenuExpanded by remember { mutableStateOf(false) }
        var isLoading by remember { mutableStateOf(false) }
        var isStreamMode by remember { mutableStateOf(false) }
        var statusText by remember { mutableStateOf("就绪") }
        var messages by remember { mutableStateOf(listOf<ChatMessage>()) }

        val interceptor = remember {
            val connector = TokenFamily.getServiceConnector()
            TokenFamilyInterceptor(connector)
        }

        val okHttpClient = remember {
            okhttp3.OkHttpClient.Builder()
                .addInterceptor(interceptor)
                .build()
        }

        val filteredModels = remember(modelName, availableModels) {
            availableModels.filter { model ->
                modelName.isBlank() || model.contains(modelName, ignoreCase = true)
            }
        }

        suspend fun refreshModels() {
            refreshAvailableModels(
                loadModels = { loadAvailableModels(okHttpClient) },
                onLoadingChange = { isModelListLoading = it },
                onModelsLoaded = { availableModels = it },
                onStatusChange = { modelListStatusText = it }
            )
        }

        LaunchedEffect(okHttpClient) {
            refreshModels()
        }

        LaunchedEffect(messages.size) {
            if (messages.isNotEmpty()) {
                listState.animateScrollToItem(messages.size - 1)
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("词元芯核 SDK Demo") },
                    actions = {
                        Text(
                            if (isStreamMode) "流式" else "非流式",
                            modifier = Modifier.padding(end = 8.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Switch(
                            checked = isStreamMode,
                            onCheckedChange = { isStreamMode = it }
                        )
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    if (messages.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "词元芯核 SDK 演示",
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "输入消息测试 SDK 接入\n词元芯核 会通过 Binder 转发请求",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        "SDK 状态: ${connectorStatusText()}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    items(messages) { msg ->
                        MessageBubble(msg)
                    }
                }

                if (isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                Text(
                    text = statusText,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = modelName,
                            onValueChange = { value ->
                                modelName = value
                                isModelMenuExpanded = availableModels.any { model ->
                                    value.isBlank() || model.contains(value, ignoreCase = true)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("模型名 (留空使用默认)") },
                            enabled = !isLoading,
                            singleLine = true,
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        isModelMenuExpanded =
                                            !isModelMenuExpanded && availableModels.isNotEmpty()
                                    },
                                    enabled = !isLoading && availableModels.isNotEmpty()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "显示模型建议"
                                    )
                                }
                            }
                        )

                        DropdownMenu(
                            expanded = isModelMenuExpanded && filteredModels.isNotEmpty(),
                            onDismissRequest = { isModelMenuExpanded = false }
                        ) {
                            filteredModels.forEach { model ->
                                DropdownMenuItem(
                                    text = { Text(model) },
                                    onClick = {
                                        modelName = model
                                        isModelMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            isModelMenuExpanded = false
                            scope.launch { refreshModels() }
                        },
                        enabled = !isModelListLoading
                    ) {
                        if (isModelListLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("刷新")
                        }
                    }
                }

                Text(
                    text = modelListStatusText,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (modelListStatusText.startsWith("模型列表加载失败")) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )

                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("输入消息...") },
                    enabled = !isLoading,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (input.isNotBlank() && !isLoading) {
                                val userMessage = input.trim()
                                input = ""
                                focusManager.clearFocus()

                                scope.launch {
                                    sendMessage(
                                        okHttpClient = okHttpClient,
                                        isStreamMode = isStreamMode,
                                        modelName = modelName,
                                        userMessage = userMessage,
                                        messages = messages,
                                        onMessagesUpdate = { messages = it },
                                        onLoadingChange = { isLoading = it },
                                        onStatusChange = { statusText = it },
                                        onError = {
                                            statusText = "错误: ${it.message}"
                                            isLoading = false
                                        }
                                    )
                                }
                            }
                        }
                    ),
                    minLines = 2,
                    maxLines = 4
                )
            }
        }
    }
}

internal suspend fun refreshAvailableModels(
    loadModels: suspend () -> List<String>,
    onLoadingChange: (Boolean) -> Unit,
    onModelsLoaded: (List<String>) -> Unit,
    onStatusChange: (String) -> Unit
) {
    onLoadingChange(true)
    onStatusChange("正在加载本地可用模型...")
    try {
        val models = loadModels()
        onModelsLoaded(models)
        onStatusChange(
            if (models.isEmpty()) {
                "未找到已配置模型，仍可手动输入或留空"
            } else {
                "已加载 ${models.size} 个本地模型"
            }
        )
    } catch (exception: TokenFamilyException) {
        onStatusChange("模型列表加载失败: ${friendlyTokenFamilyError(exception)}")
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        onStatusChange("模型列表加载失败: ${exception.message ?: "未知错误"}")
    } finally {
        onLoadingChange(false)
    }
}

private suspend fun loadAvailableModels(
    okHttpClient: okhttp3.OkHttpClient
): List<String> = withContext(Dispatchers.IO) {
    val request = okhttp3.Request.Builder()
        .url("https://api.openai.com/v1/models")
        .get()
        .build()

    okHttpClient.newCall(request).execute().use { response ->
        parseModelListResponse(
            statusCode = response.code,
            body = response.body?.string().orEmpty()
        )
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage) {
    val isUser = msg.role == "user"
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val bgColor = if (isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = bgColor,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = if (isUser) "你" else "AI",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = msg.content,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (msg.isStreaming) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun connectorStatusText(): String {
    val connector = remember { TokenFamily.getServiceConnector() }
    val isConnected by connector.isConnected.collectAsState()
    return if (isConnected) "已连接" else "未连接"
}

private suspend fun sendMessage(
    okHttpClient: okhttp3.OkHttpClient,
    isStreamMode: Boolean,
    modelName: String,
    userMessage: String,
    messages: List<ChatMessage>,
    onMessagesUpdate: (List<ChatMessage>) -> Unit,
    onLoadingChange: (Boolean) -> Unit,
    onStatusChange: (String) -> Unit,
    onError: (Exception) -> Unit
) {
    onLoadingChange(true)
    if (!isStreamMode) {
        onStatusChange("发送中...")
    } else {
        onStatusChange("流式接收中...")
    }

    val updatedMessages = messages + ChatMessage("user", userMessage)
    onMessagesUpdate(updatedMessages)

    val requestBody = buildChatRequestBody(
        modelName = modelName,
        messages = updatedMessages,
        isStreamMode = isStreamMode
    )

    val request = okhttp3.Request.Builder()
        .url("https://api.openai.com/v1/chat/completions")
        .post(requestBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
        .build()

    try {
        if (isStreamMode) {
            withContext(Dispatchers.IO) {
                handleStreamResponse(
                    okHttpClient = okHttpClient,
                    request = request,
                    messages = updatedMessages,
                    onMessagesUpdate = onMessagesUpdate,
                    onLoadingChange = onLoadingChange,
                    onStatusChange = onStatusChange
                )
            }
        } else {
            withContext(Dispatchers.IO) {
                handleNonStreamResponse(
                    okHttpClient = okHttpClient,
                    request = request,
                    messages = updatedMessages,
                    onMessagesUpdate = onMessagesUpdate,
                    onLoadingChange = onLoadingChange,
                    onStatusChange = onStatusChange
                )
            }
        }
    } catch (e: TokenFamilyException) {
        val friendlyError = friendlyTokenFamilyError(e)
        onError(TokenFamilyException(e.errorCode, friendlyError))
    } catch (e: Exception) {
        onError(e)
    }
}

internal fun buildChatRequestBody(
    modelName: String,
    messages: List<ChatMessage>,
    isStreamMode: Boolean
): String {
    val jsonMessages = buildString {
        append("[")
        messages.forEachIndexed { i, msg ->
            if (i > 0) append(",")
            append("""{"role":"${msg.role}","content":"${escapeJson(msg.content)}"}""")
        }
        append("]")
    }

    val requestBody = """
        {
            "model": "${escapeJson(modelName)}",
            "messages": $jsonMessages,
            "stream": $isStreamMode
        }
    """.trimIndent()
    return requestBody
}

private fun friendlyTokenFamilyError(exception: TokenFamilyException): String =
    when (exception.errorCode) {
        "KEY_MISSING" -> "请先在词元芯核 App 中配置 API 密钥"
        "UNAUTHORIZED" -> "应用未授权，请在词元芯核 App 中授权"
        "BIND_FAILED" -> "无法连接词元芯核服务"
        "NOT_INSTALLED" -> "词元芯核 App 未安装"
        else -> exception.message ?: "未知错误"
    }

private suspend fun handleNonStreamResponse(
    okHttpClient: okhttp3.OkHttpClient,
    request: okhttp3.Request,
    messages: List<ChatMessage>,
    onMessagesUpdate: (List<ChatMessage>) -> Unit,
    onLoadingChange: (Boolean) -> Unit,
    onStatusChange: (String) -> Unit
) {
    val content = okHttpClient.newCall(request).execute().use { response ->
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) throw java.io.IOException(extractErrorMessage(body, response.code))
        extractContent(body)
    }

    onMessagesUpdate(messages + ChatMessage("assistant", content))
    onStatusChange("完成")
    onLoadingChange(false)
}

private suspend fun handleStreamResponse(
    okHttpClient: okhttp3.OkHttpClient,
    request: okhttp3.Request,
    messages: List<ChatMessage>,
    onMessagesUpdate: (List<ChatMessage>) -> Unit,
    onLoadingChange: (Boolean) -> Unit,
    onStatusChange: (String) -> Unit
) {
    val response = okHttpClient.newCall(request).execute()
    if (!response.isSuccessful) {
        val body = response.use { it.body?.string().orEmpty() }
        throw java.io.IOException(extractErrorMessage(body, response.code))
    }
    val source = response.body?.source() ?: run {
        response.close()
        onLoadingChange(false)
        return
    }

    var streamingContent = ""
    var streamingChunk = ""
    val streamingMsg = ChatMessage("assistant", "", isStreaming = true)
    onMessagesUpdate(messages + streamingMsg)

    source.use { bufferedSource ->
        while (!bufferedSource.exhausted()) {
            val line = bufferedSource.readUtf8Line() ?: break
            if (line.startsWith("data: ")) {
                val data = line.removePrefix("data: ").trim()
                if (data == "[DONE]") break

                val content = extractStreamContent(data)
                if (content.isNotEmpty()) {
                    streamingContent += content
                    streamingChunk += content
                    if (streamingChunk.length >= 8) {
                        onMessagesUpdate(messages + ChatMessage("assistant", streamingContent, isStreaming = true))
                        streamingChunk = ""
                    }
                }
            }
        }
    }

    onMessagesUpdate(messages + ChatMessage("assistant", streamingContent))
    onStatusChange("完成")
    onLoadingChange(false)
    response.close()
}

private fun extractErrorMessage(body: String, statusCode: Int): String = try {
    val error = com.google.gson.JsonParser.parseString(body).asJsonObject.getAsJsonObject("error")
    error?.get("message")?.asString ?: "HTTP $statusCode"
} catch (_: Exception) {
    body.ifBlank { "HTTP $statusCode" }
}

private fun extractContent(json: String): String {
    return try {
        val gson = com.google.gson.Gson()
        val obj = gson.fromJson(json, com.google.gson.JsonObject::class.java)
        obj.getAsJsonArray("choices")
            ?.firstOrNull()
            ?.asJsonObject
            ?.getAsJsonObject("message")
            ?.get("content")
            ?.asString ?: ""
    } catch (e: Exception) {
        ""
    }
}

private fun extractStreamContent(json: String): String {
    return try {
        val gson = com.google.gson.Gson()
        val obj = gson.fromJson(json, com.google.gson.JsonObject::class.java)
        obj.getAsJsonArray("choices")
            ?.firstOrNull()
            ?.asJsonObject
            ?.getAsJsonObject("delta")
            ?.get("content")
            ?.asString ?: ""
    } catch (e: Exception) {
        ""
    }
}

private fun escapeJson(s: String): String {
    return s.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}
