package top.ntutn.tokenfamily.data.repository

import java.util.LinkedHashMap

data class LogEntry(
    val timestamp: Long,
    val callingPackage: String,
    val model: String,
    val streamMode: Boolean,
    val tokenCount: Int,
    val status: String
)

class LogRepository {

    private val maxEntries = 500

    private val logs = object : LinkedHashMap<Long, LogEntry>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, LogEntry>?): Boolean {
            return size > maxEntries
        }
    }

    @Synchronized
    fun addLog(entry: LogEntry) {
        logs[entry.timestamp] = entry
    }

    @Synchronized
    fun getAllLogs(): List<LogEntry> =
        logs.values.toList().sortedByDescending { it.timestamp }

    @Synchronized
    fun clearLogs() {
        logs.clear()
    }

    companion object {
        @Volatile
        private var instance: LogRepository? = null

        fun getInstance(): LogRepository {
            return instance ?: synchronized(this) {
                instance ?: LogRepository().also { instance = it }
            }
        }
    }
}
