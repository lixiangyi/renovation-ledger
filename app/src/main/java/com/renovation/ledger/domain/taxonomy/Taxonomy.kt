package com.renovation.ledger.domain.taxonomy

/**
 * 预算项三维标签：
 * - 阶段 stage：施工/推进阶段（水电、泥木…）
 * - 分类 category：费用大类（家具、家电、硬装…）；旧账本「所属类别」落此字段
 * - 空间 space：房间/区域（客厅、主卧…）
 */
object Taxonomy {
    val STAGES = listOf(
        "水电", "泥木", "油漆", "硬装", "软装", "主材", "验收", "其他",
    )

    val CATEGORIES = listOf(
        "家具", "家电", "软装", "硬装", "卫浴", "全屋定制", "全屋智能", "全屋净水", "其他",
    )

    val SPACES = listOf(
        "全屋", "客厅", "主卧", "次卧", "厨房", "卫生间", "阳台", "玄关", "其他",
    )

    const val BLANK_OPTION = "（不填）"
}
