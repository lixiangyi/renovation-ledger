package com.renovation.ledger.ui.importbatch

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import com.renovation.ledger.ui.common.ZeroTopAppBarWindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.renovation.ledger.domain.importing.ImportedLineDraft
import com.renovation.ledger.ui.common.formatYuan

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchImportConfirmScreen(
    onBack: () -> Unit,
    onImported: () -> Unit,
    viewModel: BatchImportConfirmViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var expandedStages by remember { mutableStateOf(setOf<String>()) }

    val grouped = uiState.lines
        .mapIndexed { index, line -> index to line }
        .groupBy { it.second.stage }

    Scaffold(
        topBar = {
            TopAppBar(
            windowInsets = ZeroTopAppBarWindowInsets,
                title = {
                    Text("确认导入 ${uiState.selectedCount}/${uiState.totalCount}")
                },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("←") }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "来源：${uiState.sourceLabel.ifBlank { "文件" }} · 去重 ${uiState.duplicateCount} 条 · 已选合计 ${formatYuan(uiState.selectedSumCents)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = uiState.hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            uiState.error?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = viewModel::selectAll) { Text("全选") }
                OutlinedButton(onClick = viewModel::selectNonDuplicates) { Text("仅选非重复") }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (uiState.lines.isEmpty()) {
                    Text(
                        text = "没有可导入的数据，请返回重新选择 CSV",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                grouped.forEach { (stage, indexedLines) ->
                    val open = stage in expandedStages || expandedStages.isEmpty()
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        expandedStages = if (stage in expandedStages) {
                                            expandedStages - stage
                                        } else {
                                            expandedStages + stage
                                        }
                                    }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "${if (open) "▼" else "▶"} $stage",
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "${indexedLines.size} 项",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (open) {
                                indexedLines.forEach { (index, line) ->
                                    ImportLineRow(
                                        line = line,
                                        onToggle = { viewModel.toggleSelected(index) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    viewModel.confirmImport {
                        Toast.makeText(
                            context,
                            "已导入 ${uiState.selectedCount} 项",
                            Toast.LENGTH_SHORT,
                        ).show()
                        onImported()
                    }
                },
                enabled = uiState.selectedCount > 0 && !uiState.isImporting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (uiState.isImporting) "导入中…" else "确认导入")
            }
        }
    }
}

@Composable
private fun ImportLineRow(
    line: ImportedLineDraft,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = line.selected, onCheckedChange = { onToggle() })
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = buildString {
                    if (!line.recordedDate.isNullOrBlank()) append("${line.recordedDate}  ")
                    append(line.name)
                    if (line.isDuplicate) append(" · 重复")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (line.isDuplicate) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            if (line.remark.isNotBlank()) {
                Text(
                    text = line.remark,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(text = formatYuan(line.amountCents))
    }
}
