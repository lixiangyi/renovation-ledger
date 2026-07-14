package com.renovation.ledger.ui.paidgap

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import com.renovation.ledger.ui.common.ZeroTopAppBarWindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.renovation.ledger.domain.metrics.PaidBudgetGap
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
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (selectedTab == 0) {
                    GapList(
                        rows = uiState.overspendRows,
                        emptyHint = "暂无单项超支（已付未超过预算）",
                        gapLabel = "超出",
                        showTotal = true,
                        onOpenItem = onOpenItem,
                    )
                } else {
                    GapList(
                        rows = uiState.surplusRows,
                        emptyHint = "暂无单项节余（已结清且未花满预算）",
                        gapLabel = "节余",
                        showTotal = false,
                        onOpenItem = onOpenItem,
                    )
                }
            }
        }
    }
}

@Composable
private fun GapList(
    rows: List<PaidBudgetGap>,
    emptyHint: String,
    gapLabel: String,
    showTotal: Boolean,
    onOpenItem: (String) -> Unit,
) {
    if (rows.isEmpty()) {
        Text(
            text = emptyHint,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    if (showTotal) {
        OverspendTotalCard(rows = rows)
    }
    rows.forEach { row ->
        GapDetailCard(
            row = row,
            gapLabel = gapLabel,
            onOpenItem = onOpenItem,
        )
    }
}

@Composable
private fun OverspendTotalCard(rows: List<PaidBudgetGap>) {
    val gapTotal = rows.sumOf { it.gapAmount }
    val budgetTotal = rows.sumOf { it.budgetAmount }
    val paidTotal = rows.sumOf { it.paidAmount }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = "超支合计",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatYuan(gapTotal),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "预算合计 ${formatYuan(budgetTotal)}  ·  实付合计 ${formatYuan(paidTotal)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
private fun GapDetailCard(
    row: PaidBudgetGap,
    gapLabel: String,
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
                    color = MaterialTheme.colorScheme.primary,
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
