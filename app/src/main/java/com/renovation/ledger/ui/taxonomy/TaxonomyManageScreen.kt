package com.renovation.ledger.ui.taxonomy

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.renovation.ledger.domain.taxonomy.TaxonomyKind
import com.renovation.ledger.domain.taxonomy.label
import com.renovation.ledger.ui.common.ClearableOutlinedTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaxonomyManageScreen(
    onBack: () -> Unit,
    viewModel: TaxonomyManageViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var editingValue by remember { mutableStateOf<String?>(null) }
    var deletingValue by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
            windowInsets = ZeroTopAppBarWindowInsets,
                title = { Text("标签管理") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("←")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            draft = ""
                            showAdd = true
                        },
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = "新增标签")
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
            TabRow(selectedTabIndex = uiState.selectedKind.ordinal) {
                TaxonomyKind.entries.forEach { kind ->
                    Tab(
                        selected = uiState.selectedKind == kind,
                        onClick = { viewModel.selectKind(kind) },
                        text = { Text(kind.label()) },
                    )
                }
            }
            Text(
                text = "维护「${uiState.selectedKind.label()}」选项，录入与编辑下拉会同步使用。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (uiState.options.isEmpty()) {
                    item {
                        Text(
                            text = "暂无选项，点右上角 + 添加",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                }
                items(uiState.options, key = { it }) { value ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                draft = value
                                editingValue = value
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = {
                                draft = value
                                editingValue = value
                            },
                        ) {
                            Icon(Icons.Outlined.Edit, contentDescription = "编辑")
                        }
                        IconButton(onClick = { deletingValue = value }) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
            OutlinedButton(
                onClick = viewModel::resetCurrent,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text("恢复「${uiState.selectedKind.label()}」默认列表")
            }
        }
    }

    if (showAdd) {
        TaxonomyNameDialog(
            title = "新增${uiState.selectedKind.label()}",
            value = draft,
            onValueChange = { draft = it },
            onDismiss = { showAdd = false },
            onConfirm = {
                viewModel.add(draft)
                showAdd = false
            },
        )
    }

    editingValue?.let { old ->
        TaxonomyNameDialog(
            title = "修改${uiState.selectedKind.label()}",
            value = draft,
            onValueChange = { draft = it },
            onDismiss = { editingValue = null },
            onConfirm = {
                viewModel.rename(old, draft)
                editingValue = null
            },
        )
    }

    deletingValue?.let { value ->
        AlertDialog(
            onDismissRequest = { deletingValue = null },
            title = { Text("删除标签") },
            text = { Text("确定删除「$value」？已有预算项上的该标签不会自动改名。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.remove(value)
                        deletingValue = null
                    },
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingValue = null }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun TaxonomyNameDialog(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            ClearableOutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text("名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = value.isNotBlank(),
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}
