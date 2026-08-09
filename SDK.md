# TokenFamily SDK 接入文档

## 1. 能力与要求

TokenFamily SDK 通过 OkHttp Interceptor 截获 Chat Completions 请求，经 Binder 交给词元芯核 App，并使用 App 中托管的 API Key 请求上游服务。

| 项目 | 要求 |
|---|---|
| Android 运行版本 | API 26+ |
| 消费方 compileSdk | 30+ |
| SDK | `top.ntutn:tokenfamily-sdk:0.2.0` |
| 词元芯核 App | 必须安装与 SDK 协议匹配的 0.2.x 版本 |

兼容范围是 OpenAI 风格 Chat Completions 的 JSON 请求体和响应体透传，不代表每个上游供应商或模型都支持相同功能。

## 2. 安装与初始化

```kotlin
dependencies {
    implementation("top.ntutn:tokenfamily-sdk:0.2.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
```

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        TokenFamily.init(this)
    }
}

val client = OkHttpClient.Builder()
    .addInterceptor(
        TokenFamilyInterceptor(TokenFamily.getServiceConnector())
    )
    .build()
```

SDK 只拦截路径以 `/chat/completions` 结尾的请求，其他请求继续使用原 OkHttp 网络链路。

## 3. 普通请求

所有同步网络请求都必须在后台线程执行。主线程触发连接会立即抛出 `MAIN_THREAD_CALL`，避免阻塞等待主线程上的 ServiceConnection 回调。

```kotlin
val body = """
{
  "model": "",
  "messages": [{"role": "user", "content": "你好"}],
  "response_format": {"type": "json_object"},
  "stream": false
}
""".trimIndent()

val request = Request.Builder()
    .url("https://api.openai.com/v1/chat/completions")
    .post(body.toRequestBody("application/json".toMediaType()))
    .build()

val response = withContext(Dispatchers.IO) {
    client.newCall(request).execute()
}

response.use {
    val responseBody = it.body?.string().orEmpty()
    if (!it.isSuccessful) {
        // 按正常 HTTP 错误处理状态码和 JSON error body。
    }
}
```

`model` 为空时使用词元芯核密钥配置中的默认模型。非空时用于选择匹配配置并原样发送。

## 4. Tool calling

SDK 不解析或重建工具定义，完整 JSON Schema 会原样送达上游，返回的 `tool_calls` 也不会丢失。

```kotlin
val body = """
{
  "model": "agent-model",
  "messages": [{"role": "user", "content": "创建第一章"}],
  "tools": [{
    "type": "function",
    "function": {
      "name": "write_chapter",
      "description": "创建或更新章节",
      "parameters": {
        "type": "object",
        "properties": {
          "title": {"type": "string"},
          "content": {"type": "string"}
        },
        "required": ["title", "content"]
      }
    }
  }],
  "tool_choice": "auto",
  "stream": false
}
""".trimIndent()
```

是否真正产生原生 tool call 仍取决于所配置的供应商与模型。

## 5. SSE 流式请求

设置 `"stream": true`。SDK 会先等待上游响应头：

- 上游返回非 2xx 时，`execute()` 返回真实状态码和有限 JSON 错误体，不会伪装成 200 SSE。
- 上游成功后，响应体保留 SSE 的 `data:`、注释行、空行和 `[DONE]`。
- 流开始后的网络或 Binder 故障会在读取 ResponseBody 时抛出带 `errorCode` 的 `TokenFamilyException`。

```kotlin
withContext(Dispatchers.IO) {
    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            val error = response.body?.string().orEmpty()
            throw IOException("HTTP ${response.code}: $error")
        }

        response.body?.source()?.use { source ->
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (line.startsWith("data:")) {
                    val data = line.removePrefix("data:").trimStart()
                    if (data == "[DONE]") break
                    // 解析 chunk JSON
                }
            }
        }
    }
}
```

关闭 ResponseBody 会取消对应上游流。应始终使用 `use` 或显式 `close()`；保留 `Call` 后也可在外部调用 `cancel()`。

## 6. 错误语义

词元芯核服务能生成 HTTP 响应时，采用统一 JSON：

```json
{
  "error": {
    "type": "tokenfamily_error",
    "code": "KEY_MISSING",
    "message": "No API key configured"
  }
}
```

| HTTP 状态 | TokenFamily code | 含义 |
|---|---|---|
| 400 | `INVALID_REQUEST` | 请求体不是合法 JSON 对象 |
| 403 | `UNAUTHORIZED` | 调用应用无法授权 |
| 502 | `NETWORK_ERROR` | 词元芯核无法连接上游 |
| 502 | `EMPTY_RESPONSE` | 上游成功但没有响应体 |
| 502 | `PAYLOAD_TOO_LARGE` | 非流式响应超过 Binder 安全上限 |
| 503 | `KEY_MISSING` | 没有可用 API Key 配置 |
| 503 | `SERVICE_ERROR` | 词元芯核服务尚不可用 |

上游返回的 4xx/5xx 不会被改写，调用方直接收到上游状态、Content-Type 和错误体。

无法形成 HTTP 响应的安装、绑定和 IPC 故障会抛出 `TokenFamilyException : IOException`，常见 `errorCode` 包括：

- `NOT_INSTALLED`
- `BIND_FAILED`
- `NOT_CONNECTED`
- `MAIN_THREAD_CALL`
- `IPC_DISCONNECTED`
- `IPC_ERROR`
- `STREAM_ERROR`
- `PAYLOAD_TOO_LARGE`

## 7. 请求头与安全

SDK 使用词元芯核中托管的密钥覆盖 `Authorization`，并过滤以下请求头：

- `Authorization`、`Proxy-Authorization`、`Cookie`
- `Host`、`Content-Length`、`Content-Type`
- Connection、Transfer-Encoding 等 hop-by-hop 头

其他端到端头（例如 `OpenAI-Project`、`Idempotency-Key`）会转发。响应中的 hop-by-hop 头、`Content-Length` 和 `Set-Cookie` 不会跨 Binder 返回。

## 8. Binder 负载限制

0.2.x 尚未使用文件描述符传输大请求：

- 请求 JSON 与透传请求头合计上限为 512 KiB，超出时抛出 `PAYLOAD_TOO_LARGE`。
- 非流式响应体上限为 512 KiB，超出时返回 502 `PAYLOAD_TOO_LARGE`。
- SSE 会分块跨 Binder 传输，不受单行 JSON 大小直接影响。

需要发送超长小说上下文时，应在调用前裁剪、摘要或检索相关片段。
