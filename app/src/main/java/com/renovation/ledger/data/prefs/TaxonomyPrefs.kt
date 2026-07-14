package com.renovation.ledger.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.renovation.ledger.domain.taxonomy.Taxonomy
import com.renovation.ledger.domain.taxonomy.TaxonomyCatalog
import com.renovation.ledger.domain.taxonomy.TaxonomyKind
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.taxonomyPrefsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "taxonomy_prefs",
)

@Singleton
class TaxonomyPrefs @Inject constructor(
    @ApplicationContext private val ctx: Context,
) {
    private val stagesKey = stringPreferencesKey("stages")
    private val categoriesKey = stringPreferencesKey("categories")
    private val spacesKey = stringPreferencesKey("spaces")

    val catalog: Flow<TaxonomyCatalog> =
        ctx.taxonomyPrefsDataStore.data.map { prefs ->
            TaxonomyCatalog(
                stages = decodeList(prefs[stagesKey], Taxonomy.STAGES),
                categories = decodeList(prefs[categoriesKey], Taxonomy.CATEGORIES),
                spaces = decodeList(prefs[spacesKey], Taxonomy.SPACES),
            )
        }

    suspend fun setOptions(kind: TaxonomyKind, values: List<String>) {
        val cleaned = sanitize(values)
        ctx.taxonomyPrefsDataStore.edit { prefs ->
            val encoded = encodeList(cleaned)
            when (kind) {
                TaxonomyKind.STAGE -> prefs[stagesKey] = encoded
                TaxonomyKind.CATEGORY -> prefs[categoriesKey] = encoded
                TaxonomyKind.SPACE -> prefs[spacesKey] = encoded
            }
        }
    }

    suspend fun addOption(kind: TaxonomyKind, value: String) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return
        ctx.taxonomyPrefsDataStore.edit { prefs ->
            val key = keyOf(kind)
            val current = decodeList(prefs[key], defaultsOf(kind)).toMutableList()
            if (trimmed !in current) {
                current.add(trimmed)
                prefs[key] = encodeList(current)
            }
        }
    }

    suspend fun renameOption(kind: TaxonomyKind, oldValue: String, newValue: String) {
        val trimmed = newValue.trim()
        if (trimmed.isEmpty()) return
        ctx.taxonomyPrefsDataStore.edit { prefs ->
            val key = keyOf(kind)
            val current = decodeList(prefs[key], defaultsOf(kind)).toMutableList()
            val index = current.indexOf(oldValue)
            if (index < 0) return@edit
            if (trimmed != oldValue && trimmed in current) {
                current.removeAt(index)
            } else {
                current[index] = trimmed
            }
            prefs[key] = encodeList(current)
        }
    }

    suspend fun removeOption(kind: TaxonomyKind, value: String) {
        ctx.taxonomyPrefsDataStore.edit { prefs ->
            val key = keyOf(kind)
            val current = decodeList(prefs[key], defaultsOf(kind)).toMutableList()
            current.removeAll { it == value }
            prefs[key] = encodeList(sanitize(current))
        }
    }

    suspend fun resetToDefaults(kind: TaxonomyKind) {
        setOptions(kind, defaultsOf(kind))
    }

    private fun keyOf(kind: TaxonomyKind) = when (kind) {
        TaxonomyKind.STAGE -> stagesKey
        TaxonomyKind.CATEGORY -> categoriesKey
        TaxonomyKind.SPACE -> spacesKey
    }

    private fun defaultsOf(kind: TaxonomyKind): List<String> = when (kind) {
        TaxonomyKind.STAGE -> Taxonomy.STAGES
        TaxonomyKind.CATEGORY -> Taxonomy.CATEGORIES
        TaxonomyKind.SPACE -> Taxonomy.SPACES
    }

    private fun sanitize(values: List<String>): List<String> =
        values.map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    private fun encodeList(values: List<String>): String =
        values.joinToString("\u001f")

    private fun decodeList(raw: String?, defaults: List<String>): List<String> {
        if (raw == null) return defaults
        if (raw.isEmpty()) return emptyList()
        val parsed = raw.split('\u001f').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        return parsed.ifEmpty { defaults }
    }
}
