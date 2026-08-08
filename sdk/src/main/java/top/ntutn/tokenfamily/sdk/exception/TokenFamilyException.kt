package top.ntutn.tokenfamily.sdk.exception

open class TokenFamilyException(
    val errorCode: String,
    override val message: String
) : Exception(message)

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
