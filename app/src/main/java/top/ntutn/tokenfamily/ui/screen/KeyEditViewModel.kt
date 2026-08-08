package top.ntutn.tokenfamily.ui.screen

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import top.ntutn.tokenfamily.data.model.ApiKeyConfig
import top.ntutn.tokenfamily.data.repository.KeyRepository
import top.ntutn.tokenfamily.data.security.KeyStoreManager
import java.util.UUID

class KeyEditViewModel(application: Application) : AndroidViewModel(application) {

    private val keyRepository = KeyRepository(KeyStoreManager.getInstance(application))

    private var editingId: String? = null

    fun loadKey(id: String) {
        editingId = id
    }

    fun saveKey(
        providerName: String,
        alias: String,
        apiKey: String,
        apiBaseUrl: String,
        defaultModel: String,
        isDefault: Boolean
    ) {
        viewModelScope.launch {
            val config = ApiKeyConfig(
                id = editingId ?: UUID.randomUUID().toString(),
                providerName = providerName,
                alias = alias,
                apiKey = apiKey,
                apiBaseUrl = apiBaseUrl,
                defaultModel = defaultModel,
                isDefault = isDefault
            )
            keyRepository.saveKey(config)
        }
    }

    fun deleteKey() {
        val id = editingId ?: return
        viewModelScope.launch {
            keyRepository.deleteKey(id)
        }
    }

    fun getExistingKey(id: String): ApiKeyConfig? {
        return keyRepository.getAllKeys().find { it.id == id }
    }
}
