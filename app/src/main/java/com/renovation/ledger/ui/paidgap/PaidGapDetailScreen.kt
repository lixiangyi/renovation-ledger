package com.renovation.ledger.ui.paidgap

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.renovation.ledger.domain.metrics.PaidBudgetGap
import com.renovation.ledger.ui.common.HealthGreen
import com.renovation.ledger.ui.common.HealthRed
import com.renovation.ledger.ui.common.ZeroTopAppBarWindowInsets
import com.renovation.ledger.ui.common.formatYuan

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaidGapDetailScreen(
    initialTab: String = "overspend",
    onBack: () -> Unit,
    onOpenItem: (String) -> Unit,
    viewModel: PaidGapDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember {
        mutableIntStateOf(if (initialTab == "surplus") 1 else 0)
    }

    LaunchedEffect(initialTab) {
        selectedTab = if (initialTab == "surplus") 1 else 0
    }

    val overspendTotal = uiState.overspendRows.sumOf { it.gapAmount }
    val surplusTotal = uiState.surplusRows.sumOf { it.gapAmount }
    val overspendBudget = uiState.overspendRows.sumOf { it.budgetAmount }
    val overspendPaid = uiState.overspendRows.sumOf { it.paidAmount }
    val surplusBudget = uiState.surplusRows.sumOf { it.budgetAmount }
    val surplusPaid = uiState.surplusRows.sumOf { it.paidAmount }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = ZeroTopAppBarWindowInsets,
                title = { Text("已实付明细") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("←") }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("超支项（${uiState.overspendRows.size}）") },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("节余项（${uiState.surplusRows.size}）") },
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (selectedTab == 0) {
                    GapTotalHeroCard(
                        title = "超支合计",
                        amount = overspendTotal,
                        detail = "预算 ${formatYuan(overspendBudget)} · 实付 ${formatYuan(overspendPaid)}",
                        amountColor = HealthRed,
                        containerColor = HealthRed.copy(alpha = 0.14f),
                        borderColor = HealthRed.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    GapList(
                        rows = uiState.overspendRows,
                        emptyHint = "暂无单项超支（已付未超过预算）",
                        gapLabel = "超出",
                        amountColor = HealthRed,
                        onOpenItem = onOpenItem,
                    )
                } else {
                    GapTotalHeroCard(
                        title = "节余合计",
                        amount = surplusTotal,
                        detail = "预算 ${formatYuan(surplusBudget)} · 实付 ${formatYuan(surplusPaid)}",
                        amountColor = HealthGreen,
                        containerColor = HealthGreen.copy(alpha = 0.14f),
                        borderColor = HealthGreen.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    GapList(
                        rows = uiState.surplusRows,
                        emptyHint = "暂无单项节余（已结清且未花满预算）",
                        gapLabel = "节余",
                        amountColor = HealthGreen,
                        onOpenItem = onOpenItem,
                    )
                }
            }
        }
    }
}

@Composable
private fun GapTotalHeroCard(
    title: String,
    amount: Long,
    detail: String,
    amountColor: Color,
    containerColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.5.dp, borderColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = amountColor,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = formatYuan(amount),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = amountColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun GapList(
    rows: List<PaidBudgetGap>,
    emptyHint: String,
    gapLabel: String,
    amountColor: Color,
    onOpenItem: (String) -> Unit,
) {
    if (rows.isEmpty()) {
        Text(
            text = emptyHint,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    rows.forEach { row ->
        GapDetailCard(
            row = row,
            gapLabel = gapLabel,
            amountColor = amountColor,
            onOpenItem = onOpenItem,
        )
    }
}

@Composable
private fun GapDetailCard(
    row: PaidBudgetGap,
    gapLabel: String,
    amountColor: Color,
    onOpenItem: (String) -> Unit,
) {
    val percentText = row.gapPercent?.let { "$it%" } ?: "—"
    Card(
        onClick = { onOpenItem(row.itemId) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = row.itemName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp),
                )
                Text(
                    text = "$gapLabel ${formatYuan(row.gapAmount)} · $percentText",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = amountColor,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "预算 ${formatYuan(row.budgetAmount)}  ·  实付 ${formatYuan(row.paidAmount)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
