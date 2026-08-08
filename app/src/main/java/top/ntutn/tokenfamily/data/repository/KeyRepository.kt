package top.ntutn.tokenfamily.data.repository

import top.ntutn.tokenfamily.data.model.ApiKeyConfig
import top.ntutn.tokenfamily.data.security.KeyStoreManager

class KeyRepository(private val keyStoreManager: KeyStoreManager) {

    fun getAllKeys(): List<ApiKeyConfig> = keyStoreManager.getAllKeys()

    fun getDefaultKey(): ApiKeyConfig? = keyStoreManager.getDefaultKey()

    fun saveKey(config: ApiKeyConfig) = keyStoreManager.saveKey(config)

    fun deleteKey(id: String) = keyStoreManager.deleteKey(id)

    fun setDefaultKey(id: String) = keyStoreManager.setDefaultKey(id)

    fun findKeyForModel(model: String?): ApiKeyConfig? {
        if (model.isNullOrBlank()) return getDefaultKey()
        return getAllKeys().firstOrNull { it.defaultModel == model }
            ?: getDefaultKey()
    }
}
