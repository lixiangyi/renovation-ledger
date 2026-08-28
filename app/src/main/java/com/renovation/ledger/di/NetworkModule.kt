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
import java.net.Proxy
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
            // Wi‑Fi 系统代理（Charles 等）在电脑没开时会让云地址也超时
            .proxy(Proxy.NO_PROXY)
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
        .baseUrl(CloudEnv.defaultUrl())
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(LedgerApi::class.java)
}
