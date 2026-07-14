package com.renovation.ledger.ui.detail

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.renovation.ledger.domain.model.BudgetItem
import com.renovation.ledger.domain.model.ItemStatus
import com.renovation.ledger.domain.model.Payment
import com.renovation.ledger.domain.model.PaymentStatus
import com.renovation.ledger.domain.model.PaymentType
import com.renovation.ledger.domain.model.label
import com.renovation.ledger.domain.taxonomy.TaxonomyCatalog
import com.renovation.ledger.ui.common.ClearableOutlinedTextField
import com.renovation.ledger.ui.common.DatePickerField
import com.renovation.ledger.ui.common.TaxonomyDropdownField
import com.renovation.ledger.ui.common.formatYuan

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(
    onBack: () -> Unit,
    onAddPayment: (String) -> Unit,
    onDeleted: () -> Unit,
    viewModel: ItemDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var editingPayment by remember { mutableStateOf<Payment?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
            windowInsets = ZeroTopAppBarWindowInsets,
                title = { Text(uiState.item?.name ?: "预算项详情") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("←")
                    }
                },
            )
        },
    ) { innerPadding ->
        val item = uiState.item
        if (item == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "未找到该预算项",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                InfoCard(
                    item = item,
                    status = uiState.status,
                    paidSum = uiState.paidSum,
                    unpaidSum = uiState.unpaidSum,
                    isOverBudget = uiState.isOverBudget,
                )
                PaymentListSection(
                    payments = item.payments,
                    onEditPayment = { editingPayment = it },
                )
                ActionButtons(
                    status = uiState.status,
                    onAddPayment = { onAddPayment(item.id) },
                    onSettle = viewModel::settleItem,
                    onEdit = { showEditDialog = true },
                    onDelete = { showDeleteDialog = true },
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    val item = uiState.item
    if (showEditDialog && item != null) {
        EditItemDialog(
            item = item,
            catalog = uiState.catalog,
            onDismiss = { showEditDialog = false },
            onConfirm = { name, budget, contract, recordedDate, remark, stage, category, space ->
                viewModel.updateItem(
                    name,
                    budget,
                    contract,
                    recordedDate,
                    remark,
                    stage,
                    category,
                    space,
                )
                showEditDialog = false
            },
        )
    }

    editingPayment?.let { payment ->
        EditPaymentDialog(
            payment = payment,
            onDismiss = { editingPayment = null },
            onConfirm = { type, amountYuan, status, note ->
                viewModel.updatePayment(payment.id, type, amountYuan, status, note)
                editingPayment = null
            },
            onDelete = {
                viewModel.deletePayment(payment.id)
                editingPayment = null
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除预算项") },
            text = { Text("确定删除「${item?.name ?: ""}」？相关付款记录将一并删除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteItem(onDeleted)
                    },
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun InfoCard(
    item: BudgetItem,
    status: ItemStatus,
    paidSum: Long,
    unpaidSum: Long,
    isOverBudget: Boolean,
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
            InfoRow("状态", statusLabel(status))
            InfoRow("阶段", item.stage.ifBlank { "—" })
            InfoRow("分类", item.category.ifBlank { "—" })
            InfoRow("空间", item.space.ifBlank { "—" })
            if (item.merchant.isNotBlank()) {
                InfoRow("商家", item.merchant)
            }
            val budgetText = formatYuan(item.budgetAmount)
            val costText = if (item.contractAmount != null) {
                val contractText = formatYuan(item.contractAmount)
                if (isOverBudget) {
                    "$budgetText → $contractText ↑"
                } else {
                    "$budgetText → $contractText"
                }
            } else {
                budgetText
            }
            InfoRow("预算", costText)
            InfoRow("已付合计", formatYuan(paidSum))
            InfoRow("未付合计", formatYuan(unpaidSum))
            if (!item.recordedDate.isNullOrBlank()) {
                InfoRow("记账日期", item.recordedDate)
            }
            if (item.remark.isNotBlank()) {
                InfoRow("备注", item.remark)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun PaymentListSection(
    payments: List<Payment>,
    onEditPayment: (Payment) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "付款记录",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        if (payments.isEmpty()) {
            Text(
                text = "暂无付款记录",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            payments.forEach { payment ->
                PaymentRow(
                    payment = payment,
                    onClick = { onEditPayment(payment) },
                )
            }
        }
    }
}

@Composable
private fun PaymentRow(
    payment: Payment,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${paymentTypeLabel(payment.type)} · ${paymentStatusLabel(payment.status)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                if (payment.note.isNotBlank()) {
                    Text(
                        text = payment.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (payment.createdBy.isNotBlank()) {
                    Text(
                        text = payment.createdBy,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "点击修改",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = formatYuan(payment.amount),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ActionButtons(
    status: ItemStatus,
    onAddPayment: () -> Unit,
    onSettle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onAddPayment,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("添加付款")
        }
        if (status != ItemStatus.SETTLED) {
            OutlinedButton(
                onClick = onSettle,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("标记为已结清")
            }
        }
        OutlinedButton(
            onClick = onEdit,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("编辑")
        }
        OutlinedButton(
            onClick = onDelete,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("删除")
        }
    }
}

@Composable
private fun EditItemDialog(
    item: BudgetItem,
    catalog: TaxonomyCatalog,
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        budgetYuan: String,
        contractYuan: String,
        recordedDate: String,
        remark: String,
        stage: String,
        category: String,
        space: String,
    ) -> Unit,
) {
    var name by remember(item) { mutableStateOf(item.name) }
    var budget by remember(item) { mutableStateOf(fenToYuanString(item.budgetAmount)) }
    var contract by remember(item) { mutableStateOf(fenToYuanStringOrEmpty(item.contractAmount)) }
    var recordedDate by remember(item) { mutableStateOf(item.recordedDate.orEmpty()) }
    var remark by remember(item) { mutableStateOf(item.remark) }
    var stage by remember(item, catalog.stages) {
        mutableStateOf(item.stage.ifBlank { catalog.stages.firstOrNull().orEmpty() })
    }
    var category by remember(item) { mutableStateOf(item.category) }
    var space by remember(item) { mutableStateOf(item.space) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑预算项") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    options = catalog.stages,
                    onValueChange = { stage = it },
                )
                TaxonomyDropdownField(
                    label = "分类",
                    value = category,
                    options = catalog.categories,
                    onValueChange = { category = it },
                    allowBlank = true,
                )
                TaxonomyDropdownField(
                    label = "空间",
                    value = space,
                    options = catalog.spaces,
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
                ClearableOutlinedTextField(
                    value = contract,
                    onValueChange = { contract = it },
                    label = { Text("合同价（元，可选）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                DatePickerField(
                    label = "记账日期",
                    value = recordedDate.ifBlank { null },
                    onDateSelected = { recordedDate = it.orEmpty() },
                )
                ClearableOutlinedTextField(
                    value = remark,
                    onValueChange = { remark = it },
                    label = { Text("备注") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(name, budget, contract, recordedDate, remark, stage, category, space)
                },
                enabled = name.isNotBlank() && budget.isNotBlank(),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditPaymentDialog(
    payment: Payment,
    onDismiss: () -> Unit,
    onConfirm: (
        type: PaymentType,
        amountYuan: String,
        status: PaymentStatus,
        note: String,
    ) -> Unit,
    onDelete: () -> Unit,
) {
    var type by remember(payment.id) { mutableStateOf(payment.type) }
    var amount by remember(payment.id) { mutableStateOf(fenToYuanString(payment.amount)) }
    var status by remember(payment.id) { mutableStateOf(payment.status) }
    var note by remember(payment.id) { mutableStateOf(payment.note) }
    var typeExpanded by remember { mutableStateOf(false) }
    var statusExpanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除付款") },
            text = { Text("确定删除这条付款记录？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("取消")
                }
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑付款") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = it },
                ) {
                    OutlinedTextField(
                        value = paymentTypeLabel(type),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("付款类型") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded)
                        },
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
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusExpanded)
                        },
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
                    label = { Text("备注") },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "删除此付款",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(type, amount, status, note) },
                enabled = amount.isNotBlank(),
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

private fun statusLabel(status: ItemStatus): String = when (status) {
    ItemStatus.TO_BUY -> "待购买"
    ItemStatus.PAYING -> "付款中"
    ItemStatus.SETTLED -> "已结清"
}

private fun paymentTypeLabel(type: PaymentType): String = type.label()

private fun paymentStatusLabel(status: PaymentStatus): String = when (status) {
    PaymentStatus.PAID -> "已付"
    PaymentStatus.UNPAID -> "未付"
}
