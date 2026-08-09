package top.ntutn.tokenfamily.sdk.exception

import java.io.IOException

open class TokenFamilyException(
    val errorCode: String,
    override val message: String,
    cause: Throwable? = null
) : IOException(message, cause)

class NotInstalledException(message: String = "词元芯核 App 未安装") :
    TokenFamilyException("NOT_INSTALLED", message)

class BindFailedException(message: String = "无法绑定词元芯核服务") :
    TokenFamilyException("BIND_FAILED", message)

class UnauthorizedException(message: String = "应用未授权") :
    TokenFamilyException("UNAUTHORIZED", message)

class KeyMissingException(message: String = "未配置 API 密钥") :
    TokenFamilyException("KEY_MISSING", message)

class StreamCancelledException(message: String = "流式请求已取消") :
    TokenFamilyException("STREAM_CANCELLED", message)

class NetworkException(message: String = "网络异常") :
    TokenFamilyException("NETWORK_ERROR", message)

class PayloadTooLargeException(message: String = "请求体超过 Binder 安全上限") :
    TokenFamilyException("PAYLOAD_TOO_LARGE", message)

class MainThreadCallException(message: String = "不能在主线程同步连接词元芯核服务") :
    TokenFamilyException("MAIN_THREAD_CALL", message)
