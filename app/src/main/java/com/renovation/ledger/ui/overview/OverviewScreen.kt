package com.renovation.ledger.ui.overview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.PendingActions
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import com.renovation.ledger.ui.common.ZeroTopAppBarWindowInsets
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.renovation.ledger.domain.metrics.ProjectMetrics
import com.renovation.ledger.domain.model.BudgetItem
import com.renovation.ledger.domain.model.HealthLevel
import com.renovation.ledger.domain.model.PaymentStatus
import com.renovation.ledger.domain.model.Project
import com.renovation.ledger.domain.model.effectiveCost
import com.renovation.ledger.domain.model.label
import com.renovation.ledger.ui.common.formatYuan
import com.renovation.ledger.ui.common.overspendHintColor
import com.renovation.ledger.ui.common.progressPercentColor
import com.renovation.ledger.ui.entry.EntryChooserSheet
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
    viewModel: OverviewViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val expandUi by viewModel.expandUiState.collectAsStateWithLifecycle()
    var showEntryChooser by remember { mutableStateOf(false) }
    var showCreateLedger by remember { mutableStateOf(false) }
    var newLedgerName by remember { mutableStateOf("新账本") }
    var renameTarget by remember { mutableStateOf<Project?>(null) }
    var renameLedgerName by remember { mutableStateOf("") }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    if (showEntryChooser) {
        EntryChooserSheet(
            onDismiss = { showEntryChooser = false },
            onManualEntry = onOpenManualEntry,
            onVoiceEntry = { onOpenConfirmEntry("voice") },
            onImageEntry = { onOpenConfirmEntry("image") },
        )
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
                onCreate = {
                    newLedgerName = "新账本"
                    showCreateLedger = true
                },
            )
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    windowInsets = ZeroTopAppBarWindowInsets,
                title = { Text("总览") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Outlined.Menu, contentDescription = "账本列表")
                        }
                    },
                    actions = {
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
                            text = formatYuan(uiState.metrics.totalBudget),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                PaidPendingRow(
                    metrics = uiState.metrics,
                    currentHealth = uiState.currentHealth,
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

                ProjectedSpendCard(
                    metrics = uiState.metrics,
                    projectedHealth = uiState.projectedHealth,
                )

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        BudgetProgressSection(
                            metrics = uiState.metrics,
                            currentHealth = uiState.currentHealth,
                            projectedHealth = uiState.projectedHealth,
                        )
                    }
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
    onCreate: () -> Unit,
) {
    ModalDrawerSheet {
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
    currentHealth: HealthLevel,
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
            label = "已实付 ${if (paidExpanded) "▴" else "▾"}",
            amount = metrics.paidActual,
            subtitle = overspendLabel(metrics.currentOverspend),
            subtitleColor = overspendHintColor(metrics.currentOverspend, currentHealth),
        )
        MetricCard(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            onClick = onTogglePending,
            icon = Icons.Outlined.PendingActions,
            label = "待花费 ${if (pendingExpanded) "▴" else "▾"}",
            amount = metrics.pendingSpend,
            subtitle = "尾款 ${formatYuan(metrics.unpaidFinal)}\n待购 ${formatYuan(metrics.toBuyAmount)}",
            subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant,
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
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (selectedTab == 0) {
                    PendingListSection(
                        rows = overspendRows.take(5).map {
                            PendingRowUi(it.itemId, it.itemName, it.gapAmount, amountPrefix = "+")
                        },
                        remainingCount = (overspendRows.size - 5).coerceAtLeast(0),
                        emptyHint = "暂无单项超支（已付未超预算）",
                        onOpenItem = onOpenItem,
                    )
                } else {
                    PendingListSection(
                        rows = surplusRows.take(5).map {
                            PendingRowUi(it.itemId, it.itemName, it.gapAmount, amountPrefix = "-")
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
private fun MetricCard(
    icon: ImageVector,
    label: String,
    amount: Long,
    subtitle: String,
    subtitleColor: androidx.compose.ui.graphics.Color,
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
            // 固定两行高度，保证「已实付 / 待花费」卡片对齐
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = subtitleColor,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
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
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { onTabSelected(0) },
                    text = { Text("待付尾款") },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { onTabSelected(1) },
                    text = { Text("待购买") },
                )
            }
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (selectedTab == 0) {
                    PendingListSection(
                        rows = unpaidFinalRows.take(5).map {
                            PendingRowUi(it.itemId, it.itemName, it.unpaidAmount)
                        },
                        remainingCount = (unpaidFinalRows.size - 5).coerceAtLeast(0),
                        emptyHint = "暂无待付尾款",
                        onOpenItem = onOpenItem,
                    )
                } else {
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
private fun ProjectedSpendCard(
    metrics: ProjectMetrics,
    projectedHealth: HealthLevel,
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.TrendingUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "预计花费",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
            }
            Text(
                text = "如果全部买完，预计花费",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatYuan(metrics.projectedTotal),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = overspendLabel(metrics.projectedOverspend),
                style = MaterialTheme.typography.bodyMedium,
                color = overspendHintColor(metrics.projectedOverspend, projectedHealth),
            )
        }
    }
}

@Composable
private fun BudgetProgressSection(
    metrics: ProjectMetrics,
    currentHealth: HealthLevel,
    projectedHealth: HealthLevel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ProgressRow(
            label = "预算执行",
            helpTitle = "预算执行是什么？",
            helpMessage = "预算执行 = 已实付 ÷ 总预算。\n\n" +
                "表示到目前为止，已经实际付出去的钱占装修总预算的比例。只看「花出去多少了」，不含还没付的尾款和还没买的东西。",
            numerator = metrics.paidActual,
            denominator = metrics.totalBudget,
            health = currentHealth,
        )
        ProgressRow(
            label = "预计执行",
            helpTitle = "预计执行是什么？",
            helpMessage = "预计执行 = 预计总花费 ÷ 总预算。\n\n" +
                "表示如果清单上的项目都按合同价（没有合同价则按预算）买完、付完，最终花费将占总预算的比例。用来提前看整体会不会超支。",
            numerator = metrics.projectedTotal,
            denominator = metrics.totalBudget,
            health = projectedHealth,
        )
    }
}

@Composable
private fun ProgressRow(
    label: String,
    helpTitle: String,
    helpMessage: String,
    numerator: Long,
    denominator: Long,
    health: HealthLevel,
) {
    var showHelp by remember { mutableStateOf(false) }
    val percent = if (denominator > 0L) {
        (numerator.toDouble() / denominator.toDouble() * 100).toInt()
    } else {
        0
    }
    val progress = if (denominator > 0L) {
        (numerator.toFloat() / denominator.toFloat()).coerceAtMost(1f)
    } else {
        0f
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(text = label, style = MaterialTheme.typography.labelMedium)
                IconButton(
                    onClick = { showHelp = true },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                        contentDescription = "查看${label}说明",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.labelMedium,
                color = progressPercentColor(percent, health),
                fontWeight = if (percent > 100) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
    }
    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text(helpTitle) },
            text = { Text(helpMessage) },
            confirmButton = {
                TextButton(onClick = { showHelp = false }) {
                    Text("知道了")
                }
            },
        )
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
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 预算项目
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "预算项",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = row.itemName.ifBlank { "未命名" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "预算",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatYuan(row.budgetAmount),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            )

            // 实际付款
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "实际付款",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MetaChip(text = payment.type.label())
                        MetaChip(
                            text = paymentStatusLabel(payment.status),
                            emphasize = payment.status == PaymentStatus.UNPAID,
                        )
                        if (payment.createdBy.isNotBlank()) {
                            Text(
                                text = payment.createdBy,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        payment.paidAtEpochMs?.let { epoch ->
                            Text(
                                text = formatPaymentDate(epoch),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "实付",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatYuan(payment.amount),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (payment.status == PaymentStatus.PAID) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
            if (payment.note.isNotBlank()) {
                Text(
                    text = payment.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MetaChip(
    text: String,
    emphasize: Boolean = false,
) {
    val background = if (emphasize) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val foreground = if (emphasize) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = background),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = foreground,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

private fun paymentStatusLabel(status: PaymentStatus): String = when (status) {
    PaymentStatus.PAID -> "已付"
    PaymentStatus.UNPAID -> "未付"
}

private fun formatPaymentDate(epochMs: Long): String =
    SimpleDateFormat("MM-dd", Locale.CHINA).format(Date(epochMs))

private fun overspendLabel(overspend: Long): String = when {
    overspend > 0L -> "超支 ${formatYuan(overspend)}"
    overspend < 0L -> "节余 ${formatYuan(abs(overspend))}"
    else -> "与预算持平"
}
