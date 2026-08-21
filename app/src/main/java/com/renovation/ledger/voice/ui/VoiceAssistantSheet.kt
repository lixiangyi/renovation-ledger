package com.renovation.ledger.voice.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceAssistantSheet(
    state: VoiceAssistantUiState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onTranscriptChange: (String) -> Unit,
    onSubmitEditedTranscript: () -> Unit,
    onUseTypedInput: () -> Unit,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
) {
    if (!state.visible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "语音助手",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Icon(
                imageVector = Icons.Outlined.Mic,
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape),
                tint = MaterialTheme.colorScheme.primary,
            )
            when (state.mode) {
                VoiceAssistantMode.HOLD_TO_TALK, VoiceAssistantMode.LISTENING -> {
                    Text("按住说话…")
                    Button(
                        onClick = {},
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        onHoldStart()
                                        tryAwaitRelease()
                                        onHoldEnd()
                                    },
                                )
                            },
                    ) {
                        Text("按住 说话")
                    }
                    TextButton(onClick = onUseTypedInput) { Text("改用文字输入") }
                }
                VoiceAssistantMode.TRANSCRIBING -> Text("正在转写…")
                VoiceAssistantMode.ANALYZING -> Text("正在分析…")
                VoiceAssistantMode.EDIT_TRANSCRIPT -> {
                    Text(state.errorMessage ?: "识别结果请确认")
                    OutlinedTextField(
                        value = state.transcript,
                        onValueChange = onTranscriptChange,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                    Button(
                        onClick = onSubmitEditedTranscript,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("继续分析")
                    }
                }
                VoiceAssistantMode.NEED_CONFIRM, VoiceAssistantMode.EXECUTING -> {
                    Text("等待确认…")
                }
                VoiceAssistantMode.ERROR -> {
                    Text(state.errorMessage ?: "识别失败")
                    TextButton(onClick = onRetry) { Text("重试") }
                    TextButton(onClick = onUseTypedInput) { Text("改用文字输入") }
                }
                VoiceAssistantMode.DONE, VoiceAssistantMode.IDLE -> {
                    Text(state.snackMessage ?: "已完成")
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
