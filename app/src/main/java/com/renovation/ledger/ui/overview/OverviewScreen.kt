package com.renovation.ledger.ui.overview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PendingActions
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.renovation.ledger.ui.common.CompactTopAppBar
import com.renovation.ledger.ui.common.ZeroTopAppBarWindowInsets
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.renovation.ledger.data.auth.WeChatAppAuth
import com.renovation.ledger.domain.metrics.ProjectMetrics
import com.renovation.ledger.domain.metrics.ProjectedSpendPercent
import com.renovation.ledger.domain.model.BudgetItem
import com.renovation.ledger.domain.model.HealthLevel
import com.renovation.ledger.domain.model.Project
import com.renovation.ledger.domain.model.effectiveCost
import com.renovation.ledger.ui.common.HealthGreen
import com.renovation.ledger.ui.common.HealthRed
import com.renovation.ledger.ui.common.formatYuan
import com.renovation.ledger.ui.common.overspendHintColor
import com.renovation.ledger.ui.entry.EntryChooserSheet
import com.renovation.ledger.voice.ui.VoiceAssistantMode
import com.renovation.ledger.voice.ui.VoiceAssistantSheet
import com.renovation.ledger.voice.ui.VoiceAssistantViewModel
import com.renovation.ledger.voice.ui.VoiceConfirmDialog
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    onOpenPending: (initialTab: String) -> Unit,
    onOpenPaidGap: (initialTab: String) -> Unit,
    onOpenManualEntry: () -> Unit,
    onOpenConfirmEntry: (String) -> Unit,
    onOpenItem: (String) -> Unit,
    onOpenSearch: () -> Unit,
    viewModel: OverviewViewModel = hiltViewModel(),
    voiceViewModel: VoiceAssistantViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val expandUi by viewModel.expandUiState.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
    val voiceUiState by voiceViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { WeChatAppAuth.findActivity(context) }
    var showEntryChooser by remember { mutableStateOf(false) }
    var showCreateLedger by remember { mutableStateOf(false) }
    var newLedgerName by remember { mutableStateOf("新账本") }
    var renameTarget by remember { mutableStateOf<Project?>(null) }
    var renameLedgerName by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<Project?>(null) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            voiceViewModel.startVoice()
        } else {
            Toast.makeText(context, "需要麦克风权限才能使用语音助手", Toast.LENGTH_SHORT).show()
        }
    }

    fun startVoiceAssistant() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            voiceViewModel.startVoice()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(activity) {
        voiceViewModel.attachHost(activity)
        onDispose { voiceViewModel.attachHost(null) }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.pullFromCloud()
        }
    }

    LaunchedEffect(userMessage) {
        val message = userMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.clearUserMessage()
    }

    LaunchedEffect(voiceUiState.snackMessage) {
        val message = voiceUiState.snackMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        voiceViewModel.clearSnack()
    }

    if (showEntryChooser) {
        EntryChooserSheet(
            onDismiss = { showEntryChooser = false },
            onManualEntry = onOpenManualEntry,
            onVoiceEntry = {
                showEntryChooser = false
                startVoiceAssistant()
            },
            onImageEntry = { onOpenConfirmEntry("image") },
        )
    }

    if (voiceUiState.visible) {
        VoiceAssistantSheet(
            state = voiceUiState,
            onDismiss = voiceViewModel::dismiss,
            onRetry = voiceViewModel::startVoice,
            onTranscriptChange = voiceViewModel::updateTranscript,
            onSubmitEditedTranscript = voiceViewModel::submitEditedTranscript,
            onUseTypedInput = voiceViewModel::useTypedInput,
            onHoldStart = voiceViewModel::onHoldStart,
            onHoldEnd = voiceViewModel::onHoldEnd,
        )
    }

    voiceUiState.confirmPreview?.let { preview ->
        if (voiceUiState.mode == VoiceAssistantMode.NEED_CONFIRM) {
            VoiceConfirmDialog(
                preview = preview,
                onCancel = voiceViewModel::cancelConfirm,
                onConfirm = voiceViewModel::confirmCurrent,
            )
        }
    }

    if (showCreateLedger) {
        AlertDialog(
            onDismissRequest = { showCreateLedger = false },
            title = { Text("新建账本") },
            text = {
                OutlinedTextField(
                    value = newLedgerName,
                    onValueChange = { newLedgerName = it },
                    singleLine = true,
                    label = { Text("账本名称") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.createProject(newLedgerName)
                        showCreateLedger = false
                        newLedgerName = "新账本"
                        scope.launch { drawerState.close() }
                    },
                ) {
                    Text("创建")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateLedger = false }) {
                    Text("取消")
                }
            },
        )
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("修改账本名称") },
            text = {
                OutlinedTextField(
                    value = renameLedgerName,
                    onValueChange = { renameLedgerName = it },
                    singleLine = true,
                    label = { Text("账本名称") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.renameProject(target.id, renameLedgerName)
                        renameTarget = null
                    },
                    enabled = renameLedgerName.trim().isNotEmpty(),
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
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
                        scope.launch { drawerState.close() }
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

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            LedgerDrawerContent(
                projects = uiState.projects,
                currentProjectId = uiState.projectId,
                onSelect = { id ->
                    viewModel.switchProject(id)
                    scope.launch { drawerState.close() }
                },
                onRename = { project ->
                    renameTarget = project
                    renameLedgerName = project.name
                },
                onDelete = { project -> deleteTarget = project },
                onCreate = {
                    newLedgerName = "新账本"
                    showCreateLedger = true
                },
            )
        },
    ) {
        Scaffold(
            topBar = {
                CompactTopAppBar(
                title = { Text("总览") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Outlined.Menu, contentDescription = "账本列表")
                        }
                    },
                    actions = {
                        IconButton(onClick = onOpenSearch) {
                            Icon(Icons.Outlined.Search, contentDescription = "搜索")
                        }
                        Text(
                            text = uiState.projectName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .clickable {
                                    val current = uiState.projects.find { it.id == uiState.projectId }
                                        ?: Project(
                                            id = uiState.projectId,
                                            name = uiState.projectName,
                                            memberNames = emptyList(),
                                        )
                                    renameTarget = current
                                    renameLedgerName = current.name
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    },
                )
            },
            floatingActionButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    SmallFloatingActionButton(
                        onClick = { startVoiceAssistant() },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Mic,
                            contentDescription = "语音助手",
                        )
                    }
                    ExtendedFloatingActionButton(
                        onClick = { showEntryChooser = true },
                        modifier = Modifier.height(48.dp),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        icon = {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        text = {
                            Text(
                                text = "记一笔",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        },
                    )
                }
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
                if (uiState.memberNames.isNotBlank()) {
                    Text(
                        text = uiState.memberNames,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                BudgetSummaryCard(
                    metrics = uiState.metrics,
                    projectedHealth = uiState.projectedHealth,
                )

                PaidPendingRow(
                    metrics = uiState.metrics,
                    overspendGapTotal = uiState.overspendRows.sumOf { it.gapAmount },
                    surplusGapTotal = uiState.surplusRows.sumOf { it.gapAmount },
                    paidExpanded = expandUi.paidExpanded,
                    pendingExpanded = expandUi.pendingExpanded,
                    onTogglePaid = viewModel::togglePaidExpanded,
                    onTogglePending = viewModel::togglePendingExpanded,
                )

                if (expandUi.paidExpanded) {
                    PaidGapExpandedTabs(
                        selectedTab = expandUi.paidTab,
                        onTabSelected = viewModel::setPaidTab,
                        overspendRows = uiState.overspendRows,
                        surplusRows = uiState.surplusRows,
                        onOpenItem = onOpenItem,
                        onOpenPaidGap = {
                            val tab = if (expandUi.paidTab == 0) "overspend" else "surplus"
                            onOpenPaidGap(tab)
                        },
                    )
                }

                if (expandUi.pendingExpanded) {
                    PendingExpandedTabs(
                        selectedTab = expandUi.pendingTab,
                        onTabSelected = viewModel::setPendingTab,
                        unpaidFinalRows = uiState.unpaidFinalRows,
                        toBuyItems = uiState.toBuyItems,
                        onOpenItem = onOpenItem,
                        onOpenPending = {
                            val tab = if (expandUi.pendingTab == 0) "unpaid" else "tobuy"
                            onOpenPending(tab)
                        },
                    )
                }

                RecentPaymentsSection(
                    recentPayments = uiState.recentPayments,
                    onOpenItem = onOpenItem,
                )

                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }
}

@Composable
private fun LedgerDrawerContent(
    projects: List<Project>,
    currentProjectId: String,
    onSelect: (String) -> Unit,
    onRename: (Project) -> Unit,
    onDelete: (Project) -> Unit,
    onCreate: () -> Unit,
) {
    ModalDrawerSheet(
        modifier = Modifier.fillMaxWidth(3f / 4f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 16.dp),
        ) {
            Text(
                text = "我的账本",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            projects.forEach { project ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NavigationDrawerItem(
                        label = { Text(project.name) },
                        selected = project.id == currentProjectId,
                        onClick = { onSelect(project.id) },
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 4.dp),
                    )
                    IconButton(onClick = { onRename(project) }) {
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = "修改名称",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    IconButton(onClick = { onDelete(project) }) {
                        Icon(
                            Icons.Outlined.DeleteOutline,
                            contentDescription = "移入垃圾箱",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            TextButton(
                onClick = onCreate,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            ) {
                Text("+ 新建账本")
            }
        }
    }
}

@Composable
private fun PaidPendingRow(
    metrics: ProjectMetrics,
    overspendGapTotal: Long,
    surplusGapTotal: Long,
    paidExpanded: Boolean,
    pendingExpanded: Boolean,
    onTogglePaid: () -> Unit,
    onTogglePending: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MetricCard(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            onClick = onTogglePaid,
            icon = Icons.Outlined.AccountBalanceWallet,
            label = "已花费 ${if (paidExpanded) "▴" else "▾"}",
            amount = metrics.paidActual,
            subtitle = {
                Text(
                    text = "超支 ${formatYuan(overspendGapTotal)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = HealthRed,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "节余 ${formatYuan(surplusGapTotal)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = HealthGreen,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
        )
        MetricCard(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            onClick = onTogglePending,
            icon = Icons.Outlined.PendingActions,
            label = "待花费 ${if (pendingExpanded) "▴" else "▾"}",
            amount = metrics.pendingSpend,
            subtitle = {
                Text(
                    text = "尾款 ${formatYuan(metrics.unpaidFinal)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "待购 ${formatYuan(metrics.toBuyAmount)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
        )
    }
}

@Composable
private fun PaidGapExpandedTabs(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    overspendRows: List<PaidBudgetGapRow>,
    surplusRows: List<PaidBudgetGapRow>,
    onOpenItem: (String) -> Unit,
    onOpenPaidGap: () -> Unit,
) {
    val overspendTotal = overspendRows.sumOf { it.gapAmount }
    val surplusTotal = surplusRows.sumOf { it.gapAmount }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { onTabSelected(0) },
                    text = { Text("超支项（${overspendRows.size}）") },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { onTabSelected(1) },
                    text = { Text("节余项（${surplusRows.size}）") },
                )
            }
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (selectedTab == 0) {
                    GapTotalQuietLine(
                        title = "超支合计",
                        amount = overspendTotal,
                        amountColor = HealthRed,
                    )
                    PendingListSection(
                        rows = overspendRows.take(5).map {
                            PendingRowUi(
                                id = it.itemId,
                                name = it.itemName,
                                amount = it.gapAmount,
                                amountPrefix = "+",
                                amountColor = HealthRed,
                            )
                        },
                        remainingCount = (overspendRows.size - 5).coerceAtLeast(0),
                        emptyHint = "暂无单项超支（已付未超预算）",
                        onOpenItem = onOpenItem,
                    )
                } else {
                    GapTotalQuietLine(
                        title = "节余合计",
                        amount = surplusTotal,
                        amountColor = HealthGreen,
                    )
                    PendingListSection(
                        rows = surplusRows.take(5).map {
                            PendingRowUi(
                                id = it.itemId,
                                name = it.itemName,
                                amount = it.gapAmount,
                                amountPrefix = "-",
                                amountColor = HealthGreen,
                            )
                        },
                        remainingCount = (surplusRows.size - 5).coerceAtLeast(0),
                        emptyHint = "暂无单项节余（已结清且未花满预算）",
                        onOpenItem = onOpenItem,
                    )
                }
                TextButton(
                    onClick = onOpenPaidGap,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 32.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Text(
                        text = "查看全部明细 ›",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun GapTotalQuietLine(
    title: String,
    amount: Long,
    amountColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = formatYuan(amount),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = amountColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MetricCard(
    icon: ImageVector,
    label: String,
    amount: Long,
    subtitle: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = formatYuan(amount),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Column(modifier = Modifier.heightIn(min = 36.dp)) {
                subtitle()
            }
        }
    }
}

@Composable
private fun PendingExpandedTabs(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    unpaidFinalRows: List<UnpaidFinalRow>,
    toBuyItems: List<BudgetItem>,
    onOpenItem: (String) -> Unit,
    onOpenPending: () -> Unit,
) {
    val unpaidFinalTotal = unpaidFinalRows.sumOf { it.unpaidAmount }
    val toBuyTotal = toBuyItems.sumOf { it.effectiveCost() }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { onTabSelected(0) },
                    text = { Text("待付尾款（${unpaidFinalRows.size}）") },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { onTabSelected(1) },
                    text = { Text("待购买（${toBuyItems.size}）") },
                )
            }
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (selectedTab == 0) {
                    GapTotalQuietLine(
                        title = "待付尾款合计",
                        amount = unpaidFinalTotal,
                        amountColor = MaterialTheme.colorScheme.onSurface,
                    )
                    PendingListSection(
                        rows = unpaidFinalRows.take(5).map {
                            PendingRowUi(it.itemId, it.itemName, it.unpaidAmount)
                        },
                        remainingCount = (unpaidFinalRows.size - 5).coerceAtLeast(0),
                        emptyHint = "暂无待付尾款",
                        onOpenItem = onOpenItem,
                    )
                } else {
                    GapTotalQuietLine(
                        title = "待购买合计",
                        amount = toBuyTotal,
                        amountColor = MaterialTheme.colorScheme.onSurface,
                    )
                    PendingListSection(
                        rows = toBuyItems.take(5).map {
                            PendingRowUi(it.id, it.name, it.effectiveCost())
                        },
                        remainingCount = (toBuyItems.size - 5).coerceAtLeast(0),
                        emptyHint = "暂无待购买项",
                        onOpenItem = onOpenItem,
                    )
                }
                TextButton(
                    onClick = onOpenPending,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 32.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Text(
                        text = "查看全部明细 ›",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

private data class PendingRowUi(
    val id: String,
    val name: String,
    val amount: Long,
    /** 金额前缀，如超支「+」、节余「-」。 */
    val amountPrefix: String = "",
    val amountColor: Color? = null,
)

@Composable
private fun PendingListSection(
    rows: List<PendingRowUi>,
    remainingCount: Int,
    emptyHint: String,
    onOpenItem: (String) -> Unit,
) {
    if (rows.isEmpty() && remainingCount == 0) {
        Text(
            text = emptyHint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    rows.forEach { row ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenItem(row.id) }
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = row.name,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = row.amountPrefix + formatYuan(row.amount),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (row.amountColor != null) FontWeight.SemiBold else FontWeight.Normal,
                color = row.amountColor ?: MaterialTheme.colorScheme.onSurface,
            )
        }
    }
    if (remainingCount > 0) {
        Text(
            text = "…还有 $remainingCount 项",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BudgetSummaryCard(
    metrics: ProjectMetrics,
    projectedHealth: HealthLevel,
) {
    val projected = ProjectedSpendPercent.compute(metrics.projectedTotal, metrics.totalBudget)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "总预算",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatYuan(metrics.totalBudget),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.TrendingUp,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "预计花费",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (projected.percent != null) {
                    Text(
                        text = projected.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = overspendHintColor(metrics.projectedOverspend, projectedHealth),
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatYuan(metrics.projectedTotal),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (projected.gap != 0L) {
                Text(
                    text = overspendLabel(projected.gap),
                    style = MaterialTheme.typography.bodySmall,
                    color = overspendHintColor(metrics.projectedOverspend, projectedHealth),
                )
            }
        }
    }
}

@Composable
private fun RecentPaymentsSection(
    recentPayments: List<RecentPaymentRow>,
    onOpenItem: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "最近记账",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
        )
        if (recentPayments.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            ) {
                Text(
                    text = "暂无记账记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            recentPayments.forEach { row ->
                RecentPaymentItemCard(
                    row = row,
                    onClick = { onOpenItem(row.itemId) },
                )
            }
        }
    }
}

@Composable
private fun RecentPaymentItemCard(
    row: RecentPaymentRow,
    onClick: () -> Unit,
) {
    val payment = row.payment
    val actualChanged = row.actualAmount != row.budgetAmount
    val dateText = row.recordedDate ?: payment.paidAtEpochMs?.let { formatPaymentDate(it) }.orEmpty()
    val cardBackground = Color(0xFFFFF7EA)
    val borderColor = Color(0xFFF0D9B5)
    val paidColor = Color(0xFFE86F00)
    val statusBackground = if (row.statusText == "已结清") Color(0xFFE8F5E9) else Color(0xFFFFF1D6)
    val statusForeground = if (row.statusText == "已结清") Color(0xFF2E7D32) else Color(0xFF9A5B00)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = cardBackground,
        ),
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = row.itemName.ifBlank { "未命名" },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1F1A14),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (row.isNewAddition) {
                            InfoPill(
                                text = "新增",
                                background = Color(0xFFFFE8B8),
                                foreground = Color(0xFF6B4500),
                            )
                        }
                    }
                    if (dateText.isNotBlank()) {
                        Text(
                            text = dateText,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF6F665B),
                        )
                    }
                    Text(
                        text = "分类 ${row.category.ifBlank { "未分类" }}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF6F665B),
                    )
                }
                InfoPill(
                    text = row.statusText,
                    background = statusBackground,
                    foreground = statusForeground,
                )
            }

            HorizontalDivider(color = borderColor.copy(alpha = 0.8f))

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "预算金额",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF6F665B),
                )
                Text(
                    text = if (actualChanged) {
                        "${formatYuan(row.budgetAmount)} → ${formatYuan(row.actualAmount)}"
                    } else {
                        formatYuan(row.budgetAmount)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1F1A14),
                )
                BudgetGapPercentText(
                    budgetAmount = row.budgetAmount,
                    actualAmount = row.actualAmount,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column {
                    Text(
                        text = "已付",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF6F665B),
                    )
                    Text(
                        text = formatYuan(row.paidAmount),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = paidColor,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "未付",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF6F665B),
                    )
                    Text(
                        text = formatYuan(row.unpaidAmount),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1F1A14),
                    )
                }
            }
        }
    }
}

@Composable
private fun BudgetGapPercentText(
    budgetAmount: Long,
    actualAmount: Long,
) {
    val gap = actualAmount - budgetAmount
    if (budgetAmount <= 0L || gap == 0L) return
    val percent = abs(gap).toDouble() * 100.0 / budgetAmount.toDouble()
    val isOver = gap > 0L
    Text(
        text = "${if (isOver) "超支" else "节余"} ${formatPercent(percent)}",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = if (isOver) Color(0xFFC62828) else Color(0xFF2E7D32),
    )
}

private fun formatPercent(value: Double): String {
    val rounded = String.format(Locale.CHINA, "%.1f", value)
    return rounded.removeSuffix(".0") + "%"
}

@Composable
private fun InfoPill(
    text: String,
    background: Color,
    foreground: Color,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = foreground,
        )
    }
}

private fun formatPaymentDate(epochMs: Long): String =
    SimpleDateFormat("MM-dd", Locale.CHINA).format(Date(epochMs))

private fun overspendLabel(overspend: Long): String = when {
    overspend > 0L -> "超支 ${formatYuan(overspend)}"
    overspend < 0L -> "节余 ${formatYuan(abs(overspend))}"
    else -> "与预算持平"
}
