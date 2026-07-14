package com.renovation.ledger.ui.mine

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.material3.TopAppBar
import com.renovation.ledger.ui.common.ZeroTopAppBarWindowInsets
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import com.renovation.ledger.ui.common.ClearableOutlinedTextField
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
    viewModel: MineViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showHealthColorHelp by remember { mutableStateOf(false) }
    var editingMemberIndex by remember { mutableStateOf<Int?>(null) }
    var memberDraft by remember { mutableStateOf("") }
    var showAddMember by remember { mutableStateOf(false) }
    var addMemberDraft by remember { mutableStateOf("") }
    var showImportLedgerPrompt by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Project?>(null) }

    LaunchedEffect(uiState.actionMessage) {
        val message = uiState.actionMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
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
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("移入垃圾箱") },
            text = {
                Text(
                    "将「${target.name}」移入垃圾箱。\n" +
                        "会先导出备份，之后可从垃圾箱恢复；永久删除前仍可找回。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteProject(target.id)
                        deleteTarget = null
                    },
                ) {
                    Text("移入垃圾箱")
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
            TopAppBar(
                windowInsets = ZeroTopAppBarWindowInsets,
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
                        .clickable(onClick = onOpenSettings)
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
                            text = "点击进入设置修改昵称与头像",
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
                    if (uiState.memberNames.isEmpty()) {
                        Text(text = "暂无成员", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        uiState.memberNames.forEachIndexed { index, name ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        editingMemberIndex = index
                                        memberDraft = name
                                    }
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (name == uiState.profile.nickname) {
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
                                    Text(text = name, style = MaterialTheme.typography.bodyLarge)
                                }
                                Text(
                                    text = "改昵称",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    TextButton(onClick = { showAddMember = true }) {
                        Text("＋添加成员")
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
                    uiState.projects.forEach { project ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = project.name,
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

    editingMemberIndex?.let { index ->
        AlertDialog(
            onDismissRequest = { editingMemberIndex = null },
            title = { Text("修改成员昵称") },
            text = {
                ClearableOutlinedTextField(
                    value = memberDraft,
                    onValueChange = { memberDraft = it },
                    label = { Text("昵称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateMemberNickname(index, memberDraft)
                        editingMemberIndex = null
                    },
                    enabled = memberDraft.isNotBlank(),
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingMemberIndex = null }) {
                    Text("取消")
                }
            },
        )
    }

    if (showAddMember) {
        AlertDialog(
            onDismissRequest = { showAddMember = false },
            title = { Text("添加成员") },
            text = {
                ClearableOutlinedTextField(
                    value = addMemberDraft,
                    onValueChange = { addMemberDraft = it },
                    label = { Text("昵称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addMember(addMemberDraft)
                        addMemberDraft = ""
                        showAddMember = false
                    },
                    enabled = addMemberDraft.isNotBlank(),
                ) {
                    Text("添加")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddMember = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun ProfileAvatar(
    avatarPath: String?,
    size: androidx.compose.ui.unit.Dp = 88.dp,
) {
    val bitmap = remember(avatarPath) {
        avatarPath
            ?.takeIf { it.isNotBlank() }
            ?.let { path -> runCatching { BitmapFactory.decodeFile(path) }.getOrNull() }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "头像",
            modifier = Modifier
                .size(size)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    } else {
        Icon(
            imageVector = Icons.Outlined.Person,
            contentDescription = "默认头像",
            modifier = Modifier.size(size * 0.45f),
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
        )
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
                        "· 首页「已实付」下方的超支 / 节余\n" +
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
