package top.ntutn.tokenfamily.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.ntutn.tokenfamily.data.repository.LogEntry
import top.ntutn.tokenfamily.data.repository.LogRepository

class LogViewModel : ViewModel() {

    private val logRepository = LogRepository.getInstance()

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    init {
        loadLogs()
    }

    fun loadLogs() {
        viewModelScope.launch {
            _logs.value = logRepository.getAllLogs()
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            logRepository.clearLogs()
            _logs.value = emptyList()
        }
    }
}
