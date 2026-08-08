package top.ntutn.tokenfamily.ui.screen

import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class SettingsViewModel(application: android.app.Application) : AndroidViewModel(application) {

    private val prefs: SharedPreferences =
        application.getSharedPreferences("agentcore_settings", android.content.Context.MODE_PRIVATE)

    fun getAutoStopDelayMinutes(): Int {
        return prefs.getInt(KEY_AUTO_STOP_DELAY, DEFAULT_DELAY_MINUTES)
    }

    fun setAutoStopDelayMinutes(delayMinutes: Int) {
        prefs.edit().putInt(KEY_AUTO_STOP_DELAY, delayMinutes).apply()
    }

    companion object {
        private const val KEY_AUTO_STOP_DELAY = "auto_stop_delay_minutes"
        private const val DEFAULT_DELAY_MINUTES = 5
    }
}
