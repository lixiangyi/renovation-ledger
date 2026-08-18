package com.renovation.ledger.di

import com.renovation.ledger.BuildConfig
import com.renovation.ledger.data.remote.CloudEnv
import com.renovation.ledger.data.remote.LedgerApi
import com.renovation.ledger.ui.debug.netrecord.NetRecordInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerEndpoint @Inject constructor() {
    @Volatile
    var baseUrl: String = CloudEnv.defaultUrl()
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun okHttp(endpoint: ServerEndpoint): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            // 系统/抓包代理会劫持 127.0.0.1，导致 adb reverse 返回 502/503
            .proxySelector(object : ProxySelector() {
                override fun select(uri: URI?): List<Proxy> {
                    val host = uri?.host.orEmpty()
                    if (host == "127.0.0.1" || host == "localhost" || host == "::1") {
                        return listOf(Proxy.NO_PROXY)
                    }
                    return ProxySelector.getDefault()?.select(uri) ?: listOf(Proxy.NO_PROXY)
                }

                override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
                    ProxySelector.getDefault()?.connectFailed(uri, sa, ioe)
                }
            })
            .addInterceptor(Interceptor { chain ->
                val original = chain.request()
                val parsed = endpoint.baseUrl.toHttpUrlOrNull()
                    ?: return@Interceptor chain.proceed(original)
                val rewritten = original.url.newBuilder()
                    .scheme(parsed.scheme)
                    .host(parsed.host)
                    .port(parsed.port)
                    .build()
                chain.proceed(original.newBuilder().url(rewritten).build())
            })
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(NetRecordInterceptor())
        }
        return builder.build()
    }

    @Provides
    @Singleton
    fun ledgerApi(client: OkHttpClient): LedgerApi = Retrofit.Builder()
        .baseUrl(CloudEnv.DEV_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(LedgerApi::class.java)
}
