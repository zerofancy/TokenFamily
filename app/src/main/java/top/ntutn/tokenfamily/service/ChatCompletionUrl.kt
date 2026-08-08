package top.ntutn.tokenfamily.service

import java.net.URI

internal object ChatCompletionUrl {

    fun normalize(baseUrl: String): String {
        val raw = baseUrl.trimEnd('/')
        val uri = URI(raw)
        val path = (uri.path ?: "").removeSuffix("/")
        val newPath = when {
            path.endsWith("/chat/completions") -> path
            path.endsWith("/v1") -> "$path/chat/completions"
            else -> "$path/v1/chat/completions"
        }
        val rebuilt = URI(uri.scheme, uri.userInfo, uri.host, uri.port, newPath, uri.query, null)
        return rebuilt.toString()
    }
}
