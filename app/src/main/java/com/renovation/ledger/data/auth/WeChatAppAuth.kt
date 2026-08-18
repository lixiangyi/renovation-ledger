package com.renovation.ledger.data.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import com.renovation.ledger.BuildConfig
import com.tencent.mm.opensdk.modelmsg.SendAuth
import com.tencent.mm.opensdk.openapi.IWXAPI
import com.tencent.mm.opensdk.openapi.WXAPIFactory

object WeChatAppAuth {
    fun appId(): String = BuildConfig.WECHAT_APP_ID.trim()

    fun api(context: Context): IWXAPI {
        val api = WXAPIFactory.createWXAPI(context.applicationContext, appId(), true)
        api.registerApp(appId())
        return api
    }

    fun sendAuth(activity: Activity): String? {
        if (appId().isEmpty()) return "尚未配置微信 AppId，开发包请用开发登录"
        val api = api(activity)
        if (!api.isWXAppInstalled) return "请先安装微信"
        val req = SendAuth.Req()
        req.scope = "snsapi_userinfo"
        req.state = "ledger_login"
        api.sendReq(req)
        return null
    }

    fun findActivity(context: Context): Activity? = when (context) {
        is Activity -> context
        is ContextWrapper -> findActivity(context.baseContext)
        else -> null
    }
}
