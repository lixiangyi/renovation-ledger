package com.renovation.ledger.wxapi

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.renovation.ledger.data.auth.WeChatAppAuth
import com.renovation.ledger.data.remote.ApiErrorMessages
import com.renovation.ledger.data.sync.LedgerSyncRepository
import com.tencent.mm.opensdk.modelbase.BaseReq
import com.tencent.mm.opensdk.modelbase.BaseResp
import com.tencent.mm.opensdk.modelmsg.SendAuth
import com.tencent.mm.opensdk.openapi.IWXAPIEventHandler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class WXEntryActivity : ComponentActivity(), IWXAPIEventHandler {

    @Inject lateinit var ledgerSync: LedgerSyncRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WeChatAppAuth.api(this).handleIntent(intent, this)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        WeChatAppAuth.api(this).handleIntent(intent, this)
    }

    override fun onReq(req: BaseReq?) {
        finish()
    }

    override fun onResp(resp: BaseResp?) {
        if (resp is SendAuth.Resp && resp.errCode == BaseResp.ErrCode.ERR_OK && !resp.code.isNullOrBlank()) {
            lifecycleScope.launch {
                runCatching { ledgerSync.wechatLogin(resp.code, client = "app") }
                    .onSuccess {
                        Toast.makeText(this@WXEntryActivity, "已登录", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    .onFailure {
                        Toast.makeText(
                            this@WXEntryActivity,
                            ApiErrorMessages.fromThrowable(it),
                            Toast.LENGTH_SHORT,
                        ).show()
                        finish()
                    }
            }
            return
        }
        if (resp != null && resp.errCode != BaseResp.ErrCode.ERR_USER_CANCEL) {
            Toast.makeText(this, "微信登录失败", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
