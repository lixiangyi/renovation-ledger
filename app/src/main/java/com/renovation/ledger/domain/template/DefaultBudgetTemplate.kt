package com.renovation.ledger.domain.template

import com.renovation.ledger.domain.model.BudgetItem
import java.util.UUID

object DefaultBudgetTemplate {
    fun items(projectId: String): List<BudgetItem> = listOf(
        item(projectId, "水电改造", stage = "水电", category = "硬装", space = "全屋", budget = 20_000_00L),
        item(projectId, "强弱电布线", stage = "水电", category = "硬装", space = "全屋", budget = 8_000_00L),
        item(projectId, "瓷砖", stage = "泥木", category = "硬装", space = "全屋", budget = 15_000_00L),
        item(projectId, "木地板", stage = "泥木", category = "硬装", space = "全屋", budget = 12_000_00L),
        item(projectId, "吊顶", stage = "泥木", category = "硬装", space = "全屋", budget = 8_000_00L),
        item(projectId, "墙漆", stage = "油漆", category = "硬装", space = "全屋", budget = 10_000_00L),
        item(projectId, "橱柜", stage = "主材", category = "全屋定制", space = "厨房", budget = 25_000_00L),
        item(projectId, "卫浴洁具", stage = "主材", category = "卫浴", space = "卫生间", budget = 15_000_00L),
        item(projectId, "室内门", stage = "主材", category = "全屋定制", space = "全屋", budget = 12_000_00L),
        item(projectId, "窗帘", stage = "软装", category = "软装", space = "全屋", budget = 5_000_00L),
        item(projectId, "灯具", stage = "软装", category = "软装", space = "全屋", budget = 6_000_00L),
        item(projectId, "空调", stage = "主材", category = "家电", space = "全屋", budget = 20_000_00L),
    )

    private fun item(
        projectId: String,
        name: String,
        stage: String,
        category: String,
        space: String,
        budget: Long,
    ) = BudgetItem(
        id = UUID.randomUUID().toString(),
        projectId = projectId,
        name = name,
        stage = stage,
        category = category,
        space = space,
        budgetAmount = budget,
    )
}
