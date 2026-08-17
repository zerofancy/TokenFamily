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
                return RequestForwarder.errorResponse(503, "SERVICE_ERROR", "Service not initialized")
            }

            val callingUid = Binder.getCallingUid()
            val callingPackage = resolveCallingPackage(callingUid)

            val auth = authManager
                ?: return RequestForwarder.errorResponse(503, "SERVICE_ERROR", "Auth not available")
            if (!auth.isAuthorized(callingUid, this@ChatCompletionService)) {
                if (!auth.grantAuthorization(callingUid, this@ChatCompletionService)) {
                    return RequestForwarder.errorResponse(403, "UNAUTHORIZED", "Package not authorized")
                }
            }

            val repo = keyRepository
                ?: return RequestForwarder.errorResponse(503, "SERVICE_ERROR", "Key store not available")
            val requestedModel = try {
                requestForwarder.requestedModel(request.bodyJson)
            } catch (e: IllegalArgumentException) {
                return RequestForwarder.errorResponse(
                    400,
                    "INVALID_REQUEST",
                    e.message ?: "Invalid JSON request"
                )
            }
            val apiKeyConfig = repo.findKeyForModel(requestedModel)
                ?: return RequestForwarder.errorResponse(503, "KEY_MISSING", "No API key configured")

            val response = kotlinx.coroutines.runBlocking {
                requestForwarder.forward(request, apiKeyConfig)
            }

            logCall(
                callingPackage ?: "unknown",
                requestedModel.ifBlank { apiKeyConfig.defaultModel },
                false,
                extractTotalTokens(response.body),
                response.statusCode in 200..299
            )

            return response
        }

        override fun streamChat(
            request: ChatCompletionRequest,
            callback: IChatStreamCallback
        ): ChatCompletionResponse {
            if (!initialized) {
                return RequestForwarder.errorResponse(503, "SERVICE_ERROR", "Service not initialized")
            }

            val callingUid = Binder.getCallingUid()
            val callingPackage = resolveCallingPackage(callingUid)

            val auth = authManager
            if (auth == null) {
                return RequestForwarder.errorResponse(503, "SERVICE_ERROR", "Auth not available")
            }
            if (!auth.isAuthorized(callingUid, this@ChatCompletionService)) {
                if (!auth.grantAuthorization(callingUid, this@ChatCompletionService)) {
                    return RequestForwarder.errorResponse(403, "UNAUTHORIZED", "Package not authorized")
                }
            }

            val repo = keyRepository
            if (repo == null) {
                return RequestForwarder.errorResponse(503, "SERVICE_ERROR", "Key store not available")
            }
            val requestedModel = try {
                requestForwarder.requestedModel(request.bodyJson)
            } catch (e: IllegalArgumentException) {
                return RequestForwarder.errorResponse(
                    400,
                    "INVALID_REQUEST",
                    e.message ?: "Invalid JSON request"
                )
            }
            val apiKeyConfig = repo.findKeyForModel(requestedModel)
            if (apiKeyConfig == null) {
                return RequestForwarder.errorResponse(503, "KEY_MISSING", "No API key configured")
            }

            val forwarder = streamForwarder
            if (forwarder == null) {
                return RequestForwarder.errorResponse(
                    503,
                    "SERVICE_ERROR",
                    "Stream forwarder not available"
                )
            }

            val response = forwarder.openStream(request, apiKeyConfig, callback)
            logCall(
                callingPackage ?: "unknown",
                requestedModel.ifBlank { apiKeyConfig.defaultModel },
                true,
                0,
                response.statusCode in 200..299
            )
            return response
        }

        override fun cancelStream(requestId: String) {
            streamForwarder?.cancelStream(requestId)
        }

        override fun listModels(): ChatCompletionResponse {
            if (!initialized) {
                return RequestForwarder.errorResponse(503, "SERVICE_ERROR", "Service not initialized")
            }

            val callingUid = Binder.getCallingUid()
            val auth = authManager
                ?: return RequestForwarder.errorResponse(503, "SERVICE_ERROR", "Auth not available")
            if (!auth.isAuthorized(callingUid, this@ChatCompletionService)) {
                if (!auth.grantAuthorization(callingUid, this@ChatCompletionService)) {
                    return RequestForwarder.errorResponse(403, "UNAUTHORIZED", "Package not authorized")
                }
            }

            val repo = keyRepository
                ?: return RequestForwarder.errorResponse(503, "SERVICE_ERROR", "Key store not available")
            return ModelCatalog.buildResponse(repo.getAllKeys())
        }
    }

    private fun extractTotalTokens(body: String): Int = try {
        com.google.gson.JsonParser.parseString(body)
            .asJsonObject
            .getAsJsonObject("usage")
            ?.get("total_tokens")
            ?.asInt ?: 0
    } catch (_: Exception) {
        0
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
