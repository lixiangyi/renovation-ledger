package com.renovation.ledger.ui.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryChooserSheet(
    onDismiss: () -> Unit,
    onManualEntry: () -> Unit,
    onVoiceEntry: () -> Unit,
    onImageEntry: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "记一笔",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            TextButton(
                onClick = {
                    onDismiss()
                    onManualEntry()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("手动录入")
            }
            TextButton(
                onClick = onVoiceEntry,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("语音录入")
            }
            TextButton(
                onClick = {
                    onDismiss()
                    onImageEntry()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("图片识别")
            }
        }
    }
}
