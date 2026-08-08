# TokenFamily SDK 接入文档

## 概述

TokenFamily（词元芯核）SDK 提供一组轻量 API，让第三方 Android 应用通过 Binder IPC 调用词元芯核服务完成 AI Chat Completions 请求，无需关心 API Key 管理和网络转发细节。SDK 层完全兼容 OpenAI Chat Completions 接口格式，业务代码零修改。

## 最低要求

| 项目 | 要求 |
|------|------|
| Android 版本 | API 26+ (Android 8.0) |
| Kotlin | 2.0+ |
| 词元芯核 App | 需先安装到设备 |

## 快速接入

### 步骤 1：添加依赖

SDK 已发布到 Maven Central，坐标：`top.ntutn:tokenfamily-sdk`。

在应用模块的 `build.gradle.kts` 添加依赖：

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("top.ntutn:tokenfamily-sdk:0.1.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
```

> 若在项目内联开发（monorepo），可将依赖替换为 `implementation(project(":sdk"))`，并确认 `settings.gradle.kts` 中包含 `include(":sdk")`。

### 步骤 2：初始化

在 `Application.onCreate()` 或首个 `Activity.onCreate()` 中初始化：

```kotlin
import top.ntutn.tokenfamily.sdk.TokenFamily

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        TokenFamily.init(this)
    }
}
```

### 步骤 3：创建 OkHttpClient

```kotlin
import top.ntutn.tokenfamily.sdk.interceptor.TokenFamilyInterceptor

val interceptor = TokenFamilyInterceptor(TokenFamily.getServiceConnector())

val okHttpClient = OkHttpClient.Builder()
    .addInterceptor(interceptor)
    .build()
```

### 步骤 4：调用（与原生 OpenAI 完全一致）

```kotlin
val requestBody = """
{
    "model": "",
    "messages": [{"role": "user", "content": "Hello"}],
    "stream": false
}
""".trimIndent()

val request = Request.Builder()
    .url("https://api.openai.com/v1/chat/completions")
    .post(requestBody.toRequestBody("application/json".toMediaType()))
    .build()

val response = okHttpClient.newCall(request).execute()
println(response.body?.string())
```

## 流式调用

设置 `"stream": true` 即可自动切换为 SSE 流式模式：

```kotlin
val requestBody = """
{
    "model": "",
    "messages": [{"role": "user", "content": "写一首诗"}],
    "stream": true
}
""".trimIndent()

val request = Request.Builder()
    .url("https://api.openai.com/v1/chat/completions")
    .post(requestBody.toRequestBody("application/json".toMediaType()))
    .build()

val response = okHttpClient.newCall(request).execute()

response.body?.source()?.use { source ->
    while (!source.exhausted()) {
        val line = source.readUtf8Line() ?: break
        if (line.startsWith("data: ")) {
            val data = line.removePrefix("data: ")
            if (data == "[DONE]") break
            // 处理流式数据块
            println(data)
        }
    }
}
```

## 取消请求

```kotlin
val call = okHttpClient.newCall(request)
call.enqueue(object : Callback {
    override fun onResponse(call: Call, response: Response) { ... }
    override fun onFailure(call: Call, e: IOException) { ... }
})

// 随时取消，取消信号会透传到服务端终止上游调用
call.cancel()
```

**注意**：`call.cancel()` 必须在调用线程外的线程执行。主线程调用 `execute()` 时会阻塞，请使用 `Dispatchers.IO` 或单独线程：

```kotlin
// 在协程中调用
withContext(Dispatchers.IO) {
    val response = okHttpClient.newCall(request).execute()
}
```

## Error Code 说明

| ErrorCode | 含义 | 处理建议 |
|-----------|------|----------|
| `NOT_INSTALLED` | 词元芯核 App 未安装 | 提示用户安装词元芯核 |
| `BIND_FAILED` | 服务绑定失败 | 检查词元芯核是否正常运行 |
| `UNAUTHORIZED` | 应用未授权 | 提示用户授权或前往词元芯核审核 |
| `KEY_MISSING` | 未配置 API Key | 提示用户在词元芯核中配置密钥 |
| `NOT_CONNECTED` | 连接未建立 | 重新调用或检查服务状态 |
| `STREAM_CANCELLED` | 流式请求被取消 | 正常行为，可重试 |
| `NETWORK_ERROR` | 网络异常 | 检查网络连接或重试 |
| `UPSTREAM_ERROR` | 上游模型返回错误 | 检查请求参数或 API Key 有效性 |

## 完整示例

详见 `demo/` 模块，包含一个带 UI 的完整聊天演示。
