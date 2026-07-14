package com.renovation.ledger.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.renovation.ledger.domain.metrics.HealthColorResolver
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
}
