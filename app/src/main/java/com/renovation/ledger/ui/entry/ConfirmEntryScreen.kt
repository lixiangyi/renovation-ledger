package com.renovation.ledger.ui.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.renovation.ledger.domain.model.BudgetItem
import com.renovation.ledger.domain.model.PaymentStatus
import com.renovation.ledger.domain.model.PaymentType
import com.renovation.ledger.domain.model.label
import com.renovation.ledger.domain.taxonomy.TaxonomyCatalog
import com.renovation.ledger.ui.common.ClearableOutlinedTextField
import com.renovation.ledger.ui.common.TaxonomyDropdownField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmEntryScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: ConfirmEntryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val draft = uiState.draft

    Scaffold(
        topBar = {
            TopAppBar(
            windowInsets = ZeroTopAppBarWindowInsets,
                title = { Text(confirmTitle(uiState.source)) },
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
            if (draft.showRecognitionBanner) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Text(
                        text = "识别结果请确认后保存",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            ActionTypeSelector(
                selected = draft.actionType,
                onSelected = { type ->
                    viewModel.updateDraft { it.copy(actionType = type) }
                },
            )

            when (draft.actionType) {
                ConfirmActionType.NEW_ITEM -> NewItemFields(
                    draft = draft,
                    catalog = uiState.catalog,
                    onDraftChange = viewModel::updateDraft,
                )
                ConfirmActionType.ADD_PAYMENT -> AddPaymentFields(
                    draft = draft,
                    items = uiState.allItems,
                    onDraftChange = viewModel::updateDraft,
                )
            }

            Button(
                onClick = { viewModel.save(onSuccess = onSaved) },
                modifier = Modifier.fillMaxWidth(),
                enabled = draft.amountYuan.isNotBlank() ||
                    (draft.actionType == ConfirmActionType.NEW_ITEM && draft.budgetYuan.isNotBlank()),
            ) {
                Text("保存")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionTypeSelector(
    selected: ConfirmActionType,
    onSelected: (ConfirmActionType) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = when (selected) {
                ConfirmActionType.NEW_ITEM -> "新增预算项"
                ConfirmActionType.ADD_PAYMENT -> "为已有项添加付款"
            },
            onValueChange = {},
            readOnly = true,
            label = { Text("操作类型") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            ConfirmActionType.entries.forEach { type ->
                DropdownMenuItem(
                    text = {
                        Text(
                            when (type) {
                                ConfirmActionType.NEW_ITEM -> "新增预算项"
                                ConfirmActionType.ADD_PAYMENT -> "为已有项添加付款"
                            },
                        )
                    },
                    onClick = {
                        onSelected(type)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun NewItemFields(
    draft: ConfirmEntryDraft,
    catalog: TaxonomyCatalog,
    onDraftChange: ((ConfirmEntryDraft) -> ConfirmEntryDraft) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ClearableOutlinedTextField(
            value = draft.itemName,
            onValueChange = { value -> onDraftChange { it.copy(itemName = value) } },
            label = { Text("项名称") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        TaxonomyDropdownField(
            label = "阶段",
            value = draft.stage,
            options = catalog.stages,
            onValueChange = { value -> onDraftChange { it.copy(stage = value) } },
        )
        TaxonomyDropdownField(
            label = "分类",
            value = draft.category,
            options = catalog.categories,
            onValueChange = { value -> onDraftChange { it.copy(category = value) } },
            allowBlank = true,
        )
        TaxonomyDropdownField(
            label = "空间",
            value = draft.space,
            options = catalog.spaces,
            onValueChange = { value -> onDraftChange { it.copy(space = value) } },
            allowBlank = true,
        )
        ClearableOutlinedTextField(
            value = draft.budgetYuan,
            onValueChange = { value -> onDraftChange { it.copy(budgetYuan = value) } },
            label = { Text("预算（元）") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        PaymentFields(draft = draft, onDraftChange = onDraftChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPaymentFields(
    draft: ConfirmEntryDraft,
    items: List<BudgetItem>,
    onDraftChange: ((ConfirmEntryDraft) -> ConfirmEntryDraft) -> Unit,
) {
    var itemExpanded by remember { mutableStateOf(false) }
    val selectedItem = items.find { it.id == draft.selectedItemId }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (items.isNotEmpty()) {
            ExposedDropdownMenuBox(
                expanded = itemExpanded,
                onExpandedChange = { itemExpanded = it },
            ) {
                OutlinedTextField(
                    value = selectedItem?.name ?: draft.itemName.ifBlank { "选择预算项" },
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
                                onDraftChange {
                                    it.copy(
                                        selectedItemId = item.id,
                                        itemName = item.name,
                                        budgetYuan = (item.budgetAmount / 100).toString(),
                                    )
                                }
                                itemExpanded = false
                            },
                        )
                    }
                }
            }
        } else {
            ClearableOutlinedTextField(
                value = draft.itemName,
                onValueChange = { value -> onDraftChange { it.copy(itemName = value) } },
                label = { Text("项名称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        ClearableOutlinedTextField(
            value = draft.budgetYuan,
            onValueChange = { value -> onDraftChange { it.copy(budgetYuan = value) } },
            label = { Text("预算（元，参考）") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        PaymentFields(draft = draft, onDraftChange = onDraftChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentFields(
    draft: ConfirmEntryDraft,
    onDraftChange: ((ConfirmEntryDraft) -> ConfirmEntryDraft) -> Unit,
) {
    var typeExpanded by remember { mutableStateOf(false) }
    var statusExpanded by remember { mutableStateOf(false) }

    ClearableOutlinedTextField(
        value = draft.amountYuan,
        onValueChange = { value -> onDraftChange { it.copy(amountYuan = value) } },
        label = { Text("付款金额（元）") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    ExposedDropdownMenuBox(
        expanded = typeExpanded,
        onExpandedChange = { typeExpanded = it },
    ) {
        OutlinedTextField(
            value = confirmPaymentTypeLabel(draft.paymentType),
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
            PaymentType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(confirmPaymentTypeLabel(type)) },
                    onClick = {
                        onDraftChange { it.copy(paymentType = type) }
                        typeExpanded = false
                    },
                )
            }
        }
    }
    ExposedDropdownMenuBox(
        expanded = statusExpanded,
        onExpandedChange = { statusExpanded = it },
    ) {
        OutlinedTextField(
            value = confirmPaymentStatusLabel(draft.paymentStatus),
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
            PaymentStatus.entries.forEach { status ->
                DropdownMenuItem(
                    text = { Text(confirmPaymentStatusLabel(status)) },
                    onClick = {
                        onDraftChange { it.copy(paymentStatus = status) }
                        statusExpanded = false
                    },
                )
            }
        }
    }
}

private fun confirmTitle(source: EntrySource): String = when (source) {
    EntrySource.VOICE -> "语音录入确认"
    EntrySource.IMAGE -> "图片识别确认"
    EntrySource.MANUAL -> "录入确认"
}

private fun confirmPaymentTypeLabel(type: PaymentType): String = type.label()

private fun confirmPaymentStatusLabel(status: PaymentStatus): String = when (status) {
    PaymentStatus.PAID -> "已付"
    PaymentStatus.UNPAID -> "未付"
}
