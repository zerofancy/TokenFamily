package top.ntutn.tokenfamily.sdk.binder

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.util.Log
import top.ntutn.tokenfamily.aidl.IChatCompletionService
import top.ntutn.tokenfamily.sdk.exception.TokenFamilyException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ServiceConnector(private val context: Context) {

    var onServiceDisconnected: (() -> Unit)? = null

    private val handler = Handler(Looper.getMainLooper())
    private var service: IChatCompletionService? = null
    private var retryCount = 0
    private var reconnectRunnable: Runnable? = null
    private var deathRecipient: IBinder.DeathRecipient? = null
    private var connectLatch: CountDownLatch? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.i(TAG, "onServiceConnected: $name")
            service = IChatCompletionService.Stub.asInterface(binder)
            _isConnected.value = true
            retryCount = 0
            cancelReconnect()

            deathRecipient = IBinder.DeathRecipient {
                Log.i(TAG, "Binder died, scheduling reconnect")
                onServiceDisconnected?.invoke()
                _isConnected.value = false
                service = null
                scheduleReconnect()
            }

            try {
                binder?.linkToDeath(deathRecipient!!, 0)
            } catch (_: Exception) {}

            connectLatch?.countDown()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.i(TAG, "onServiceDisconnected: $name")
            service = null
            _isConnected.value = false
            deathRecipient = null
            onServiceDisconnected?.invoke()
            scheduleReconnect()
        }

        override fun onBindingDied(name: ComponentName?) {
            Log.i(TAG, "onBindingDied: $name")
            onServiceDisconnected(name)
        }

        override fun onNullBinding(name: ComponentName?) {
            Log.i(TAG, "onNullBinding: $name")
            connectLatch?.countDown()
        }
    }

    fun connect(timeoutMs: Long = 15000L): IChatCompletionService {
        if (service != null) return service!!

        if (!isTokenFamilyInstalled()) {
            throw TokenFamilyException(
                "NOT_INSTALLED",
                "词元芯核 App 未安装"
            )
        }

        connectLatch = CountDownLatch(1)

        val intent = Intent().apply {
            component = ComponentName(
                PACKAGE_NAME,
                "$PACKAGE_NAME.service.ChatCompletionService"
            )
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }

        try {
            val result = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            Log.i(TAG, "bindService returned: $result")
        } catch (e: Exception) {
            throw TokenFamilyException(
                "BIND_FAILED",
                "无法绑定词元芯核服务: ${e.message}"
            )
        }

        val success = connectLatch!!.await(timeoutMs, TimeUnit.MILLISECONDS)
        connectLatch = null

        if (!success || service == null) {
            throw TokenFamilyException(
                "NOT_CONNECTED",
                "连接词元芯核服务超时 (${timeoutMs}ms)"
            )
        }

        return service!!
    }

    fun bindService() {
        connect(timeoutMs = 5000L)
    }

    fun unbindService() {
        cancelReconnect()
        try {
            context.unbindService(serviceConnection)
        } catch (_: Exception) {}
        service = null
        _isConnected.value = false
    }

    fun getService(): IChatCompletionService {
        return service ?: throw TokenFamilyException(
            "NOT_CONNECTED",
            "未连接词元芯核服务，请先调用 bindService()"
        )
    }

    fun isTokenFamilyInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo(PACKAGE_NAME, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun scheduleReconnect() {
        cancelReconnect()
        if (retryCount >= MAX_RETRIES) return

        val delay = computeBackoffDelay(retryCount)
        retryCount++

        reconnectRunnable = Runnable {
            Thread {
                try {
                    connect()
                } catch (_: Exception) {
                    handler.post { scheduleReconnect() }
                }
            }.start()
        }

        handler.postDelayed(reconnectRunnable!!, delay)
    }

    private fun cancelReconnect() {
        reconnectRunnable?.let { handler.removeCallbacks(it) }
        reconnectRunnable = null
    }

    private fun computeBackoffDelay(attempt: Int): Long {
        val delays = longArrayOf(1000L, 2000L, 4000L, 8000L, 16000L)
        return delays.getOrElse(attempt) { 30000L }
    }

    companion object {
        private const val TAG = "TokenFamilySDK"
        private const val PACKAGE_NAME = "top.ntutn.tokenfamily"
        private const val MAX_RETRIES = 5
    }
}
