package com.renovation.ledger.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.reflect.TypeToken
import com.renovation.ledger.domain.taxonomy.Taxonomy
import com.renovation.ledger.domain.taxonomy.TaxonomyCatalog
import com.renovation.ledger.domain.taxonomy.TaxonomyIconRef
import com.renovation.ledger.domain.taxonomy.TaxonomyKind
import com.renovation.ledger.dsl.gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.taxonomyPrefsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "taxonomy_prefs",
)

/**
 * 标签（阶段/分类/空间）选项存储。选项列表沿用旧版纯字符串分隔存储（不改格式，天然兼容旧数据）；
 * 图标另开一组 `*_icons` key，JSON 存 value -> [TaxonomyIconRef]，旧版本没有该 key 时解码为空 Map。
 */
@Singleton
class TaxonomyPrefs @Inject constructor(
    @ApplicationContext private val ctx: Context,
) {
    private val stagesKey = stringPreferencesKey("stages")
    private val categoriesKey = stringPreferencesKey("categories")
    private val spacesKey = stringPreferencesKey("spaces")
    private val stagesIconsKey = stringPreferencesKey("stages_icons")
    private val categoriesIconsKey = stringPreferencesKey("categories_icons")
    private val spacesIconsKey = stringPreferencesKey("spaces_icons")

    val catalog: Flow<TaxonomyCatalog> =
        ctx.taxonomyPrefsDataStore.data.map { prefs ->
            TaxonomyCatalog(
                stages = decodeList(prefs[stagesKey], Taxonomy.STAGES),
                categories = decodeList(prefs[categoriesKey], Taxonomy.CATEGORIES),
                spaces = decodeList(prefs[spacesKey], Taxonomy.SPACES),
                stageIcons = decodeIcons(prefs[stagesIconsKey]),
                categoryIcons = decodeIcons(prefs[categoriesIconsKey]),
                spaceIcons = decodeIcons(prefs[spacesIconsKey]),
            )
        }

    suspend fun setOptions(kind: TaxonomyKind, values: List<String>) {
        val cleaned = sanitize(values)
        ctx.taxonomyPrefsDataStore.edit { prefs ->
            val encoded = encodeList(cleaned)
            prefs[keyOf(kind)] = encoded
            // 选项被整体替换时，孤立的图标条目一并裁掉
            val iconsKey = iconsKeyOf(kind)
            val icons = decodeIcons(prefs[iconsKey]).filterKeys { it in cleaned }
            prefs[iconsKey] = encodeIcons(icons)
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

            if (trimmed != oldValue) {
                val iconsKey = iconsKeyOf(kind)
                val icons = decodeIcons(prefs[iconsKey]).toMutableMap()
                val carried = icons.remove(oldValue)
                if (carried != null) {
                    icons[trimmed] = carried
                    prefs[iconsKey] = encodeIcons(icons)
                }
            }
        }
    }

    suspend fun removeOption(kind: TaxonomyKind, value: String) {
        ctx.taxonomyPrefsDataStore.edit { prefs ->
            val key = keyOf(kind)
            val current = decodeList(prefs[key], defaultsOf(kind)).toMutableList()
            current.removeAll { it == value }
            prefs[key] = encodeList(sanitize(current))

            val iconsKey = iconsKeyOf(kind)
            val icons = decodeIcons(prefs[iconsKey]).toMutableMap()
            if (icons.remove(value) != null) {
                prefs[iconsKey] = encodeIcons(icons)
            }
        }
    }

    /** 设置或清除某个标签值的图标；[icon] 为 null 或空引用即清除。 */
    suspend fun setIcon(kind: TaxonomyKind, value: String, icon: TaxonomyIconRef?) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return
        ctx.taxonomyPrefsDataStore.edit { prefs ->
            val iconsKey = iconsKeyOf(kind)
            val icons = decodeIcons(prefs[iconsKey]).toMutableMap()
            if (icon == null || !icon.isPresent) {
                icons.remove(trimmed)
            } else {
                icons[trimmed] = icon
            }
            prefs[iconsKey] = encodeIcons(icons)
        }
    }

    suspend fun resetToDefaults(kind: TaxonomyKind) {
        setOptions(kind, defaultsOf(kind))
        ctx.taxonomyPrefsDataStore.edit { prefs ->
            prefs[iconsKeyOf(kind)] = encodeIcons(emptyMap())
        }
    }

    suspend fun snapshot(): TaxonomyCatalog = catalog.first()

    /** 云同步成功后整表覆盖当前设备标签（跟账本走）。 */
    suspend fun replaceCatalog(catalog: TaxonomyCatalog) {
        ctx.taxonomyPrefsDataStore.edit { prefs ->
            prefs[stagesKey] = encodeList(sanitize(catalog.stages))
            prefs[categoriesKey] = encodeList(sanitize(catalog.categories))
            prefs[spacesKey] = encodeList(sanitize(catalog.spaces))
            prefs[stagesIconsKey] = encodeIcons(catalog.stageIcons)
            prefs[categoriesIconsKey] = encodeIcons(catalog.categoryIcons)
            prefs[spacesIconsKey] = encodeIcons(catalog.spaceIcons)
        }
    }

    private fun keyOf(kind: TaxonomyKind) = when (kind) {
        TaxonomyKind.STAGE -> stagesKey
        TaxonomyKind.CATEGORY -> categoriesKey
        TaxonomyKind.SPACE -> spacesKey
    }

    private fun iconsKeyOf(kind: TaxonomyKind) = when (kind) {
        TaxonomyKind.STAGE -> stagesIconsKey
        TaxonomyKind.CATEGORY -> categoriesIconsKey
        TaxonomyKind.SPACE -> spacesIconsKey
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

    private fun encodeIcons(icons: Map<String, TaxonomyIconRef>): String = gson.toJson(icons)

    private fun decodeIcons(raw: String?): Map<String, TaxonomyIconRef> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val type = object : TypeToken<Map<String, TaxonomyIconRef>>() {}.type
            gson.fromJson<Map<String, TaxonomyIconRef>>(raw, type) ?: emptyMap()
        }.getOrDefault(emptyMap())
    }
}
