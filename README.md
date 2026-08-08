# TokenFamily / 词元芯核

面向 Android 平台的开源 AI 中间件，基于 Binder IPC 实现跨进程 Chat Completions 转发，兼容 OpenAI 接口格式，支持流式（SSE）和非流式两种模式。

## 架构

```
┌──────────────────────────┐     ┌────────────────────────────┐
│   第三方 App (Demo)       │     │   词元芯核 App (:app)        │
│                          │     │                            │
│  OkHttp + Interceptor ───┼─────┼──► AIDL Service           │
│  TokenFamily SDK (:sdk)  │Binder│   ├─ 密钥加密存储           │
│                          │IPC  │   ├─ 请求鉴权               │
└──────────────────────────┘     │   └─ OkHttp → 上游模型      │
                                 └────────────────────────────┘
```

## 文档

| 文档 | 说明 |
|------|------|
| [README.md](README.md) | 项目总览、架构与快速开始 |
| [SDK.md](SDK.md) | 第三方接入 SDK 的完整文档（初始化、请求、流式、错误码） |
| [AGENTS.md](AGENTS.md) | 开发者指南（构建命令、模块结构、架构约束） |
| [LICENSE](LICENSE) | MIT 许可证 |

## 模块

| 模块 | 类型 | 说明 |
|------|------|------|
| `:app` | Android Application | 核心管理 App，密钥存储、AIDL 服务、请求转发 |
| `:sdk` | Android Library | 开发者 SDK，OkHttp Interceptor 接入 |
| `:demo` | Android Application | SDK 接入演示 App |

## 快速开始

### 1. 安装词元芯核 App

```bash
./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

打开词元芯核 App，在「密钥管理」中添加 API Key 配置。

### 2. 安装 Demo App

```bash
./gradlew :demo:assembleDebug
adb install demo/build/outputs/apk/debug/demo-debug.apk
```

打开 Demo App，输入消息发送测试。

### 3. 在自己的 App 中接入 SDK

```kotlin
// build.gradle.kts
dependencies {
    implementation(project(":sdk"))
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}

// Application.onCreate()
TokenFamily.init(this)

// 创建带拦截器的 OkHttpClient
val interceptor = TokenFamilyInterceptor(TokenFamily.getServiceConnector())
val client = OkHttpClient.Builder()
    .addInterceptor(interceptor)
    .build()

// 业务代码完全不变
val request = Request.Builder()
    .url("https://api.openai.com/v1/chat/completions")
    .post(...)
    .build()
client.newCall(request).execute()  // 自动通过 词元芯核 转发
```

## 核心特性

- **Binder 内核级鉴权**：基于 UID 的应用身份识别
- **密钥安全存储**：Android Keystore + EncryptedSharedPreferences
- **OpenAI 兼容**：SDK 层 100% 对齐 OpenAI Chat Completions API
- **流式支持**：SSE 实时推流，支持中途取消
- **进程保活**：前台服务常驻通知，客户端死亡自动释放
- **SDK 轻量**：仅依赖 OkHttp + Coroutines

## 技术栈

- Kotlin 2.2
- AGP 9.3 / Gradle 9.5
- Jetpack Compose (Material 3)
- OkHttp 4.12
- AndroidX Security Crypto

## 最低兼容

Android API 26 (Android 8.0)

## 开发

```bash
# 编译全部模块
./gradlew assembleDebug

# 运行测试
./gradlew test

# 安装到设备
./gradlew :app:installDebug :demo:installDebug
```

## 许可证

MIT License — 详见 [LICENSE](LICENSE)
