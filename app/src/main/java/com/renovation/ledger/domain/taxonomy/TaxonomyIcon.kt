package com.renovation.ledger.domain.taxonomy

/**
 * 标签图标引用：预置图标存 [iconKey]，相册自定义图片存本地文件路径 [iconPath]。
 * 两者互斥，UI 层按优先级（自定义图片 > 预置图标）渲染。
 */
data class TaxonomyIconRef(
    val iconKey: String? = null,
    val iconPath: String? = null,
) {
    val isPresent: Boolean get() = !iconKey.isNullOrBlank() || !iconPath.isNullOrBlank()
}

/**
 * 预置图标 key 列表，具体图形（ImageVector）映射在 UI 层，domain 不依赖 Compose。
 */
object TaxonomyIconPresets {
    val KEYS = listOf(
        "water_drop",
        "construction",
        "format_paint",
        "chair",
        "weekend",
        "kitchen",
        "bathtub",
        "electric_bolt",
        "home",
        "bed",
        "deck",
        "meeting_room",
        "verified",
        "local_shipping",
        "lightbulb",
        "category",
    )
}
