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
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
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
import android.widget.Toast
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.renovation.ledger.domain.metrics.ProjectMetrics
import com.renovation.ledger.domain.model.BudgetItem
import com.renovation.ledger.domain.model.HealthLevel
import com.renovation.ledger.domain.model.Project
import com.renovation.ledger.domain.model.effectiveCost
import com.renovation.ledger.ui.common.HealthGreen
import com.renovation.ledger.ui.common.HealthRed
import com.renovation.ledger.ui.common.formatYuan
import com.renovation.ledger.ui.common.overspendHintColor
import com.renovation.ledger.ui.common.progressPercentColor
import com.renovation.ledger.ui.entry.EntryChooserSheet
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
    viewModel: OverviewViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val expandUi by viewModel.expandUiState.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showEntryChooser by remember { mutableStateOf(false) }
    var showCreateLedger by remember { mutableStateOf(false) }
    var newLedgerName by remember { mutableStateOf("新账本") }
    var renameTarget by remember { mutableStateOf<Project?>(null) }
    var renameLedgerName by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<Project?>(null) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    LaunchedEffect(userMessage) {
        val message = userMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.clearUserMessage()
    }

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
    onDelete: (Project) -> Unit,
    onCreate: () -> Unit,
) {
    ModalDrawerSheet(
        modifier = Modifier.fillMaxWidth(2f / 3f),
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
            helpMessage = "预算执行 = 已花费 ÷ 总预算。\n\n" +
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
