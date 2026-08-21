package com.renovation.ledger.ui.debug

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.renovation.ledger.ui.common.BackNavigationButton
import com.renovation.ledger.ui.common.CompactTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.renovation.ledger.data.remote.CloudEnv
import com.renovation.ledger.ui.common.ClearableOutlinedTextField
import com.renovation.ledger.ui.common.ZeroTopAppBarWindowInsets
import com.renovation.ledger.ui.debug.netrecord.NetRecordActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugCloudScreen(
    onBack: () -> Unit,
    viewModel: DebugCloudViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var urlDraft by remember(uiState.serverBaseUrl) { mutableStateOf(uiState.serverBaseUrl) }
    var apiKeyDraft by remember { mutableStateOf("") }
    var dashScopeKeyDraft by remember { mutableStateOf("") }
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primary,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        containerColor = MaterialTheme.colorScheme.surface,
        labelColor = MaterialTheme.colorScheme.onSurface,
    )

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.clearMessage()
    }

    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = { Text("开发面板") },
                navigationIcon = {
                    BackNavigationButton(onClick = onBack)
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "环境",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "仅 Debug 包可打开本页。正式包没有摇一摇入口。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = uiState.env == CloudEnv.Kind.DEV,
                            onClick = { viewModel.setEnv(CloudEnv.Kind.DEV) },
                            label = { Text("开发") },
                            colors = chipColors,
                        )
                        FilterChip(
                            selected = uiState.env == CloudEnv.Kind.PROD,
                            onClick = { viewModel.setEnv(CloudEnv.Kind.PROD) },
                            label = { Text("正式") },
                            colors = chipColors,
                        )
                    }
                    Text(
                        text = when {
                            uiState.env == CloudEnv.Kind.PROD ->
                                "当前：正式 ${uiState.serverBaseUrl}"
                            uiState.devChannel == DebugDevChannel.LAN ->
                                "当前：开发 · 电脑局域网 ${uiState.serverBaseUrl}"
                            else ->
                                "当前：开发 · 自定义 ${uiState.serverBaseUrl}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (uiState.env == CloudEnv.Kind.DEV) {
                        FilterChip(
                            selected = uiState.devChannel == DebugDevChannel.LAN,
                            onClick = { viewModel.useLan() },
                            label = { Text("电脑局域网") },
                            colors = chipColors,
                        )
                    }
                    ClearableOutlinedTextField(
                        value = urlDraft,
                        onValueChange = { urlDraft = it },
                        label = { Text("服务器地址") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(
                        onClick = { viewModel.setServerBaseUrl(urlDraft) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("保存服务器地址")
                    }
                    if (uiState.env == CloudEnv.Kind.DEV) {
                        OutlinedButton(
                            onClick = { viewModel.ping() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("测通服务器")
                        }
                    }
                }
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "网络",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "监听 LedgerApi 请求，详情用 FlattenTreeView 展示 JSON（最近 50 条）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = {
                            context.startActivity(Intent(context, NetRecordActivity::class.java))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("接口请求监听")
                    }
                }
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "AI 模型",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "语音意图解析用。Key 仅保存在本机。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = uiState.aiProvider == "deepseek",
                            onClick = { viewModel.setAiProvider("deepseek") },
                            label = { Text("DeepSeek") },
                            colors = chipColors,
                        )
                        FilterChip(
                            selected = uiState.aiProvider == "openai",
                            onClick = { viewModel.setAiProvider("openai") },
                            label = { Text("OpenAI") },
                            colors = chipColors,
                        )
                    }
                    if (uiState.aiApiKeyMasked.isNotBlank()) {
                        Text(
                            text = "当前 Key：${uiState.aiApiKeyMasked}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    ClearableOutlinedTextField(
                        value = apiKeyDraft,
                        onValueChange = { apiKeyDraft = it },
                        label = { Text("API Key") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(
                        onClick = { viewModel.setAiApiKey(apiKeyDraft) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("保存 API Key")
                    }
                    Text(
                        text = "百炼（DashScope）API Key",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        text = "用于语音转写（qwen3-asr-flash），与 DeepSeek 意图 Key 分开",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (uiState.dashScopeApiKeyMasked.isNotBlank()) {
                        Text(
                            text = "当前百炼 Key：${uiState.dashScopeApiKeyMasked}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    ClearableOutlinedTextField(
                        value = dashScopeKeyDraft,
                        onValueChange = { dashScopeKeyDraft = it },
                        label = { Text("百炼 API Key") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(
                        onClick = { viewModel.setDashScopeApiKey(dashScopeKeyDraft) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("保存百炼 Key")
                    }
                }
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "语音调试",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val debug = uiState.lastVoiceDebug
                    if (debug == null) {
                        Text(
                            text = "还没有语音会话。首页点麦克风试一下。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text("ASR：${debug.asrText}")
                        Text("置信度：${"%.2f".format(debug.asrConfidence)}")
                        Text("Tool Calls：${debug.toolCallsText.ifBlank { "（空）" }}")
                        Text("执行结果：${debug.resultSummary.ifBlank { "（无）" }}")
                    }
                }
            }
        }
    }
}
