package com.renovation.ledger.domain.taxonomy

enum class TaxonomyKind {
    STAGE,
    CATEGORY,
    SPACE,
}

data class TaxonomyCatalog(
    val stages: List<String> = Taxonomy.STAGES,
    val categories: List<String> = Taxonomy.CATEGORIES,
    val spaces: List<String> = Taxonomy.SPACES,
    val stageIcons: Map<String, TaxonomyIconRef> = emptyMap(),
    val categoryIcons: Map<String, TaxonomyIconRef> = emptyMap(),
    val spaceIcons: Map<String, TaxonomyIconRef> = emptyMap(),
) {
    fun options(kind: TaxonomyKind): List<String> = when (kind) {
        TaxonomyKind.STAGE -> stages
        TaxonomyKind.CATEGORY -> categories
        TaxonomyKind.SPACE -> spaces
    }

    fun icons(kind: TaxonomyKind): Map<String, TaxonomyIconRef> = when (kind) {
        TaxonomyKind.STAGE -> stageIcons
        TaxonomyKind.CATEGORY -> categoryIcons
        TaxonomyKind.SPACE -> spaceIcons
    }

    /** 按标签值取图标；未设置图标或已被删除的标签值返回 null。 */
    fun iconFor(kind: TaxonomyKind, value: String): TaxonomyIconRef? =
        icons(kind)[value]?.takeIf { it.isPresent }
}

fun TaxonomyKind.label(): String = when (this) {
    TaxonomyKind.STAGE -> "阶段"
    TaxonomyKind.CATEGORY -> "分类"
    TaxonomyKind.SPACE -> "空间"
}
