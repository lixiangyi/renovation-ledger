package com.renovation.ledger.ui.profile

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.renovation.ledger.ui.common.BackNavigationButton
import com.renovation.ledger.ui.common.ClearableOutlinedTextField
import com.renovation.ledger.ui.common.CompactTopAppBar
import com.renovation.ledger.ui.common.ProfileAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onOpenLogin: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var nicknameDraft by remember { mutableStateOf(uiState.profile.nickname) }
    var inviteInput by remember { mutableStateOf("") }
    val nicknameDirty = nicknameDraft.trim() != uiState.profile.nickname &&
        nicknameDraft.isNotBlank()

    LaunchedEffect(uiState.profile.nickname) {
        nicknameDraft = uiState.profile.nickname
    }

    LaunchedEffect(uiState.jwt, uiState.cloudUserId) {
        inviteInput = ""
    }

    LaunchedEffect(uiState.actionMessage) {
        val message = uiState.actionMessage ?: return@LaunchedEffect
        val duration = if (message.length > 18) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
        Toast.makeText(context, message, duration).show()
        viewModel.clearActionMessage()
    }

    fun commitNickname() {
        if (!nicknameDirty) return
        viewModel.saveNickname(nicknameDraft)
        keyboardController?.hide()
        focusManager.clearFocus()
    }

    val pickAvatar = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) viewModel.updateAvatar(uri)
    }

    if (uiState.cloudBusy) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
        ) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 28.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CircularProgressIndicator()
                Text(
                    text = uiState.cloudBusyLabel ?: "处理中…",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    uiState.pendingJoin?.let { preview ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelJoinInvite() },
            title = { Text("加入账本") },
            text = {
                Text(
                    "是否加入「${preview.ownerNickname}」的「${preview.ledgerName}」账本？",
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmJoinInvite() }) {
                    Text("确认")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelJoinInvite() }) {
                    Text("取消")
                }
            },
        )
    }

    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = { Text("个人中心") },
                navigationIcon = {
                    BackNavigationButton(onClick = onBack)
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "资料",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .clickable { pickAvatar.launch("image/*") },
                        contentAlignment = Alignment.Center,
                    ) {
                        ProfileAvatar(avatarPath = uiState.profile.avatarPath, size = 88.dp)
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PhotoCamera,
                                contentDescription = "更换头像",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    Text(
                        text = "点击头像更换图片",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (uiState.profile.avatarPath != null) {
                        TextButton(onClick = { viewModel.clearAvatar() }) {
                            Text("清除头像")
                        }
                    }
                    ClearableOutlinedTextField(
                        value = nicknameDraft,
                        onValueChange = { nicknameDraft = it },
                        label = { Text("昵称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { commitNickname() }),
                        trailingIcon = if (nicknameDirty) {
                            {
                                IconButton(onClick = { commitNickname() }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Check,
                                        contentDescription = "保存昵称",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        } else {
                            null
                        },
                        supportingText = if (nicknameDirty) {
                            { Text("修改后点 ✓ 或键盘完成键保存") }
                        } else {
                            null
                        },
                    )
                }
            }

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
                    Text(
                        text = "云同步",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (uiState.jwt != null) "已登录" else "未登录",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (com.renovation.ledger.BuildConfig.ENABLE_DEBUG_PANEL) {
                        Text(
                            text = "摇一摇打开开发面板（可切正式环境）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (uiState.jwt == null) {
                        Button(
                            onClick = onOpenLogin,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("去登录")
                        }
                    } else {
                        val cloudEnabled = !uiState.cloudBusy
                        OutlinedButton(
                            onClick = { viewModel.logout() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = cloudEnabled,
                        ) {
                            Text("退出登录")
                        }
                        if (uiState.phone.isNullOrBlank()) {
                            OutlinedButton(
                                onClick = { viewModel.bindPhone() },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = cloudEnabled,
                            ) {
                                Text("绑定手机号")
                            }
                        }
                        if (uiState.currentUnbound) {
                            OutlinedButton(
                                onClick = { viewModel.uploadCurrentLedger() },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = cloudEnabled,
                            ) {
                                Text("上传当前账本")
                            }
                        }
                        if (uiState.showCreateInvite) {
                            OutlinedButton(
                                onClick = { viewModel.createInvite() },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = cloudEnabled,
                            ) {
                                Text("生成邀请码")
                            }
                            if (!uiState.lastInviteCode.isNullOrBlank()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = cloudEnabled) {
                                            viewModel.copyInviteShare(uiState.lastInviteCode)
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        text = "邀请码 ${uiState.lastInviteCode}",
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        text = "点击复制（含 App 介绍，可发给家人）",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        ClearableOutlinedTextField(
                            value = inviteInput,
                            onValueChange = { inviteInput = it },
                            label = { Text("输入邀请码加入") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = cloudEnabled,
                        )
                        Button(
                            onClick = { viewModel.requestJoinInvite(inviteInput) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = cloudEnabled && inviteInput.isNotBlank(),
                        ) {
                            Text("加入账本")
                        }
                    }
                }
            }
        }
    }
}
