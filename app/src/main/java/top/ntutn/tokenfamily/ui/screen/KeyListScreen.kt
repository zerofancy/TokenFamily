package top.ntutn.tokenfamily.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.ntutn.tokenfamily.data.model.ApiKeyConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyListScreen(
    viewModel: KeyListViewModel,
    onAddClick: () -> Unit,
    onEditClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val keys by viewModel.keys.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadKeys()
    }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("密钥管理") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                Icon(Icons.Default.Add, contentDescription = "添加密钥")
            }
        }
    ) { padding ->
        if (keys.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无密钥配置，点击右下角添加", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(keys, key = { it.id }) { key ->
                    KeyCard(
                        key = key,
                        onClick = { onEditClick(key.id) },
                        onDelete = { viewModel.deleteKey(key.id) },
                        onSetDefault = { viewModel.setDefaultKey(key.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun KeyCard(
    key: ApiKeyConfig,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onSetDefault: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = key.alias.ifEmpty { key.providerName },
                    style = MaterialTheme.typography.titleMedium
                )
                if (key.isDefault) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = "默认",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "厂商: ${key.providerName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "模型: ${key.defaultModel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!key.isDefault) {
                    TextButton(onClick = onSetDefault) {
                        Text("设为默认")
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
