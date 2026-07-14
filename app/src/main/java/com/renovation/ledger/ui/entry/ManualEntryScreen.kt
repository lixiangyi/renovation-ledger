package com.renovation.ledger.ui.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import com.renovation.ledger.ui.common.ClearableOutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.renovation.ledger.domain.model.BudgetItem
import com.renovation.ledger.domain.model.PaymentStatus
import com.renovation.ledger.domain.model.PaymentType
import com.renovation.ledger.domain.model.label
import com.renovation.ledger.ui.common.ClearableOutlinedTextField
import com.renovation.ledger.ui.common.TaxonomyDropdownField
import com.renovation.ledger.ui.detail.fenToYuanString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualEntryScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: ManualEntryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
            windowInsets = ZeroTopAppBarWindowInsets,
                title = { Text(screenTitle(uiState.mode)) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("←")
                    }
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
            when (uiState.mode) {
                ManualEntryMode.CHOOSE -> ChooseModeSection(
                    onNewItem = { viewModel.setMode(ManualEntryMode.NEW_ITEM) },
                    onAddPayment = { viewModel.setMode(ManualEntryMode.ADD_PAYMENT) },
                )
                ManualEntryMode.NEW_ITEM -> NewItemForm(
                    stages = uiState.stages,
                    categories = uiState.categories,
                    spaces = uiState.spaces,
                    onSave = { name, stage, category, space, budget ->
                        viewModel.createItem(name, stage, category, space, budget, onSuccess = onSaved)
                    },
                )
                ManualEntryMode.ADD_PAYMENT -> AddPaymentForm(
                    items = uiState.allItems,
                    preselectedItemId = uiState.targetItemId,
                    onItemSelected = viewModel::setTargetItemId,
                    onSave = { itemId, type, amount, status, note ->
                        viewModel.addPayment(itemId, type, amount, status, note, onSuccess = onSaved)
                    },
                )
                ManualEntryMode.EDIT_ITEM -> {
                    val editItem = uiState.allItems.find { it.id == uiState.editItemId }
                    if (editItem != null) {
                        EditItemForm(
                            item = editItem,
                            stages = uiState.stages,
                            categories = uiState.categories,
                            spaces = uiState.spaces,
                            onSave = { name, stage, category, space, budget ->
                                viewModel.updateItem(
                                    editItem.id,
                                    name,
                                    stage,
                                    category,
                                    space,
                                    budget,
                                    onSuccess = onSaved,
                                )
                            },
                        )
                    } else {
                        Text(
                            text = "未找到要编辑的预算项",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChooseModeSection(
    onNewItem: () -> Unit,
    onAddPayment: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "选择录入类型",
            style = MaterialTheme.typography.titleMedium,
        )
        Button(
            onClick = onNewItem,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("新增预算项")
        }
        OutlinedButton(
            onClick = onAddPayment,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("为已有项添加付款")
        }
    }
}

@Composable
private fun NewItemForm(
    stages: List<String>,
    categories: List<String>,
    spaces: List<String>,
    onSave: (name: String, stage: String, category: String, space: String, budgetYuan: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var budget by remember { mutableStateOf("") }
    var stage by remember(stages) { mutableStateOf(stages.firstOrNull().orEmpty()) }
    var category by remember(categories) { mutableStateOf(categories.firstOrNull().orEmpty()) }
    var space by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ClearableOutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("名称") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        TaxonomyDropdownField(
            label = "阶段",
            value = stage,
            options = stages,
            onValueChange = { stage = it },
        )
        TaxonomyDropdownField(
            label = "分类",
            value = category,
            options = categories,
            onValueChange = { category = it },
            allowBlank = true,
        )
        TaxonomyDropdownField(
            label = "空间",
            value = space,
            options = spaces,
            onValueChange = { space = it },
            allowBlank = true,
        )
        ClearableOutlinedTextField(
            value = budget,
            onValueChange = { budget = it },
            label = { Text("预算（元）") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Button(
            onClick = { onSave(name, stage, category, space, budget) },
            modifier = Modifier.fillMaxWidth(),
            enabled = name.isNotBlank() && budget.isNotBlank() && stage.isNotBlank(),
        ) {
            Text("保存")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPaymentForm(
    items: List<BudgetItem>,
    preselectedItemId: String,
    onItemSelected: (String) -> Unit,
    onSave: (
        itemId: String,
        type: PaymentType,
        amountYuan: String,
        status: PaymentStatus,
        note: String,
    ) -> Unit,
) {
    var selectedItemId by remember(preselectedItemId, items) {
        mutableStateOf(
            preselectedItemId.ifEmpty { items.firstOrNull()?.id.orEmpty() },
        )
    }
    var itemExpanded by remember { mutableStateOf(false) }
    var type by remember { mutableStateOf(PaymentType.FINAL) }
    var typeExpanded by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(PaymentStatus.PAID) }
    var statusExpanded by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }

    val selectedItem = items.find { it.id == selectedItemId }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (preselectedItemId.isEmpty()) {
            ExposedDropdownMenuBox(
                expanded = itemExpanded,
                onExpandedChange = { itemExpanded = it },
            ) {
                OutlinedTextField(
                    value = selectedItem?.name ?: "选择预算项",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("预算项") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = itemExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                )
                DropdownMenu(
                    expanded = itemExpanded,
                    onDismissRequest = { itemExpanded = false },
                ) {
                    items.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item.name) },
                            onClick = {
                                selectedItemId = item.id
                                onItemSelected(item.id)
                                itemExpanded = false
                            },
                        )
                    }
                }
            }
        } else {
            Text(
                text = "预算项：${selectedItem?.name ?: preselectedItemId}",
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        ExposedDropdownMenuBox(
            expanded = typeExpanded,
            onExpandedChange = { typeExpanded = it },
        ) {
            OutlinedTextField(
                value = paymentTypeLabel(type),
                onValueChange = {},
                readOnly = true,
                label = { Text("付款类型") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
            )
            DropdownMenu(
                expanded = typeExpanded,
                onDismissRequest = { typeExpanded = false },
            ) {
                PaymentType.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(paymentTypeLabel(option)) },
                        onClick = {
                            type = option
                            typeExpanded = false
                        },
                    )
                }
            }
        }

        ClearableOutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("金额（元）") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        ExposedDropdownMenuBox(
            expanded = statusExpanded,
            onExpandedChange = { statusExpanded = it },
        ) {
            OutlinedTextField(
                value = paymentStatusLabel(status),
                onValueChange = {},
                readOnly = true,
                label = { Text("付款状态") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusExpanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
            )
            DropdownMenu(
                expanded = statusExpanded,
                onDismissRequest = { statusExpanded = false },
            ) {
                PaymentStatus.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(paymentStatusLabel(option)) },
                        onClick = {
                            status = option
                            statusExpanded = false
                        },
                    )
                }
            }
        }

        ClearableOutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("备注（可选）") },
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = {
                val itemId = preselectedItemId.ifEmpty { selectedItemId }
                onSave(itemId, type, amount, status, note)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = amount.isNotBlank() &&
                (preselectedItemId.isNotEmpty() || selectedItemId.isNotEmpty()),
        ) {
            Text("保存")
        }
    }
}

