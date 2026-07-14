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
) {
    fun options(kind: TaxonomyKind): List<String> = when (kind) {
        TaxonomyKind.STAGE -> stages
        TaxonomyKind.CATEGORY -> categories
        TaxonomyKind.SPACE -> spaces
    }
}

fun TaxonomyKind.label(): String = when (this) {
    TaxonomyKind.STAGE -> "阶段"
    TaxonomyKind.CATEGORY -> "分类"
    TaxonomyKind.SPACE -> "空间"
}
