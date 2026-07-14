package com.renovation.ledger.ui.pending

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.renovation.ledger.domain.model.effectiveCost
import com.renovation.ledger.ui.common.formatYuan

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingSpendScreen(
    initialTab: String = "unpaid",
    onBack: () -> Unit,
    onOpenItem: (String) -> Unit,
    viewModel: PendingSpendViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(if (initialTab == "tobuy") 1 else 0) }

    LaunchedEffect(initialTab) {
        selectedTab = if (initialTab == "tobuy") 1 else 0
    }

    Scaffold(
        topBar = {
            TopAppBar(
            windowInsets = ZeroTopAppBarWindowInsets,
                title = { Text("待花费明细") },
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
                    text = {
                        Text("待付尾款 (${uiState.unpaidFinalRows.size})")
                    },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text("待购买 (${uiState.toBuyItems.size})")
                    },
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
                    if (uiState.unpaidFinalRows.isEmpty()) {
                        Text(
                            text = "暂无待付尾款",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        uiState.unpaidFinalRows.forEach { row ->
                            PendingItemCard(
                                name = row.itemName,
                                amount = row.unpaidAmount,
                                onClick = { onOpenItem(row.itemId) },
                            )
                        }
                    }
                } else {
                    if (uiState.toBuyItems.isEmpty()) {
                        Text(
                            text = "暂无待购买项",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        uiState.toBuyItems.forEach { item ->
                            PendingItemCard(
                                name = item.name,
                                amount = item.effectiveCost(),
                                subtitle = item.recordedDate,
                                onClick = { onOpenItem(item.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingItemCard(
    name: String,
    amount: Long,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = formatYuan(amount),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