@Composable
private fun EditItemForm(
    item: BudgetItem,
    stages: List<String>,
    categories: List<String>,
    spaces: List<String>,
    onSave: (name: String, stage: String, category: String, space: String, budgetYuan: String) -> Unit,
) {
    var name by remember(item) { mutableStateOf(item.name) }
    var budget by remember(item) { mutableStateOf(fenToYuanString(item.budgetAmount)) }
    var stage by remember(item, stages) {
        mutableStateOf(item.stage.ifBlank { stages.firstOrNull().orEmpty() })
    }
    var category by remember(item) { mutableStateOf(item.category) }
    var space by remember(item) { mutableStateOf(item.space) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ClearableOutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("名称") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        TaxonomyDropdownField(
            label = "阶段",
            value = stage,
            options = stages,
            onValueChange = { stage = it },
        )
        TaxonomyDropdownField(
            label = "分类",
            value = category,
            options = categories,
            onValueChange = { category = it },
            allowBlank = true,
        )
        TaxonomyDropdownField(
            label = "空间",
            value = space,
            options = spaces,
            onValueChange = { space = it },
            allowBlank = true,
        )
        ClearableOutlinedTextField(
            value = budget,
            onValueChange = { budget = it },
            label = { Text("预算（元）") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Button(
            onClick = { onSave(name, stage, category, space, budget) },
            modifier = Modifier.fillMaxWidth(),
            enabled = name.isNotBlank() && budget.isNotBlank() && stage.isNotBlank(),
        ) {
            Text("保存")
        }
    }
}

private fun screenTitle(mode: ManualEntryMode): String = when (mode) {
    ManualEntryMode.CHOOSE -> "记一笔"
    ManualEntryMode.NEW_ITEM -> "新增预算项"
    ManualEntryMode.ADD_PAYMENT -> "添加付款"
    ManualEntryMode.EDIT_ITEM -> "编辑预算项"
}

private fun paymentTypeLabel(type: PaymentType): String = type.label()

private fun paymentStatusLabel(status: PaymentStatus): String = when (status) {
    PaymentStatus.PAID -> "已付"
    PaymentStatus.UNPAID -> "未付"
}
