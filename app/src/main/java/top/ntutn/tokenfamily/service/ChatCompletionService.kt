package top.ntutn.tokenfamily.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import top.ntutn.tokenfamily.MainActivity
import top.ntutn.tokenfamily.aidl.ChatCompletionRequest
import top.ntutn.tokenfamily.aidl.ChatCompletionResponse
import top.ntutn.tokenfamily.aidl.IChatCompletionService
import top.ntutn.tokenfamily.aidl.IChatStreamCallback
import top.ntutn.tokenfamily.data.repository.KeyRepository
import top.ntutn.tokenfamily.data.repository.LogEntry
import top.ntutn.tokenfamily.data.repository.LogRepository
import top.ntutn.tokenfamily.data.security.KeyStoreManager

class ChatCompletionService : Service() {

    companion object {
        private const val TAG = "TokenFamily"
        private const val CHANNEL_ID = "agentcore_service"
        private const val NOTIFICATION_ID = 1
        private const val AUTO_STOP_DELAY_MS = 5 * 60 * 1000L
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val requestForwarder = RequestForwarder()
    private var streamForwarder: StreamForwarder? = null
    private var keyRepository: KeyRepository? = null
    private var authManager: AuthManager? = null
    private val handler = Handler(Looper.getMainLooper())
    private var autoStopRunnable: Runnable? = null
    private var bindCount = 0
    private var initialized = false

    override fun onCreate() {
        super.onCreate()
        try {
            val keyStoreManager = KeyStoreManager.getInstance(this)
            keyRepository = KeyRepository(keyStoreManager)
            authManager = AuthManager.getInstance(this)
            streamForwarder = StreamForwarder()
            startForegroundService()
            initialized = true
            Log.i(TAG, "Service created successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Service onCreate failed", e)
            initialized = false
        }
    }

    override fun onDestroy() {
        streamForwarder?.cancelAllStreams()
        serviceScope.cancel()
        cancelAutoStop()
        Log.i(TAG, "Service destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder {
        bindCount++
        cancelAutoStop()
        Log.i(TAG, "Service bound, clientCount=$bindCount")
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        bindCount--
        Log.i(TAG, "Service unbound, clientCount=$bindCount")
        if (bindCount <= 0) {
            scheduleAutoStop()
        }
        return true
    }

    override fun onRebind(intent: Intent?) {
        bindCount++
        cancelAutoStop()
    }

    private val binder = object : IChatCompletionService.Stub() {

        override fun chat(request: ChatCompletionRequest): ChatCompletionResponse {
            if (!initialized) {
                return buildErrorResponse("SERVICE_ERROR", "Service not initialized")
            }

            val callingUid = Binder.getCallingUid()
            val callingPackage = resolveCallingPackage(callingUid)

            val auth = authManager ?: return buildErrorResponse("SERVICE_ERROR", "Auth not available")
            if (!auth.isAuthorized(callingUid, this@ChatCompletionService)) {
                if (!auth.grantAuthorization(callingUid, this@ChatCompletionService)) {
                    return buildErrorResponse("UNAUTHORIZED", "Package not authorized")
                }
            }

            val repo = keyRepository
                ?: return buildErrorResponse("SERVICE_ERROR", "Key store not available")
            val apiKeyConfig = repo.findKeyForModel(request.model)
                ?: return buildErrorResponse("KEY_MISSING", "No API key configured")

            val response = kotlinx.coroutines.runBlocking {
                requestForwarder.forward(request, apiKeyConfig)
            }

            logCall(callingPackage ?: "unknown", request.model, false, response.totalTokens, response.errorCode.isEmpty())

            return response
        }

        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        override fun streamChat(request: ChatCompletionRequest, callback: IChatStreamCallback) {
            if (!initialized) {
                callback.onError(request.requestId, "SERVICE_ERROR", "Service not initialized")
                return
            }

            val callingUid = Binder.getCallingUid()
            val callingPackage = resolveCallingPackage(callingUid)

            val auth = authManager
            if (auth == null) {
                callback.onError(request.requestId, "SERVICE_ERROR", "Auth not available")
                return
            }
            if (!auth.isAuthorized(callingUid, this@ChatCompletionService)) {
                if (!auth.grantAuthorization(callingUid, this@ChatCompletionService)) {
                    callback.onError(request.requestId, "UNAUTHORIZED", "Package not authorized")
                    return
                }
            }

            val repo = keyRepository
            if (repo == null) {
                callback.onError(request.requestId, "SERVICE_ERROR", "Key store not available")
                return
            }
            val apiKeyConfig = repo.findKeyForModel(request.model)
            if (apiKeyConfig == null) {
                callback.onError(request.requestId, "KEY_MISSING", "No API key configured")
                return
            }

            val forwarder = streamForwarder
            if (forwarder == null) {
                callback.onError(request.requestId, "SERVICE_ERROR", "Stream forwarder not available")
                return
            }

            val requestId = request.requestId.ifEmpty {
                java.util.UUID.randomUUID().toString()
            }

            serviceScope.launch {
                forwarder.startStream(requestId, request, apiKeyConfig, callback)
                logCall(
                    callingPackage ?: "unknown",
                    request.model,
                    true,
                    0,
                    true
                )
            }
        }

        override fun cancelStream(requestId: String) {
            streamForwarder?.cancelStream(requestId)
        }
    }

    private fun buildErrorResponse(code: String, message: String) =
        ChatCompletionResponse().apply {
            errorCode = code
            errorMessage = message
        }

    private fun resolveCallingPackage(uid: Int): String? {
        return try {
            packageManager.getPackagesForUid(uid)?.firstOrNull()
        } catch (e: Exception) {
            null
        }
    }

    private fun logCall(
        callingPackage: String,
        model: String,
        streamMode: Boolean,
        tokenCount: Int,
        success: Boolean
    ) {
        LogRepository.getInstance().addLog(
            LogEntry(
                timestamp = System.currentTimeMillis(),
                callingPackage = callingPackage,
                model = model,
                streamMode = streamMode,
                tokenCount = tokenCount,
                status = if (success) "success" else "error"
            )
        )
    }

    private fun startForegroundService() {
        try {
            createNotificationChannel()
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
            val notification = Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("词元芯核")
                .setContentText("AI service is running")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
            startForeground(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start foreground service", e)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "词元芯核",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "词元芯核 AI 转发服务"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun scheduleAutoStop() {
        cancelAutoStop()
        autoStopRunnable = Runnable { stopSelf() }
        handler.postDelayed(autoStopRunnable!!, AUTO_STOP_DELAY_MS)
    }

    private fun cancelAutoStop() {
        autoStopRunnable?.let { handler.removeCallbacks(it) }
        autoStopRunnable = null
    }
}
