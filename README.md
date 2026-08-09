# TokenFamily / 词元芯核

TokenFamily 是面向 Android 的开源 AI 中间件。第三方应用继续使用 OkHttp 和 OpenAI Chat Completions 请求格式，API Key、应用授权与上游转发由独立的词元芯核 App 统一管理。

## 模块

| 模块 | 说明 |
|---|---|
| `:app` | 密钥管理、应用授权、Binder 服务与上游 HTTP 转发 |
| `:sdk` | 第三方 Android 应用使用的 OkHttp Interceptor SDK |
| `:demo` | 流式与非流式聊天示例 |

## 数据流

```text
第三方 App (OkHttp)
  -> TokenFamilyInterceptor
  -> Binder IPC
  -> 词元芯核 App
  -> 使用托管 API Key 请求上游模型
```

## 快速开始

先安装并打开词元芯核 App，添加 API Key 配置：

```bash
./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

在第三方应用中添加 SDK 0.2.0：

```kotlin
dependencies {
    implementation("top.ntutn:tokenfamily-sdk:0.2.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
```

初始化并安装拦截器：

```kotlin
TokenFamily.init(applicationContext)

val client = OkHttpClient.Builder()
    .addInterceptor(
        TokenFamilyInterceptor(TokenFamily.getServiceConnector())
    )
    .build()
```

之后仍向 `/v1/chat/completions` 发起请求。SDK 会透明传递 JSON 请求体和响应体，包括 `tools`、`tool_choice`、多模态消息、`response_format`、`stream_options` 及供应商扩展字段。

> SDK 与词元芯核 App 的 0.2.x AIDL 协议不兼容 0.1.x，请同步升级。

## 兼容范围

- Android API 26+。
- 消费方最低 `compileSdk` 为 30；不要求跟随词元芯核项目使用 API 37。
- 支持 OpenAI 风格 Chat Completions 的流式和非流式请求。
- 请求中缺少 `model` 或值为空时，词元芯核会填入所选密钥配置的默认模型；其他 JSON 字段不会重建。
- 上游 HTTP 状态码、错误体、Content-Type 和安全响应头会返回调用方。
- 不提供 Responses API，也不保证所有上游供应商支持相同模型能力。

## 开发

```bash
./gradlew :sdk:test
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug :sdk:assembleDebug :demo:assembleDebug
```

完整接入、tool calling、SSE、错误与限制说明见 [SDK.md](SDK.md)。

## 许可证

MIT License，详见 [LICENSE](LICENSE)。
