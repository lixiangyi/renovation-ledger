package com.renovation.ledger.ui.list

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.renovation.ledger.ui.common.CompactTopAppBar
import com.renovation.ledger.ui.common.ZeroTopAppBarWindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.renovation.ledger.domain.list.FilterTabStats
import com.renovation.ledger.domain.ledger.LedgerContentGate
import com.renovation.ledger.domain.list.PaymentListGroupBy
import com.renovation.ledger.domain.list.PaymentListLayout
import com.renovation.ledger.domain.model.ItemStatus
import com.renovation.ledger.ui.common.TaxonomyIconView
import com.renovation.ledger.ui.common.formatYuan
import com.renovation.ledger.ui.common.overspendHintColor
import com.renovation.ledger.ui.common.progressPercentColor

private const val StageExpandAnimMs = 220
private const val StageFadeInMs = 180
private const val StageFadeOutMs = 160

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetListScreen(
    onOpenItem: (String) -> Unit,
    onOpenManualEntry: () -> Unit,
    viewModel: BudgetListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = { Text("支付清单") },
                actions = {
                    Surface(
                        onClick = onOpenManualEntry,
                        modifier = Modifier.padding(end = 12.dp),
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = "新增",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            FilterRow(
                selected = uiState.filter,
                tabStats = uiState.tabStats,
                onSelect = viewModel::setFilter,
            )
            ListControlsRow(
                groupBy = uiState.groupBy,
                layout = uiState.layout,
                onGroupBySelect = viewModel::setGroupBy,
                onLayoutSelect = viewModel::setLayout,
            )
            FilterTotalAmountBar(
                amountSum = when (uiState.filter) {
                    BudgetListFilter.ALL -> uiState.tabStats.all.amountSum
                    BudgetListFilter.TO_BUY -> uiState.tabStats.toBuy.amountSum
                    BudgetListFilter.PAYING -> uiState.tabStats.paying.amountSum
                    BudgetListFilter.SETTLED -> uiState.tabStats.settled.amountSum
                },
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (LedgerContentGate.showEmptyCopy(uiState.contentReady, uiState.groups.isEmpty())) {
                    Text(
                        text = "暂无支付项",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    uiState.groups.forEach { group ->
                        StageGroupSection(
                            group = group,
                            layout = uiState.layout,
                            onToggle = { viewModel.toggleStage(group.stage) },
                            onOpenItem = onOpenItem,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }
}

@Composable
private fun ListControlsRow(
    groupBy: PaymentListGroupBy,
    layout: PaymentListLayout,
    onGroupBySelect: (PaymentListGroupBy) -> Unit,
    onLayoutSelect: (PaymentListLayout) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedSegmentedRow(
            modifier = Modifier.weight(1.5f),
            segments = listOf(
                "阶段" to (groupBy == PaymentListGroupBy.STAGE),
                "分类" to (groupBy == PaymentListGroupBy.CATEGORY),
                "空间" to (groupBy == PaymentListGroupBy.SPACE),
            ),
            onSelect = { index ->
                onGroupBySelect(
                    when (index) {
                        0 -> PaymentListGroupBy.STAGE
                        1 -> PaymentListGroupBy.CATEGORY
                        else -> PaymentListGroupBy.SPACE
                    },
                )
            },
            borderColor = MaterialTheme.colorScheme.primary,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedContentColor = MaterialTheme.colorScheme.onPrimary,
            unselectedContentColor = MaterialTheme.colorScheme.primary,
        )
        OutlinedSegmentedRow(
            modifier = Modifier.weight(1f),
            segments = listOf(
                "二级" to (layout == PaymentListLayout.NESTED),
                "单级" to (layout == PaymentListLayout.FLAT),
            ),
            onSelect = { index ->
                onLayoutSelect(
                    when (index) {
                        0 -> PaymentListLayout.NESTED
                        else -> PaymentListLayout.FLAT
                    },
                )
            },
            borderColor = MaterialTheme.colorScheme.outlineVariant,
            selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            selectedContentColor = MaterialTheme.colorScheme.onSurface,
            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Outlined segmented control: a single pill-shaped border split into segments by thin
 * dividers, with the selected segment filled. Visually distinct from FilterChip capsules
 * (which render as separate standalone chips with gaps between them).
 */
@Composable
private fun OutlinedSegmentedRow(
    segments: List<Pair<String, Boolean>>,
    onSelect: (Int) -> Unit,
    borderColor: Color,
    selectedContainerColor: Color,
    selectedContentColor: Color,
    unselectedContentColor: Color,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            segments.forEachIndexed { index, (text, selected) ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = { onSelect(index) })
                        .background(if (selected) selectedContainerColor else Color.Transparent)
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) selectedContentColor else unselectedContentColor,
                    )
                }
                if (index != segments.lastIndex) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(16.dp)
                            .align(Alignment.CenterVertically)
                            .background(borderColor.copy(alpha = 0.5f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterRow(
    selected: BudgetListFilter,
    tabStats: FilterTabStats,
    onSelect: (BudgetListFilter) -> Unit,
) {
    val filters = listOf(
        BudgetListFilter.ALL to Pair("全部", tabStats.all),
        BudgetListFilter.TO_BUY to Pair("待购买", tabStats.toBuy),
        BudgetListFilter.PAYING to Pair("付款中", tabStats.paying),
        BudgetListFilter.SETTLED to Pair("已结清", tabStats.settled),
    )
    val selectedIndex = filters.indexOfFirst { (filter, _) -> filter == selected }.coerceAtLeast(0)
    TabRow(selectedTabIndex = selectedIndex) {
        filters.forEach { (filter, labelAndStat) ->
            val (label, stat) = labelAndStat
            Tab(
                selected = selected == filter,
                onClick = { onSelect(filter) },
            ) {
                Text(
                    text = "$label (${stat.count})",
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun FilterTotalAmountBar(amountSum: Long) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "合计",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatYuan(amountSum),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun StageGroupSection(
    group: BudgetListStageGroup,
    layout: PaymentListLayout,
    onToggle: () -> Unit,
    onOpenItem: (String) -> Unit,
) {
    val isNested = layout == PaymentListLayout.NESTED
    val expanded = isNested && group.expanded
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(StageExpandAnimMs),
        label = "stageChevron",
    )
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isNested) Modifier.clickable(onClick = onToggle) else Modifier)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (isNested) {
                            Text(
                                text = "▶",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.rotate(chevronRotation),
                            )
                        }
                        if (group.icon?.isPresent == true) {
                            TaxonomyIconView(
                                icon = group.icon,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Text(
                            text = group.stage,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(
                        text = "${group.items.size}项",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "实际支付 ${formatYuan(group.paidSum)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "预算 ${formatYuan(group.budgetSum)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "预计要支付 ${formatYuan(group.projectedSum)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "已支付 ${group.paidItemCount}项 · ${formatYuan(group.paidSum)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "待支付 ${group.pendingItemCount}项 · ${formatYuan(group.pendingAmountSum)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatStageOverspendPercent(group.overspendPercent, group.overspend),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = when {
                        group.overspendPercent != null && group.overspendPercent > 0 ->
                            progressPercentColor(100 + group.overspendPercent, group.health)
                        else ->
                            overspendHintColor(group.overspend, group.health)
                    },
                )
            }
            if (isNested) {
                AnimatedVisibility(
                    visible = expanded,
                    enter = fadeIn(animationSpec = tween(StageFadeInMs)) +
                        expandVertically(animationSpec = tween(StageExpandAnimMs)),
                    exit = fadeOut(animationSpec = tween(StageFadeOutMs)) +
                        shrinkVertically(animationSpec = tween(StageExpandAnimMs)),
                ) {
                    GroupItemsList(items = group.items, onOpenItem = onOpenItem)
                }
            } else {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                GroupItemsList(items = group.items, onOpenItem = onOpenItem)
            }
        }
    }
}

@Composable
private fun GroupItemsList(
    items: List<BudgetListItemUi>,
    onOpenItem: (String) -> Unit,
) {
    Column(
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items.forEach { itemUi ->
            key(itemUi.item.id) {
                BudgetItemCard(
                    itemUi = itemUi,
                    onClick = { onOpenItem(itemUi.item.id) },
                )
            }
        }
    }
}

@Composable
private fun BudgetItemCard(
    itemUi: BudgetListItemUi,
    onClick: () -> Unit,
) {
    val item = itemUi.item
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Zone 1: title + status（状态与标题顶对齐；时间在标题下，无边框）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = item.name.ifBlank { "未命名" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (itemUi.showNewBadge) {
                            NewBadge()
                        }
                    }
                    Text(
                        text = item.recordedDate?.takeIf { it.isNotBlank() } ?: "未填日期",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    val taxonomyLine = listOfNotNull(
                        item.category.takeIf { it.isNotBlank() }?.let { "分类 $it" },
                        item.space.takeIf { it.isNotBlank() }?.let { "空间 $it" },
                    ).joinToString(" · ")
                    if (taxonomyLine.isNotBlank()) {
                        Text(
                            text = taxonomyLine,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                StatusChip(status = itemUi.status)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

            // Zone 2: budget / contract
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "预算金额",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val budgetText = formatYuan(item.budgetAmount)
                val amountLine = if (item.contractAmount != null) {
                    "$budgetText → ${formatYuan(item.contractAmount)}"
                } else {
                    budgetText
                }
                Text(
                    text = amountLine,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

            // Zone 3: payment summary
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PaymentAmountTag(
                    label = "已付",
                    amount = itemUi.paidSum,
                    emphasize = itemUi.paidSum > 0,
                )
                PaymentAmountTag(
                    label = "未付",
                    amount = itemUi.unpaidSum,
                    emphasize = itemUi.unpaidSum > 0,
                    warn = itemUi.unpaidSum > 0,
                )
            }
        }
    }
}

@Composable
private fun PaymentAmountTag(
    label: String,
    amount: Long,
    emphasize: Boolean,
    warn: Boolean = false,
) {
    val color = when {
        warn -> Color(0xFFE65100)
        emphasize -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = formatYuan(amount),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasize) FontWeight.SemiBold else FontWeight.Normal,
            color = color,
        )
    }
}

@Composable
private fun NewBadge() {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = "新增",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun StatusChip(status: ItemStatus) {
    val (text, container, content) = when (status) {
        ItemStatus.TO_BUY -> Triple(
            "待购买",
            Color(0xFFFFF3E0),
            Color(0xFFE65100),
        )
        ItemStatus.PAYING -> Triple(
            "付款中",
            Color(0xFFE3F2FD),
            Color(0xFF1565C0),
        )
        ItemStatus.SETTLED -> Triple(
            "已结清",
            Color(0xFFE8F5E9),
            Color(0xFF2E7D32),
        )
    }
    Surface(
        color = container,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, content.copy(alpha = 0.35f)),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = content,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}
