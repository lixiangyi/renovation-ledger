package com.renovation.ledger.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerField(
    label: String,
    value: String?,
    onDateSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
    allowClear: Boolean = true,
) {
    var showPicker by remember { mutableStateOf(false) }
    val hasValue = !value.isNullOrBlank()
    val trailingReserve = when {
        allowClear && hasValue -> 72.dp
        else -> 48.dp
    }

    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text("点击选择日期") },
            modifier = Modifier.fillMaxWidth(),
            interactionSource = remember { MutableInteractionSource() },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (allowClear && hasValue) {
                        TextFieldClearButton(
                            onClick = { onDateSelected(null) },
                            contentDescription = "清除日期",
                        )
                    }
                    IconButton(onClick = { showPicker = true }) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarMonth,
                            contentDescription = "选择日期",
                        )
                    }
                }
            },
            singleLine = true,
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(end = trailingReserve)
                .clickable { showPicker = true },
        )
    }

    if (showPicker) {
        val initialMillis = remember(value) { parseToUtcMillis(value) }
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val millis = state.selectedDateMillis
                        onDateSelected(millis?.let { formatUtcMillis(it) })
                        showPicker = false
                    },
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text("取消")
                }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

internal fun parseToUtcMillis(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    return runCatching {
        LocalDate.parse(value.trim(), dateFormatter)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
    }.getOrNull()
}

internal fun formatUtcMillis(millis: Long): String =
    Instant.ofEpochMilli(millis)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
        .format(dateFormatter)

fun todayIsoDate(zoneId: ZoneId = ZoneId.systemDefault()): String =
    LocalDate.now(zoneId).format(dateFormatter)
