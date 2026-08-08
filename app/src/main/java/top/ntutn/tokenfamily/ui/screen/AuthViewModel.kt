package top.ntutn.tokenfamily.ui.screen

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.ntutn.tokenfamily.aidl.IChatCompletionService
import top.ntutn.tokenfamily.service.AuthManager

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val authManager = AuthManager.getInstance(application)

    private val _authorizedApps = MutableStateFlow<List<AuthManager.AuthorizedApp>>(emptyList())
    val authorizedApps: StateFlow<List<AuthManager.AuthorizedApp>> = _authorizedApps.asStateFlow()

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private var serviceConnection: ServiceConnection? = null

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

    fun toggleService() {
        val context = getApplication<Application>()
        if (_isServiceRunning.value) {
            stopService(context)
        } else {
            startService(context)
        }
    }

    private fun startService(context: Context) {
        val intent = Intent(context, top.ntutn.tokenfamily.service.ChatCompletionService::class.java)
        context.startForegroundService(intent)

        serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                _isServiceRunning.value = true
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                _isServiceRunning.value = false
            }
        }

        context.bindService(intent, serviceConnection!!, Context.BIND_AUTO_CREATE)
    }

    private fun stopService(context: Context) {
        serviceConnection?.let { context.unbindService(it) }
        serviceConnection = null

        val intent = Intent(context, top.ntutn.tokenfamily.service.ChatCompletionService::class.java)
        context.stopService(intent)
        _isServiceRunning.value = false
    }

    override fun onCleared() {
        super.onCleared()
        serviceConnection?.let { getApplication<Application>().unbindService(it) }
    }
}
