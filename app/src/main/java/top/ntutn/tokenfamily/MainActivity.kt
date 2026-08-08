package top.ntutn.tokenfamily

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
