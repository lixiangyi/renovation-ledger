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
import com.renovation.ledger.domain.list.PaymentListGroupBy
import com.renovation.ledger.domain.list.PaymentListLayout
import com.renovation.ledger.domain.metrics.HealthColorResolver
import com.renovation.ledger.domain.search.ItemNameSearch
import com.renovation.ledger.dsl.gson
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

    suspend fun setHealthColorEnabled(enabled: Boolean) {
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

    private fun encodeSearchHistory(values: List<String>): String = gson.toJson(values)

    private fun decodeSearchHistory(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            gson.fromJson<List<String>>(raw, object : TypeToken<List<String>>() {}.type)
        }.getOrNull()?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
    }
}
