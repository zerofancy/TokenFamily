package top.ntutn.tokenfamily.ui.screen

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import top.ntutn.tokenfamily.service.AuthManager

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val authManager = AuthManager.getInstance(application)

    private val _authorizedApps = MutableStateFlow<List<AuthManager.AuthorizedApp>>(emptyList())
    val authorizedApps: StateFlow<List<AuthManager.AuthorizedApp>> = _authorizedApps.asStateFlow()

    init {
        loadAuthorizedApps()
    }

    fun loadAuthorizedApps() {
        _authorizedApps.value = authManager.getAuthorizedApps()
    }

    fun revokeAuthorization(packageName: String) {
        authManager.revokeAuthorization(packageName)
        loadAuthorizedApps()
    }

}
