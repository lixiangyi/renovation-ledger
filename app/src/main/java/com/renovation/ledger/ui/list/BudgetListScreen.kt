package com.renovation.ledger.ui.list

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import com.renovation.ledger.ui.common.ZeroTopAppBarWindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.renovation.ledger.domain.model.ItemStatus
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
            TopAppBar(
            windowInsets = ZeroTopAppBarWindowInsets,
                title = { Text("预算清单") },
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
                onSelect = viewModel::setFilter,
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (uiState.groups.isEmpty()) {
                    Text(
                        text = "暂无预算项",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    uiState.groups.forEach { group ->
                        StageGroupSection(
                            group = group,
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
private fun FilterRow(
    selected: BudgetListFilter,
    onSelect: (BudgetListFilter) -> Unit,
) {
    val filters = listOf(
        BudgetListFilter.ALL to "全部",
        BudgetListFilter.TO_BUY to "待购买",
        BudgetListFilter.PAYING to "付款中",
        BudgetListFilter.SETTLED to "已结清",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        filters.forEach { (filter, label) ->
            val isSelected = selected == filter
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(filter) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    selectedLabelColor = MaterialTheme.colorScheme.primary,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                    selectedBorderColor = MaterialTheme.colorScheme.primary,
                    borderWidth = 1.dp,
                    selectedBorderWidth = 1.5.dp,
                ),
            )
        }
    }
}

@Composable
private fun StageGroupSection(
    group: BudgetListStageGroup,
    onToggle: () -> Unit,
    onOpenItem: (String) -> Unit,
) {
    val expanded = group.expanded
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
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
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
                        Text(
                            text = "▶",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.rotate(chevronRotation),
                        )
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
                    text = "预算 ${formatYuan(group.budgetSum)} · 实际 ${formatYuan(group.actualSum)}",
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
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(animationSpec = tween(StageFadeInMs)) +
                    expandVertically(animationSpec = tween(StageExpandAnimMs)),
                exit = fadeOut(animationSpec = tween(StageFadeOutMs)) +
                    shrinkVertically(animationSpec = tween(StageExpandAnimMs)),
            ) {
                Column(
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    group.items.forEach { itemUi ->
                        key(itemUi.item.id) {
                            BudgetItemCard(
                                itemUi = itemUi,
                                onClick = { onOpenItem(itemUi.item.id) },
                            )
                        }
                    }
                }
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
                        if (item.isNewAddition) {
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
