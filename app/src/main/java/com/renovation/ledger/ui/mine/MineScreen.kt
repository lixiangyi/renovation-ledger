package com.renovation.ledger.ui.mine

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.renovation.ledger.ui.common.CompactTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.renovation.ledger.domain.metrics.HealthColorResolver
import com.renovation.ledger.domain.model.Project
import com.renovation.ledger.ui.common.ProfileAvatar
import kotlin.math.roundToInt

private fun Context.performSliderTick() {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Vibrator::class.java)
    } ?: return
    if (!vibrator.hasVibrator()) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
    } else {
        vibrator.vibrate(VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MineScreen(
    onOpenBatchImport: () -> Unit,
    onOpenTaxonomyManage: () -> Unit,
    onOpenTrash: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    viewModel: MineViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showHealthColorHelp by remember { mutableStateOf(false) }
    var showImportLedgerPrompt by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Project?>(null) }

    LaunchedEffect(uiState.actionMessage) {
        val message = uiState.actionMessage ?: return@LaunchedEffect
        val duration = if (message.length > 18) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
        Toast.makeText(context, message, duration).show()
        viewModel.clearActionMessage()
    }

    val openDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader(Charsets.UTF_8).readText()
        }.orEmpty()
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "导入文件"
        when (val result = viewModel.prepareImportFromCsv(text, name)) {
            is CsvImportResult.Ready -> onOpenBatchImport()
            is CsvImportResult.Failed -> {
                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    if (showImportLedgerPrompt) {
        AlertDialog(
            onDismissRequest = { showImportLedgerPrompt = false },
            title = { Text("导入并新建账本") },
            text = {
                Text("导入将新建一个账本并切换过去，当前账本数据保留。是否继续选择 CSV？")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showImportLedgerPrompt = false
                        openDocument.launch(arrayOf("text/*", "text/csv", "*/*"))
                    },
                ) {
                    Text("继续导入")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportLedgerPrompt = false }) {
                    Text("取消")
                }
            },
        )
    }

    deleteTarget?.let { target ->
        val copy = viewModel.deleteDialogCopy(target)
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(copy.title) },
            text = { Text(copy.body) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteProject(target.id)
                        deleteTarget = null
                    },
                ) {
                    Text(copy.confirm)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("取消")
                }
            },
        )
    }

    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = { Text("我的") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "设置")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenProfile)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        ProfileAvatar(avatarPath = uiState.profile.avatarPath, size = 56.dp)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = uiState.profile.nickname,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "点击进入个人中心",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
                        text = uiState.projectName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "项目成员",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (uiState.members.isEmpty()) {
                        Text(text = "暂无成员", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        uiState.members.forEach { member ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (member.isSelf) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.secondaryContainer),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        ProfileAvatar(
                                            avatarPath = uiState.profile.avatarPath,
                                            size = 36.dp,
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Person,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = member.nickname,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    val roleLabel = when (member.role?.uppercase()) {
                                        "OWNER" -> "拥有者"
                                        "EDITOR" -> "协助者"
                                        else -> null
                                    }
                                    if (roleLabel != null) {
                                        Text(
                                            text = roleLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "账本管理",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                    )
                    uiState.visibleLedgers.forEach { ledger ->
                        val project = ledger.project
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = ledger.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { deleteTarget = project }) {
                                Icon(
                                    Icons.Outlined.DeleteOutline,
                                    contentDescription = "移入垃圾箱",
                                )
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = onOpenTrash,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("垃圾箱")
                    }
                }
            }

            if (uiState.showHealthColorSettings) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        text = "预算健康色",
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    Box {
                                        IconButton(
                                            onClick = { showHealthColorHelp = true },
                                            modifier = Modifier.size(28.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                                                contentDescription = "查看预算健康色说明",
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        if (showHealthColorHelp) {
                                            HealthColorHelpPopup(
                                                mildOverMaxPercent = uiState.mildOverMaxPercent,
                                                onDismiss = { showHealthColorHelp = false },
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "超支时以绿/橙/红提示",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = uiState.healthColorEnabled,
                                onCheckedChange = viewModel::setHealthColorEnabled,
                            )
                        }
                        if (uiState.healthColorEnabled) {
                            MildOverPercentSlider(
                                percent = uiState.mildOverMaxPercent,
                                onPercentChange = viewModel::setMildOverMaxPercent,
                            )
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "标签",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "维护阶段 / 分类 / 空间，录入与编辑时使用",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = onOpenTaxonomyManage,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("标签管理")
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "数据",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                    )
                    Button(
                        onClick = { viewModel.exportAndShare(context) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("导出 CSV")
                    }
                    OutlinedButton(
                        onClick = { showImportLedgerPrompt = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("从文件导入")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MildOverPercentSlider(
    percent: Int,
    onPercentChange: (Int) -> Unit,
) {
    val context = LocalContext.current
    var sliderValue by remember {
        mutableFloatStateOf(percent.toFloat())
    }
    LaunchedEffect(percent) {
        if (kotlin.math.abs(sliderValue - percent) >= 0.5f) {
            sliderValue = percent.toFloat()
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "轻度超支上限",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "${sliderValue.roundToInt()}%",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = sliderValue,
            onValueChange = { newValue ->
                val oldPercent = sliderValue.roundToInt()
                val newPercent = newValue.roundToInt()
                sliderValue = newValue
                if (newPercent != oldPercent) {
                    context.performSliderTick()
                }
            },
            onValueChangeFinished = {
                onPercentChange(sliderValue.roundToInt())
            },
            valueRange = HealthColorResolver.MIN_MILD_OVER_MAX_PERCENT.toFloat()..
                HealthColorResolver.MAX_MILD_OVER_MAX_PERCENT.toFloat(),
            steps = HealthColorResolver.MAX_MILD_OVER_MAX_PERCENT -
                HealthColorResolver.MIN_MILD_OVER_MAX_PERCENT - 1,
        )
        Text(
            text = "超支不超过该比例显示橙色，超过则红色（可调 1%～100%）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HealthColorHelpPopup(
    mildOverMaxPercent: Int,
    onDismiss: () -> Unit,
) {
    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(0, 36),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .padding(end = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "预算健康色说明",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "分档规则\n" +
                        "· 绿色：未超支（预算内 / 节余）\n" +
                        "· 橙色：超支，但不超过预算的 ${mildOverMaxPercent}%\n" +
                        "· 红色：超支超过预算的 ${mildOverMaxPercent}%\n" +
                        "· 轻度超支上限可在下方滑条调整（默认 15%，范围 1%～100%）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "会用颜色提示超支的位置\n" +
                        "· 首页「已花费」下方的超支 / 节余\n" +
                        "· 首页「预计花费」的超支 / 节余\n" +
                        "· 统计「分组明细」的超支金额\n" +
                        "· 统计「合同超预算 TOP5」的差额\n" +
                        "· 全局页面背景与底部 Tab 会随健康档变色",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "关闭后仍显示超支数字，只是不再用绿/橙/红染色。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("知道了")
                }
            }
        }
    }
}
