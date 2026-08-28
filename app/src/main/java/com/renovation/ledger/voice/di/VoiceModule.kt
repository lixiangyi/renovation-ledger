package com.renovation.ledger.voice.di

import android.content.Context
import com.renovation.ledger.BuildConfig
import com.renovation.ledger.data.prefs.TaxonomyPrefs
import com.renovation.ledger.data.prefs.UserPrefs
import com.renovation.ledger.data.remote.CloudEnv
import com.renovation.ledger.voice.asr.AsrConfig
import com.renovation.ledger.voice.asr.AsrEngine
import com.renovation.ledger.voice.asr.CloudDashScopeAsrEngine
import com.renovation.ledger.voice.asr.DashScopeAsrClient
import com.renovation.ledger.voice.asr.HoldAudioRecorder
import com.renovation.ledger.voice.asr.HoldSpeechAsr
import com.renovation.ledger.voice.llm.AppContext
import com.renovation.ledger.voice.llm.DeepSeekProvider
import com.renovation.ledger.voice.llm.DefaultIntentParser
import com.renovation.ledger.voice.llm.LlmConfig
import com.renovation.ledger.voice.llm.LlmIntentParser
import com.renovation.ledger.voice.llm.LlmProvider
import com.renovation.ledger.voice.llm.OpenAiProvider
import com.renovation.ledger.voice.llm.SwitchingLlmProvider
import com.renovation.ledger.voice.tool.ToolRegistry
import com.renovation.ledger.voice.tool.executors.AddLedgerEntryExecutor
import com.renovation.ledger.voice.tool.executors.CloudEnvStore
import com.renovation.ledger.voice.tool.executors.CloudEnvStoreImpl
import com.renovation.ledger.voice.tool.executors.SwitchEnvExecutor
import com.renovation.ledger.voice.tool.executors.VoiceHostHolder
import com.renovation.ledger.voice.tool.executors.VoiceLedgerStore
import com.renovation.ledger.voice.tool.executors.VoiceLedgerStoreImpl
import com.renovation.ledger.voice.tool.executors.WechatLoginExecutor
import com.renovation.ledger.voice.ui.AiCredentialProvider
import com.renovation.ledger.voice.ui.DashScopeCredentialProvider
import com.renovation.ledger.voice.ui.VoiceAppContextFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LlmHttp

@Module
@InstallIn(SingletonComponent::class)
abstract class VoiceBindModule {
    @Binds
    @Singleton
    abstract fun bindVoiceLedgerStore(impl: VoiceLedgerStoreImpl): VoiceLedgerStore

    @Binds
    @Singleton
    abstract fun bindCloudEnvStore(impl: CloudEnvStoreImpl): CloudEnvStore
}

@Module
@InstallIn(SingletonComponent::class)
object VoiceModule {
    @Provides
    @Singleton
    fun asrConfig(): AsrConfig = AsrConfig()

    @Provides
    @Singleton
    fun llmConfig(): LlmConfig = LlmConfig()

    @Provides
    @Singleton
    fun dashScopeAsrClient(
        @LlmHttp client: OkHttpClient,
        userPrefs: UserPrefs,
    ): DashScopeAsrClient = DashScopeAsrClient(
        client = client,
        apiKeyProvider = { userPrefs.dashScopeApiKey.first() },
    )

    @Provides
    @Singleton
    fun holdAudioRecorder(@ApplicationContext context: Context): HoldAudioRecorder =
        HoldAudioRecorder(context)

    @Provides
    @Singleton
    fun cloudDashScopeAsrEngine(
        recorder: HoldAudioRecorder,
        client: DashScopeAsrClient,
    ): CloudDashScopeAsrEngine = CloudDashScopeAsrEngine(recorder, client)

    @Provides
    @Singleton
    fun holdSpeechAsr(engine: CloudDashScopeAsrEngine): HoldSpeechAsr = engine

    @Provides
    @Singleton
    fun asrEngine(engine: CloudDashScopeAsrEngine): AsrEngine = engine

    @Provides
    @Singleton
    fun dashScopeCredentialProvider(userPrefs: UserPrefs): DashScopeCredentialProvider =
        DashScopeCredentialProvider { userPrefs.dashScopeApiKey.first() }
    @Provides
    @Singleton
    @LlmHttp
    fun llmOkHttp(config: LlmConfig): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(config.timeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(60L.coerceAtLeast(config.timeoutSeconds), TimeUnit.SECONDS)
        .writeTimeout(60L.coerceAtLeast(config.timeoutSeconds), TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun aiCredentialProvider(userPrefs: UserPrefs): AiCredentialProvider = AiCredentialProvider {
        userPrefs.aiApiKey.first().ifBlank { BuildConfig.LLM_API_KEY }
    }

    @Provides
    @Singleton
    fun deepSeekProvider(
        @LlmHttp client: OkHttpClient,
        credentialProvider: AiCredentialProvider,
    ): DeepSeekProvider = DeepSeekProvider(client, apiKeyProvider = { credentialProvider.apiKey() })

    @Provides
    @Singleton
    fun openAiProvider(
        @LlmHttp client: OkHttpClient,
        credentialProvider: AiCredentialProvider,
    ): OpenAiProvider = OpenAiProvider(client, apiKeyProvider = { credentialProvider.apiKey() })

    @Provides
    @Singleton
    fun llmProvider(
        userPrefs: UserPrefs,
        deepSeek: DeepSeekProvider,
        openAi: OpenAiProvider,
    ): LlmProvider = SwitchingLlmProvider(
        selectedProvider = {
            userPrefs.aiProvider.first().ifBlank { BuildConfig.LLM_PROVIDER }.ifBlank { "deepseek" }
        },
        deepSeek = deepSeek,
        openAi = openAi,
    )

    @Provides
    @Singleton
    fun intentParser(provider: LlmProvider, config: LlmConfig): LlmIntentParser =
        DefaultIntentParser(provider, config)

    @Provides
    @Singleton
    fun voiceAppContextFactory(
        userPrefs: UserPrefs,
        taxonomyPrefs: TaxonomyPrefs,
    ): VoiceAppContextFactory = VoiceAppContextFactory {
        val env = userPrefs.cloudEnv.first()
        val jwt = userPrefs.jwt.first()
        val catalog = taxonomyPrefs.catalog.first()
        AppContext(
            currentEnv = if (env == CloudEnv.Kind.PROD) "prod" else "dev",
            isLoggedIn = !jwt.isNullOrBlank(),
            isDebugBuild = BuildConfig.ENABLE_DEBUG_PANEL,
            availableCategories = catalog.categories,
            availableStages = catalog.stages,
        )
    }

    @Provides
    @Singleton
    fun toolRegistry(
        addLedgerEntryExecutor: AddLedgerEntryExecutor,
        switchEnvExecutor: SwitchEnvExecutor,
        wechatLoginExecutor: WechatLoginExecutor,
    ): ToolRegistry {
        val tools = mutableListOf(
            addLedgerEntryExecutor,
            wechatLoginExecutor,
        )
        if (BuildConfig.ENABLE_DEBUG_PANEL) {
            tools.add(1, switchEnvExecutor)
        }
        return ToolRegistry(tools)
    }
}
