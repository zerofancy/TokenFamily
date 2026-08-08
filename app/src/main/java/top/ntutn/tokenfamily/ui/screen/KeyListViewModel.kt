package top.ntutn.tokenfamily.ui.screen

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.ntutn.tokenfamily.data.model.ApiKeyConfig
import top.ntutn.tokenfamily.data.repository.KeyRepository
import top.ntutn.tokenfamily.data.security.KeyStoreManager

class KeyListViewModel(application: Application) : AndroidViewModel(application) {

    private val keyRepository = KeyRepository(KeyStoreManager.getInstance(application))

    private val _keys = MutableStateFlow<List<ApiKeyConfig>>(emptyList())
    val keys: StateFlow<List<ApiKeyConfig>> = _keys.asStateFlow()

    init {
        loadKeys()
    }

    fun loadKeys() {
        viewModelScope.launch {
            _keys.value = keyRepository.getAllKeys()
        }
    }

    fun deleteKey(id: String) {
        viewModelScope.launch {
            keyRepository.deleteKey(id)
            loadKeys()
        }
    }

    fun setDefaultKey(id: String) {
        viewModelScope.launch {
            keyRepository.setDefaultKey(id)
            loadKeys()
        }
    }
}
