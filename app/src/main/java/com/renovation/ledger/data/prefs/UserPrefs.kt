package com.renovation.ledger.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.reflect.TypeToken
import com.renovation.ledger.data.remote.CloudEnv
import com.renovation.ledger.domain.list.PaymentListGroupBy
import com.renovation.ledger.domain.list.PaymentListLayout
import com.renovation.ledger.domain.metrics.HealthColorResolver
import com.renovation.ledger.domain.model.HealthLevel
import com.renovation.ledger.domain.search.ItemNameSearch
import com.renovation.ledger.dsl.gson
import com.renovation.ledger.ui.theme.HealthThemeBootstrap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userPrefsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_prefs",
)

data class UserProfile(
    val nickname: String = "我",
    val avatarPath: String? = null,
)

@Singleton
class UserPrefs @Inject constructor(
    @ApplicationContext private val ctx: Context,
) {
    private val healthColorEnabledKey = booleanPreferencesKey("health_color_enabled")
    private val mildOverMaxPercentKey = intPreferencesKey("mild_over_max_percent")
    private val nicknameKey = stringPreferencesKey("user_nickname")
    private val avatarPathKey = stringPreferencesKey("user_avatar_path")
    private val currentProjectIdKey = stringPreferencesKey("current_project_id")
    private val paymentListGroupByKey = stringPreferencesKey("payment_list_group_by")
    private val paymentListLayoutKey = stringPreferencesKey("payment_list_layout")
    private val searchHistoryKey = stringPreferencesKey("search_history")
    private val jwtKey = stringPreferencesKey("cloud_jwt")
    private val cloudUserIdKey = stringPreferencesKey("cloud_user_id")
    private val phoneKey = stringPreferencesKey("cloud_phone")
    private val serverBaseUrlKey = stringPreferencesKey("server_base_url")
    private val cloudEnvKey = stringPreferencesKey("cloud_env")
    private val aiProviderKey = stringPreferencesKey("ai_provider")
    private val aiApiKeyKey = stringPreferencesKey("ai_api_key")
    private val dashScopeApiKeyKey = stringPreferencesKey("dashscope_api_key")
    private val pendingBindProjectIdKey = stringPreferencesKey("pending_bind_project_id")
    private val pendingBindProjectNameKey = stringPreferencesKey("pending_bind_project_name")

    /** Sync mirror for cold-start theme (DataStore is async; first Compose frame needs this). */
    private val themeBootstrapPrefs by lazy {
        ctx.getSharedPreferences("theme_bootstrap", Context.MODE_PRIVATE)
    }

    val healthColorEnabled: Flow<Boolean> =
        ctx.userPrefsDataStore.data.map { prefs ->
            prefs[healthColorEnabledKey] ?: true
        }

    val mildOverMaxPercent: Flow<Int> =
        ctx.userPrefsDataStore.data.map { prefs ->
            HealthColorResolver.clampPercent(
                prefs[mildOverMaxPercentKey]
                    ?: HealthColorResolver.DEFAULT_MILD_OVER_MAX_PERCENT,
            )
        }

    val currentProjectId: Flow<String?> =
        ctx.userPrefsDataStore.data.map { prefs ->
            prefs[currentProjectIdKey]?.trim()?.takeIf { it.isNotEmpty() }
        }

    val userProfile: Flow<UserProfile> =
        ctx.userPrefsDataStore.data.map { prefs ->
            UserProfile(
                nickname = prefs[nicknameKey]?.trim().orEmpty().ifBlank { "我" },
                avatarPath = prefs[avatarPathKey]?.trim()?.takeIf { it.isNotEmpty() },
            )
        }

    val paymentListGroupBy: Flow<PaymentListGroupBy> =
        ctx.userPrefsDataStore.data.map { prefs ->
            when (prefs[paymentListGroupByKey]) {
                "category" -> PaymentListGroupBy.CATEGORY
                "space" -> PaymentListGroupBy.SPACE
                "stage" -> PaymentListGroupBy.STAGE
                else -> PaymentListGroupBy.STAGE
            }
        }

    val paymentListLayout: Flow<PaymentListLayout> =
        ctx.userPrefsDataStore.data.map { prefs ->
            when (prefs[paymentListLayoutKey]) {
                "flat" -> PaymentListLayout.FLAT
                "nested" -> PaymentListLayout.NESTED
                else -> PaymentListLayout.NESTED
            }
        }

    val searchHistory: Flow<List<String>> =
        ctx.userPrefsDataStore.data.map { prefs ->
            decodeSearchHistory(prefs[searchHistoryKey])
        }

    val jwt: Flow<String?> =
        ctx.userPrefsDataStore.data.map { prefs ->
            prefs[jwtKey]?.trim()?.takeIf { it.isNotEmpty() }
        }

    val cloudUserId: Flow<String?> =
        ctx.userPrefsDataStore.data.map { prefs ->
            prefs[cloudUserIdKey]?.trim()?.takeIf { it.isNotEmpty() }
        }

    val phone: Flow<String?> =
        ctx.userPrefsDataStore.data.map { prefs ->
            prefs[phoneKey]?.trim()?.takeIf { it.isNotEmpty() }
        }

    val cloudEnv: Flow<CloudEnv.Kind> =
        ctx.userPrefsDataStore.data.map { prefs ->
            CloudEnv.kindOf(prefs[cloudEnvKey])
        }

    val serverBaseUrl: Flow<String> =
        ctx.userPrefsDataStore.data.map { prefs ->
            val stored = prefs[serverBaseUrlKey]?.trim()?.takeIf { it.isNotEmpty() }
            when {
                stored == null -> CloudEnv.defaultUrl()
                CloudEnv.isLegacyDebugDefault(stored) -> CloudEnv.defaultUrl()
                else -> CloudEnv.normalizeUrl(stored)
            }
        }

    val aiProvider: Flow<String> =
        ctx.userPrefsDataStore.data.map { prefs ->
            prefs[aiProviderKey]?.trim()?.takeIf { it.isNotEmpty() } ?: "deepseek"
        }

    val aiApiKey: Flow<String> =
        ctx.userPrefsDataStore.data.map { prefs ->
            prefs[aiApiKeyKey]?.trim().orEmpty()
        }

    val dashScopeApiKey: Flow<String> =
        ctx.userPrefsDataStore.data.map { prefs ->
            prefs[dashScopeApiKeyKey]?.trim().orEmpty()
        }

    /** 登录后待展示「绑定账本」弹窗；null 表示无。 */
    val pendingBindPrompt: Flow<Pair<String, String>?> =
        ctx.userPrefsDataStore.data.map { prefs ->
            val id = prefs[pendingBindProjectIdKey]?.trim()?.takeIf { it.isNotEmpty() }
            val name = prefs[pendingBindProjectNameKey]?.trim().orEmpty()
            if (id == null) null else id to name.ifBlank { "当前账本" }
        }

    fun peekLastHealthLevel(): HealthLevel =
        HealthThemeBootstrap.parseLevel(themeBootstrapPrefs.getString(KEY_LAST_HEALTH_LEVEL, null))

    fun peekHealthColorEnabled(): Boolean =
        themeBootstrapPrefs.getBoolean(KEY_LAST_HEALTH_COLOR_ENABLED, true)

    fun cacheThemeBootstrap(level: HealthLevel, enabled: Boolean) {
        themeBootstrapPrefs.edit()
            .putString(KEY_LAST_HEALTH_LEVEL, HealthThemeBootstrap.serializeLevel(level))
            .putBoolean(KEY_LAST_HEALTH_COLOR_ENABLED, enabled)
            .apply()
    }

    suspend fun setHealthColorEnabled(enabled: Boolean) {
        cacheThemeBootstrap(peekLastHealthLevel(), enabled)
        ctx.userPrefsDataStore.edit { prefs ->
            prefs[healthColorEnabledKey] = enabled
        }
    }

    suspend fun setMildOverMaxPercent(percent: Int) {
        ctx.userPrefsDataStore.edit { prefs ->
            prefs[mildOverMaxPercentKey] = HealthColorResolver.clampPercent(percent)
        }
    }

    suspend fun setCurrentProjectId(id: String) {
        val value = id.trim()
        if (value.isEmpty()) return
        ctx.userPrefsDataStore.edit { prefs ->
            prefs[currentProjectIdKey] = value
        }
    }

    suspend fun setNickname(nickname: String) {
        val value = nickname.trim().ifBlank { "我" }
        ctx.userPrefsDataStore.edit { prefs ->
            prefs[nicknameKey] = value
        }
    }

    suspend fun setAvatarPath(path: String?) {
        ctx.userPrefsDataStore.edit { prefs ->
            if (path.isNullOrBlank()) {
                prefs.remove(avatarPathKey)
            } else {
                prefs[avatarPathKey] = path
            }
        }
    }

    suspend fun setPaymentListGroupBy(value: PaymentListGroupBy) {
        ctx.userPrefsDataStore.edit { prefs ->
            prefs[paymentListGroupByKey] = when (value) {
                PaymentListGroupBy.STAGE -> "stage"
                PaymentListGroupBy.CATEGORY -> "category"
                PaymentListGroupBy.SPACE -> "space"
            }
        }
    }

    suspend fun setPaymentListLayout(value: PaymentListLayout) {
        ctx.userPrefsDataStore.edit { prefs ->
            prefs[paymentListLayoutKey] = when (value) {
                PaymentListLayout.NESTED -> "nested"
                PaymentListLayout.FLAT -> "flat"
            }
        }
    }

    suspend fun addSearchHistory(query: String) {
        ctx.userPrefsDataStore.edit { prefs ->
            val existing = decodeSearchHistory(prefs[searchHistoryKey])
            val updated = ItemNameSearch.pushHistory(existing, query)
            prefs[searchHistoryKey] = encodeSearchHistory(updated)
        }
    }

    suspend fun clearSearchHistory() {
        ctx.userPrefsDataStore.edit { prefs ->
            prefs.remove(searchHistoryKey)
        }
    }

    suspend fun setJwt(token: String?, userId: String?, phone: String? = null) {
        ctx.userPrefsDataStore.edit { prefs ->
            if (token.isNullOrBlank()) {
                prefs.remove(jwtKey)
                prefs.remove(cloudUserIdKey)
                prefs.remove(phoneKey)
                prefs[nicknameKey] = "我"
                prefs.remove(avatarPathKey)
            } else {
                prefs[jwtKey] = token
                if (!userId.isNullOrBlank()) prefs[cloudUserIdKey] = userId
                if (!phone.isNullOrBlank()) {
                    prefs[phoneKey] = phone
                }
            }
        }
    }

    suspend fun setPhone(phone: String?) {
        ctx.userPrefsDataStore.edit { prefs ->
            if (phone.isNullOrBlank()) prefs.remove(phoneKey) else prefs[phoneKey] = phone
        }
    }

    suspend fun setServerBaseUrl(url: String) {
        ctx.userPrefsDataStore.edit { prefs ->
            prefs[serverBaseUrlKey] = CloudEnv.normalizeUrl(url)
        }
    }

    suspend fun setCloudEnv(kind: CloudEnv.Kind, serverUrl: String? = null) {
        ctx.userPrefsDataStore.edit { prefs ->
            prefs[cloudEnvKey] = CloudEnv.storageValue(kind)
            prefs[serverBaseUrlKey] = CloudEnv.normalizeUrl(serverUrl ?: CloudEnv.urlOf(kind))
        }
    }

    suspend fun setAiProvider(value: String) {
        val cleaned = value.trim().ifBlank { "deepseek" }
        ctx.userPrefsDataStore.edit { prefs ->
            prefs[aiProviderKey] = cleaned
        }
    }

    suspend fun setAiApiKey(value: String) {
        ctx.userPrefsDataStore.edit { prefs ->
            val cleaned = value.trim()
            if (cleaned.isEmpty()) {
                prefs.remove(aiApiKeyKey)
            } else {
                prefs[aiApiKeyKey] = cleaned
            }
        }
    }

    suspend fun setDashScopeApiKey(value: String) {
        ctx.userPrefsDataStore.edit { prefs ->
            val cleaned = value.trim()
            if (cleaned.isEmpty()) {
                prefs.remove(dashScopeApiKeyKey)
            } else {
                prefs[dashScopeApiKeyKey] = cleaned
            }
        }
    }

    suspend fun setPendingBindPrompt(projectId: String, projectName: String) {
        val id = projectId.trim()
        if (id.isEmpty()) return
        ctx.userPrefsDataStore.edit { prefs ->
            prefs[pendingBindProjectIdKey] = id
            prefs[pendingBindProjectNameKey] = projectName.trim().ifBlank { "当前账本" }
        }
    }

    suspend fun clearPendingBindPrompt() {
        ctx.userPrefsDataStore.edit { prefs ->
            prefs.remove(pendingBindProjectIdKey)
            prefs.remove(pendingBindProjectNameKey)
        }
    }

    private fun encodeSearchHistory(values: List<String>): String = gson.toJson(values)

    private fun decodeSearchHistory(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            gson.fromJson<List<String>>(raw, object : TypeToken<List<String>>() {}.type)
        }.getOrNull()?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
    }

    private companion object {
        const val KEY_LAST_HEALTH_LEVEL = "last_health_level"
        const val KEY_LAST_HEALTH_COLOR_ENABLED = "last_health_color_enabled"
    }
}