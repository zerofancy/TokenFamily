# AGENTS.md — TokenFamily 项目开发指南

<!-- OPENSPEC:START -->
# OpenSpec Instructions

These instructions are for AI assistants working in this project.

Always open `@/openspec/AGENTS.md` when the request:
- Mentions planning or proposals (words like proposal, spec, change, plan)
- Introduces new capabilities, breaking changes, architecture shifts, or big performance/security work
- Sounds ambiguous and you need the authoritative spec before coding

Use `@/openspec/AGENTS.md` to learn:
- How to create and apply change proposals
- Spec format and conventions
- Project structure and guidelines

Keep this managed block so 'openspec update' can refresh the instructions.

<!-- OPENSPEC:END -->

## 项目概述

TokenFamily（词元芯核）是 Android 平台开源 AI 中间件，由三模块组成：

| 模块 | 路径 | 包名 | 类型 |
|------|------|------|------|
| 核心 App | `app/` | `top.ntutn.tokenfamily` | Application |
| SDK | `sdk/` | `top.ntutn.tokenfamily.sdk` | Library |
| Demo | `demo/` | `top.ntutn.tokenfamily.demo` | Application |

## 构建设置

- **Kotlin** 2.2.10
- **AGP** 9.3.1
- **Gradle** 9.5.0
- **Compose BOM** 2026.02.01
- **Min SDK** 26, **Target/Compile SDK** 37
- **版本目录** `gradle/libs.versions.toml`

## 常用命令

```bash
./gradlew :app:assembleDebug     # 编译 App
./gradlew :sdk:assembleDebug     # 编译 SDK (AAR)
./gradlew :demo:assembleDebug    # 编译 Demo
./gradlew :sdk:test              # 运行 SDK 测试
./gradlew clean build            # 全量构建
```

## 架构核心

### 跨进程通信（Binder/AIDL）

AIDL 文件位于两处（同步维护）：
- `app/src/main/aidl/top/ntutn/tokenfamily/aidl/`
- `sdk/src/main/aidl/top/ntutn/tokenfamily/aidl/`

关键接口：
- `IChatCompletionService.aidl` — 主服务接口
- `IChatStreamCallback.aidl` — 流式回调
- `ChatCompletionRequest.aidl` / `ChatCompletionResponse.aidl` / `ChatMessage.aidl`

### 数据流

```
第三方 App (OkHttp)
  → TokenFamilyInterceptor (SDK, 协议转换)
    → ServiceConnector.connect() (Binder 通道)
      → ChatCompletionService (App, Binder.getCallingUid 鉴权)
        → AuthManager (授权校验)
        → KeyRepository (密钥查找)
        → RequestForwarder / StreamForwarder (HTTP 请求上游)
```

### 线程模型

| 组件 | 线程 |
|------|------|
| Interceptor | OkHttp Dispatcher (后台) |
| ServiceConnector.connect() | OkHttp Dispatcher → CountDownLatch 等待 |
| AIDL 回调 | 主线程 (Handler) |
| 上游 HTTP 请求 | Coroutine IO Dispatcher |
| Compose UI | 主线程 |

### 流式链路

1. SDK 层创建 `StreamResponseWrapper`（内部 `Channel<String>` 管道）
2. Binder `onChunk` 回调 → `channel.trySend(chunk)`
3. OkHttp 读取线程 → `channel.receive()` → 包装为 SSE ResponseBody

## 关键注意事项

### 构建

- `buildFeatures { aidl = true }` 必须在 app 和 sdk 的 build.gradle.kts 中显式开启
- `kotlin-android` 插件与 `kotlin-compose` 冲突，已移除前者的显式声明
- `android-library` 和 `android-application` 插件都需在 root `build.gradle.kts` 中以 `apply false` 声明

### SDK 约束

- SDK 外部依赖 ≤ 4 个（OkHttp、Coroutines、AndroidX Core KTX、Gson）
- `consumer-rules.pro` 保留 AIDL 生成类
- `<queries>` 声明词元芯核包名和服务 Intent

### 死锁风险

Demo 中调用 OkHttp `execute()` 时**必须**使用 `withContext(Dispatchers.IO)`，否则主线程阻塞会死锁 `ServiceConnector.connect()` 中的 `CountDownLatch`。

### 安全

- API Key 通过 `EncryptedSharedPreferences` 存储
- 第三方应用通过 `Binder.getCallingUid()` 鉴权
- `FLAG_INCLUDE_STOPPED_PACKAGES` 确保绑定不受 app 停止状态限制
- Android 13+ 需 `POST_NOTIFICATIONS` 权限

## 文件结构

```
app/src/main/java/top/ntutn/tokenfamily/
├── MainActivity.kt
├── aidl/           → 自动生成
├── data/
│   ├── model/      → ApiKeyConfig
│   ├── repository/ → KeyRepository, LogRepository
│   └── security/   → KeyStoreManager
├── service/        → ChatCompletionService, RequestForwarder, StreamForwarder, AuthManager
└── ui/screen/      → KeyListScreen, KeyEditScreen, AuthScreen, LogScreen, SettingsScreen

sdk/src/main/java/top/ntutn/tokenfamily/sdk/
├── TokenFamily.kt
├── aidl/           → 自动生成
├── binder/         → ServiceConnector, BinderRequestConverter, StreamResponseWrapper
├── exception/      → TokenFamilyException 及子类
└── interceptor/    → TokenFamilyInterceptor
```

## 项目文档

- [README.md](README.md) — 项目总览与快速开始
- [SDK.md](SDK.md) — 第三方接入文档
- [LICENSE](LICENSE) — MIT 许可证
