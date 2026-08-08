package top.ntutn.tokenfamily.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyEditScreen(
    viewModel: KeyEditViewModel,
    keyId: String?,
    onBack: () -> Unit
) {
    val existingKey = keyId?.let { viewModel.getExistingKey(it) }

    var providerName by remember { mutableStateOf(existingKey?.providerName ?: "") }
    var alias by remember { mutableStateOf(existingKey?.alias ?: "") }
    var apiKey by remember { mutableStateOf(existingKey?.apiKey ?: "") }
    var apiBaseUrl by remember { mutableStateOf(existingKey?.apiBaseUrl ?: "") }
    var defaultModel by remember { mutableStateOf(existingKey?.defaultModel ?: "") }
    var isDefault by remember { mutableStateOf(existingKey?.isDefault ?: false) }

    LaunchedEffect(keyId) {
        keyId?.let { viewModel.loadKey(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (keyId != null) "编辑密钥" else "添加密钥") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = providerName,
                onValueChange = { providerName = it },
                label = { Text("厂商名称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = alias,
                onValueChange = { alias = it },
                label = { Text("别名") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = apiBaseUrl,
                onValueChange = { apiBaseUrl = it },
                label = { Text("API Base URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = defaultModel,
                onValueChange = { defaultModel = it },
                label = { Text("默认模型名") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("设为全局默认", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = isDefault,
                    onCheckedChange = { isDefault = it }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    viewModel.saveKey(providerName, alias, apiKey, apiBaseUrl, defaultModel, isDefault)
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = providerName.isNotBlank() && apiKey.isNotBlank() && apiBaseUrl.isNotBlank()
            ) {
                Text("保存")
            }
            if (keyId != null) {
                OutlinedButton(
                    onClick = {
                        viewModel.deleteKey()
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("删除")
                }
            }
        }
    }
}
