package top.ntutn.tokenfamily.data.model

data class ApiKeyConfig(
    val id: String,
    val providerName: String,
    val alias: String,
    val apiKey: String,
    val apiBaseUrl: String,
    val defaultModel: String,
    val isDefault: Boolean = false
)
