package top.ntutn.tokenfamily.sdk.binder

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import top.ntutn.tokenfamily.aidl.IChatCompletionService
import top.ntutn.tokenfamily.sdk.exception.MainThreadCallException
import top.ntutn.tokenfamily.sdk.exception.TokenFamilyException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ServiceConnector(private val context: Context) {

    var onServiceDisconnected: (() -> Unit)? = null

    private val handler = Handler(Looper.getMainLooper())
    private val connectionLock = Any()

    @Volatile
    private var service: IChatCompletionService? = null

    @Volatile
    private var connectionLatch = CountDownLatch(1)

    @Volatile
    private var binding = false

    @Volatile
    private var bound = false

    private var retryCount = 0
    private var reconnectRunnable: Runnable? = null
    private var deathRecipient: IBinder.DeathRecipient? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.i(TAG, "onServiceConnected: $name")
            val connectedService = IChatCompletionService.Stub.asInterface(binder)
            val recipient = IBinder.DeathRecipient {
                Log.i(TAG, "Binder died, scheduling reconnect")
                invalidateConnection()
                scheduleReconnect()
            }

            synchronized(connectionLock) {
                service = connectedService
                deathRecipient = recipient
                binding = false
                bound = connectedService != null
                _isConnected.value = connectedService != null
                retryCount = 0
                connectionLatch.countDown()
            }
            cancelReconnect()

            try {
                binder?.linkToDeath(recipient, 0)
            } catch (_: Exception) {
                invalidateConnection()
                scheduleReconnect()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.i(TAG, "onServiceDisconnected: $name")
            invalidateConnection()
            scheduleReconnect()
        }

        override fun onBindingDied(name: ComponentName?) {
            Log.i(TAG, "onBindingDied: $name")
            invalidateConnection()
            scheduleReconnect()
        }

        override fun onNullBinding(name: ComponentName?) {
            Log.i(TAG, "onNullBinding: $name")
            failPendingConnection()
        }
    }

    fun connect(timeoutMs: Long = DEFAULT_TIMEOUT_MS): IChatCompletionService {
        service?.let { return it }
        if (Looper.myLooper() == Looper.getMainLooper()) throw MainThreadCallException()
        if (!isTokenFamilyInstalled()) {
            throw TokenFamilyException("NOT_INSTALLED", "词元芯核 App 未安装")
        }

        var shouldBind = false
        val latch: CountDownLatch
        synchronized(connectionLock) {
            service?.let { return it }
            if (!binding) {
                binding = true
                connectionLatch = CountDownLatch(1)
                shouldBind = true
            }
            latch = connectionLatch
        }

        if (shouldBind) bindServiceOnce()

        val completed = try {
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw TokenFamilyException("BIND_INTERRUPTED", "等待词元芯核服务连接时被中断", e)
        }

        service?.let { return it }
        if (!completed) {
            val shouldUnbind = synchronized(connectionLock) {
                if (connectionLatch === latch && service == null) {
                    binding = false
                    bound = false
                    connectionLatch.countDown()
                    true
                } else {
                    false
                }
            }
            if (shouldUnbind) {
                try {
                    context.unbindService(serviceConnection)
                } catch (_: Exception) {
                }
            }
        }
        throw TokenFamilyException(
            "NOT_CONNECTED",
            if (completed) "词元芯核服务返回空连接" else "连接词元芯核服务超时 (${timeoutMs}ms)"
        )
    }

    fun bindService() {
        connect(timeoutMs = 5000L)
    }

    fun unbindService() {
        cancelReconnect()
        val shouldUnbind = synchronized(connectionLock) {
            val wasBound = bound || binding
            bound = false
            binding = false
            service = null
            deathRecipient = null
            _isConnected.value = false
            connectionLatch.countDown()
            wasBound
        }
        if (shouldUnbind) {
            try {
                context.unbindService(serviceConnection)
            } catch (_: Exception) {
            }
        }
    }

    fun getService(): IChatCompletionService = service ?: throw TokenFamilyException(
        "NOT_CONNECTED",
        "未连接词元芯核服务"
    )

    fun invalidateConnection() {
        val recipient: IBinder.DeathRecipient?
        val binder: IBinder?
        synchronized(connectionLock) {
            recipient = deathRecipient
            binder = service?.asBinder()
            service = null
            deathRecipient = null
            binding = false
            _isConnected.value = false
            connectionLatch.countDown()
        }
        if (recipient != null && binder != null) {
            try {
                binder.unlinkToDeath(recipient, 0)
            } catch (_: Exception) {
            }
        }
        onServiceDisconnected?.invoke()
    }

    fun isTokenFamilyInstalled(): Boolean = try {
        context.packageManager.getPackageInfo(PACKAGE_NAME, 0)
        true
    } catch (_: Exception) {
        false
    }

    private fun bindServiceOnce() {
        val intent = Intent().apply {
            component = ComponentName(PACKAGE_NAME, "$PACKAGE_NAME.service.ChatCompletionService")
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }

        // 先尝试 startForegroundService 触发 onStartCommand，Service 内部自动显示前台通知；
        // 即使此调用因系统限制（Android 12+ 后台启动限制 / Android 14 前台服务类型缺失 / 权限不足）失败，
        // 后续 bindService 依然会拉起服务，且服务端已在 onBind 内兜底保证前台通知。
        try {
            context.startForegroundService(intent)
            Log.d(TAG, "startForegroundService requested for TokenFamily service")
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException (API 31+)：后台无法启动前台服务，降级为仅 bind
            Log.w(TAG, "startForegroundService blocked (background restriction), fallback to bindService only: ${e.message}")
        } catch (e: SecurityException) {
            // 权限或前台服务类型声明缺失
            Log.w(TAG, "startForegroundService denied (SecurityException), fallback to bindService only: ${e.message}")
        } catch (e: Exception) {
            // 兜底：其他未知异常（如服务已在运行、组件未找到等）
            Log.w(TAG, "startForegroundService skipped: ${e.message}")
        }

        val result = try {
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            failPendingConnection()
            throw TokenFamilyException("BIND_FAILED", "无法绑定词元芯核服务: ${e.message}", e)
        }
        Log.i(TAG, "bindService returned: $result")
        if (!result) {
            failPendingConnection()
            throw TokenFamilyException("BIND_FAILED", "系统拒绝绑定词元芯核服务")
        }
    }

    private fun failPendingConnection() {
        synchronized(connectionLock) {
            binding = false
            connectionLatch.countDown()
        }
    }

    private fun scheduleReconnect() {
        cancelReconnect()
        if (retryCount >= MAX_RETRIES || !bound) return

        val delay = BACKOFF_DELAYS.getOrElse(retryCount) { 30_000L }
        retryCount++
        reconnectRunnable = Runnable {
            Thread {
                try {
                    connect()
                } catch (_: Exception) {
                    handler.post(::scheduleReconnect)
                }
            }.start()
        }
        handler.postDelayed(reconnectRunnable!!, delay)
    }

    private fun cancelReconnect() {
        reconnectRunnable?.let(handler::removeCallbacks)
        reconnectRunnable = null
    }

    companion object {
        private const val TAG = "TokenFamilySDK"
        private const val PACKAGE_NAME = "top.ntutn.tokenfamily"
        private const val MAX_RETRIES = 5
        private const val DEFAULT_TIMEOUT_MS = 15_000L
        private val BACKOFF_DELAYS = longArrayOf(1000L, 2000L, 4000L, 8000L, 16_000L)
    }
}
