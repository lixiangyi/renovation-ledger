package com.renovation.ledger.voice.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.renovation.ledger.voice.tool.ToolPreview

@Composable
fun VoiceConfirmDialog(
    preview: ToolPreview,
    onCancel: () -> Unit,
    onConfirm: (Map<String, String>) -> Unit,
) {
    val edits = remember(preview) {
        mutableStateMapOf<String, String>().apply {
            preview.fields.forEach { field ->
                if (field.key.isNotBlank()) {
                    put(field.key, field.value.removePrefix("¥").substringBefore("（").trim())
                }
            }
        }
    }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("语音助手 · ${preview.title}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                preview.fields.forEach { field ->
                    if (field.editable && field.key.isNotBlank()) {
                        OutlinedTextField(
                            value = edits[field.key].orEmpty(),
                            onValueChange = { edits[field.key] = it },
                            label = { Text(field.label) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                    } else {
                        Text("${field.label}  ${field.value}")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(edits.toMap()) }) {
                Text("确认")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("取消")
            }
        },
    )
}
