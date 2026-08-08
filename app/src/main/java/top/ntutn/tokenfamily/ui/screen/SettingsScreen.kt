package top.ntutn.tokenfamily.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    var autoStopDelay by remember {
        mutableFloatStateOf(viewModel.getAutoStopDelayMinutes().toFloat())
    }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("设置") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("服务设置", style = MaterialTheme.typography.titleMedium)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("无客户端时自动停止延时")
                        Text(
                            "${autoStopDelay.roundToInt()} 分钟",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = autoStopDelay,
                        onValueChange = { autoStopDelay = it },
                        valueRange = 1f..30f,
                        steps = 28,
                        onValueChangeFinished = {
                            viewModel.setAutoStopDelayMinutes(autoStopDelay.roundToInt())
                        }
                    )
                }
            }
        }
    }
}
