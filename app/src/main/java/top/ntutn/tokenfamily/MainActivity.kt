package top.ntutn.tokenfamily

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import top.ntutn.tokenfamily.ui.screen.*
import top.ntutn.tokenfamily.ui.theme.TokenFamilyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TokenFamilyTheme {
                MainApp()
            }
        }
    }
}

enum class Screen(val title: String, val icon: ImageVector) {
    Keys("密钥", Icons.Default.Home),
    Auth("授权", Icons.Default.Lock),
    Logs("日志", Icons.AutoMirrored.Filled.List),
    Settings("设置", Icons.Default.Settings)
}

@Composable
fun MainApp() {
    var currentScreen by remember { mutableStateOf(Screen.Keys) }
    var editingKeyId by remember { mutableStateOf<String?>(null) }

    NotificationPermissionRequester()

    if (editingKeyId != null || currentScreen == Screen.Keys && editingKeyId == "") {
        val keyEditViewModel: KeyEditViewModel = viewModel()
        KeyEditScreen(
            viewModel = keyEditViewModel,
            keyId = if (editingKeyId == "") null else editingKeyId,
            onBack = { editingKeyId = null }
        )
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    Screen.entries.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            selected = currentScreen == screen,
                            onClick = { currentScreen = screen }
                        )
                    }
                }
            }
        ) { innerPadding ->
            when (currentScreen) {
                Screen.Keys -> {
                    val keyListViewModel: KeyListViewModel = viewModel()
                    KeyListScreen(
                        viewModel = keyListViewModel,
                        onAddClick = { editingKeyId = "" },
                        onEditClick = { id -> editingKeyId = id },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
                Screen.Auth -> {
                    val authViewModel: AuthViewModel = viewModel()
                    AuthScreen(
                        viewModel = authViewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
                Screen.Logs -> {
                    val logViewModel: LogViewModel = viewModel()
                    LogScreen(
                        viewModel = logViewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
                Screen.Settings -> {
                    val settingsViewModel: SettingsViewModel = viewModel()
                    SettingsScreen(
                        viewModel = settingsViewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

/**
 * 检查并请求通知权限。
 * - Android 13 (API 33)+ 需要请求运行时权限 POST_NOTIFICATIONS
 * - 若用户拒绝过（不再弹出系统对话框），则引导进入应用设置
 * - 通知通道被关闭（无论权限是否授予）都视为不可用
 */
@Composable
private fun NotificationPermissionRequester() {
    val context = LocalContext.current
    val nm = remember(context) {
        ContextCompat.getSystemService(context, NotificationManager::class.java)
    }
    var areNotificationsEnabled by remember {
        mutableStateOf(nm?.areNotificationsEnabled() != false)
    }
    // 记录系统弹窗是否已经显示过，避免被永久拒绝后反复触发
    var permissionPromptShown by remember { mutableStateOf(false) }
    var showRationale by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        permissionPromptShown = true
        val enabled = nm?.areNotificationsEnabled() != false
        areNotificationsEnabled = enabled
        if (!enabled) {
            showRationale = true
        }
    }

    // 进入应用时，若通知不可用则先尝试请求一次系统弹窗（API 33+）
    LaunchedEffect(Unit) {
        if (nm?.areNotificationsEnabled() != false) {
            areNotificationsEnabled = true
            return@LaunchedEffect
        }
        areNotificationsEnabled = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // 12 及以下没有运行时通知权限，但用户可以在设置中关通知；直接引导去设置
            showRationale = true
        }
    }

    if (showRationale) {
        NotificationRationaleDialog(
            onGoSettings = {
                showRationale = false
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            },
            onDismiss = {
                showRationale = false
            }
        )
    }
}

@Composable
private fun NotificationRationaleDialog(
    onGoSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("需要通知权限") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "词元芯核作为 AI 转发中间件，运行时会启动一个前台服务。" +
                        "若“允许通知”关闭，前台服务的常驻通知将无法显示，" +
                        "系统可能误判服务异常甚至提前结束任务。"
                )
                Text(
                    "请点击下方按钮前往系统设置，打开词元芯核的通知开关。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onGoSettings) { Text("前往设置") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("稍后再说") }
        }
    )
}
