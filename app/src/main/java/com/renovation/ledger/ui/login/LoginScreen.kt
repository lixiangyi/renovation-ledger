package com.renovation.ledger.ui.login

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.renovation.ledger.data.auth.WeChatAppAuth
import com.renovation.ledger.ui.common.BackNavigationButton
import com.renovation.ledger.ui.common.ClearableOutlinedTextField
import com.renovation.ledger.ui.common.CompactTopAppBar

private object PhoneVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): androidx.compose.ui.text.input.TransformedText {
        val digits = digitsOnlyPhone(text.text)
        val display = formatPhoneDisplay(digits)
        val transformed = AnnotatedString(display)
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val o = offset.coerceIn(0, digits.length)
                return when {
                    o <= 3 -> o
                    o <= 7 -> o + 1
                    else -> o + 2
                }
            }

            override fun transformedToOriginal(offset: Int): Int {
                val t = display
                val o = offset.coerceIn(0, t.length)
                var digitsCount = 0
                for (i in 0 until o) {
                    if (t[i].isDigit()) digitsCount++
                }
                return digitsCount
            }
        }
        return androidx.compose.ui.text.input.TransformedText(transformed, offsetMapping)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onBack: () -> Unit,
    onLoggedIn: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primary,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        containerColor = MaterialTheme.colorScheme.surface,
        labelColor = MaterialTheme.colorScheme.onSurface,
    )

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.clearMessage()
    }

    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = { Text("登录") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "账号登录",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = uiState.tab == LoginTab.PHONE,
                    onClick = { viewModel.selectTab(LoginTab.PHONE) },
                    label = { Text("手机号") },
                    colors = chipColors,
                )
                FilterChip(
                    selected = uiState.tab == LoginTab.WECHAT,
                    onClick = { viewModel.selectTab(LoginTab.WECHAT) },
                    label = { Text("微信") },
                    colors = chipColors,
                )
            }
            when (uiState.tab) {
                LoginTab.PHONE -> {
                    ClearableOutlinedTextField(
                        value = uiState.phone,
                        onValueChange = viewModel::setPhone,
                        label = { Text("手机号") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.busy,
                        visualTransformation = PhoneVisualTransformation,
                    )
                    ClearableOutlinedTextField(
                        value = uiState.code,
                        onValueChange = viewModel::setCode,
                        label = { Text("验证码") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.busy,
                    )
                    OutlinedButton(
                        onClick = viewModel::sendCode,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.busy,
                    ) {
                        Text("获取验证码")
                    }
                    Button(
                        onClick = { viewModel.loginPhone(onLoggedIn) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.busy,
                    ) {
                        Text("登录")
                    }
                }
                LoginTab.WECHAT -> {
                    Button(
                        onClick = {
                            viewModel.wechatLogin(WeChatAppAuth.findActivity(context))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.busy,
                    ) {
                        Text("微信登录")
                    }
                    Text(
                        text = "若微信暂不可用，请改用手机号登录。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
