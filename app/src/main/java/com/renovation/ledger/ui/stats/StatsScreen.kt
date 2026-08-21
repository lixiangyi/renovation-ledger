package com.renovation.ledger.ui.stats

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import com.renovation.ledger.ui.common.CompactTopAppBar
import com.renovation.ledger.ui.common.ZeroTopAppBarWindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.renovation.ledger.domain.metrics.GroupBy
import com.renovation.ledger.domain.metrics.GroupMetrics
import com.renovation.ledger.domain.metrics.PieMetric
import com.renovation.ledger.domain.metrics.interleaveLargeAndSmall
import com.renovation.ledger.ui.common.formatYuan
import com.renovation.ledger.ui.common.healthColor
import java.text.DecimalFormat

private val PiePercentFormat = DecimalFormat("##0.#")

internal enum class PieLabelKind {
    /** 图上不标，只看图例 */
    NONE,
    /** 外侧仅百分比 */
    PCT_ONLY,
    /** 名称 + 百分比 */
    NAME_PCT,
}

/** 有占比的扇区一律外侧标注，重叠由引线错开处理。 */
private fun resolvePieLabelKinds(percents: List<Double>): List<PieLabelKind> {
    return percents.map { PieLabelKind.NAME_PCT }
}

private fun formatPieSliceLabel(
    kind: PieLabelKind,
    key: String,
    percent: Float,
): String {
    val pct = PiePercentFormat.format(percent.toDouble()) + "%"
    return when (kind) {
        PieLabelKind.NONE -> ""
        PieLabelKind.PCT_ONLY -> {
            val name = shortPieLabel(key, maxLen = 4)
            if (name.isEmpty()) pct else "$name $pct"
        }
        PieLabelKind.NAME_PCT -> {
            val name = shortPieLabel(key, maxLen = 6)
            if (name.isEmpty()) pct else "$name $pct"
        }
    }
}

private fun labelColorForSlice(color: Color): Int {
    val r = color.red
    val g = color.green
    val b = color.blue
    val luminance = 0.2126f * r + 0.7152f * g + 0.0722f * b
    return if (luminance > 0.62f) AndroidColor.DKGRAY else AndroidColor.WHITE
}

private fun shortPieLabel(raw: String, maxLen: Int = 4): String {
    val t = raw.trim()
    return when {
        t.isEmpty() -> ""
        t.length <= maxLen -> t
        else -> t.take(maxLen - 1) + "…"
    }
}
private val PieColors = listOf(
    Color(0xFF5C6BC0),
    Color(0xFF26A69A),
    Color(0xFFFFA726),
    Color(0xFFEF5350),
    Color(0xFFAB47BC),
    Color(0xFF42A5F5),
    Color(0xFF66BB6A),
    Color(0xFF8D6E63),
)

private val BarColors = listOf(
    Color(0xFF7986CB),
    Color(0xFF4DB6AC),
    Color(0xFFFFB74D),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onOpenItem: (String) -> Unit,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = { Text("统计") })
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            GroupByTabs(
                selected = uiState.groupBy,
                onSelected = viewModel::setGroupBy,
            )

            PieMetricChips(
                selected = uiState.pieMetric,
                onSelected = viewModel::setPieMetric,
            )

            PieChartSection(
                groups = uiState.groups,
                metric = uiState.pieMetric,
                groupBy = uiState.groupBy,
            )

            BarChartSection(groups = uiState.groups)

            GroupMetricsList(
                groups = uiState.groups,
                healthColorEnabled = uiState.healthColorEnabled,
                viewModel = viewModel,
            )

            TopContractGapSection(
                rows = uiState.topContractGaps,
                healthColorEnabled = uiState.healthColorEnabled,
                viewModel = viewModel,
                onOpenItem = onOpenItem,
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun GroupByTabs(
    selected: GroupBy,
    onSelected: (GroupBy) -> Unit,
) {
    val tabs = listOf(
        GroupBy.CATEGORY to "按分类",
        GroupBy.STAGE to "按阶段",
        GroupBy.SPACE to "按空间",
    )
    val selectedIndex = tabs.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    TabRow(selectedTabIndex = selectedIndex) {
        tabs.forEachIndexed { index, (groupBy, label) ->
            Tab(
                selected = index == selectedIndex,
                onClick = { onSelected(groupBy) },
                text = { Text(label) },
            )
        }
    }
}

@Composable
private fun PieMetricChips(
    selected: PieMetric,
    onSelected: (PieMetric) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == PieMetric.PAID,
            onClick = { onSelected(PieMetric.PAID) },
            label = { Text("已花费") },
        )
        FilterChip(
            selected = selected == PieMetric.PROJECTED,
            onClick = { onSelected(PieMetric.PROJECTED) },
            label = { Text("预计花费") },
        )
        FilterChip(
            selected = selected == PieMetric.BUDGET,
            onClick = { onSelected(PieMetric.BUDGET) },
            label = { Text("预算") },
        )
    }
}

@Composable
private fun PieChartSection(
    groups: List<GroupMetrics>,
    metric: PieMetric,
    groupBy: GroupBy,
) {
    val metricLabel = when (metric) {
        PieMetric.PAID -> "已花费"
        PieMetric.PROJECTED -> "预计花费"
        PieMetric.BUDGET -> "预算"
    }
    val groupByLabel = when (groupBy) {
        GroupBy.STAGE -> "按阶段"
        GroupBy.CATEGORY -> "按分类"
        GroupBy.SPACE -> "按空间"
    }
    val legendGroups = remember(groups, metric) {
        groups.filter { it.pieValue(metric) > 0L }
    }
    val pieGroups = remember(legendGroups, metric) {
        interleaveLargeAndSmall(legendGroups) { it.pieValue(metric) }
    }
    val total = remember(legendGroups, metric) {
        legendGroups.sumOf { it.pieValue(metric) }
    }
    val dataSignature = remember(pieGroups, metric) {
        pieGroups.joinToString { "${it.key}:${it.pieValue(metric)}" } + "|${metric.name}"
    }
    val slicePercents = remember(pieGroups, metric, total) {
        pieGroups.map { group -> group.pieValue(metric) * 100.0 / total }
    }
    val labelKinds = remember(slicePercents) { resolvePieLabelKinds(slicePercents) }
    val colorByKey = remember(legendGroups) {
        legendGroups.mapIndexed { index, group ->
            group.key to PieColors[index % PieColors.size]
        }.toMap()
    }
    val sliceColors = remember(pieGroups, colorByKey) {
        pieGroups.map { group -> colorByKey[group.key] ?: PieColors[0] }
    }
    var selectedKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(dataSignature) {
        // 默认选最大扇区，让中间指示气泡在“未点击前”也能快速对应百分比
        selectedKey = pieGroups.maxByOrNull { it.pieValue(metric) }?.key
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "$metricLabel · 占比分布",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "$groupByLabel 查看各分组在「$metricLabel」中的占比",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (pieGroups.isEmpty() || total <= 0L) {
                Text(
                    text = "暂无数据",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "合计 $metricLabel ${formatYuan(total)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                    ) {
                        AndroidView(
                            factory = { context ->
                                PieChart(context).apply {
                                    description.isEnabled = false
                                    legend.isEnabled = false
                                    setUsePercentValues(true)
                                    setDrawEntryLabels(false)
                                    setHoleColor(AndroidColor.TRANSPARENT)
                                    setTransparentCircleAlpha(0)
                                    // 未选中时中空尽量小，接近实心饼
                                    holeRadius = 2f
                                    transparentCircleRadius = 2f
                                    setNoDataText("暂无数据")
                                    setTouchEnabled(true)
                                    isRotationEnabled = false
                                    isHighlightPerTapEnabled = true
                                    setExtraOffsets(12f, 10f, 12f, 10f)
                                    setMinAngleForSlices(0f)
                                    renderer = LeaderLinePieChartRenderer(this, animator, viewPortHandler)
                                }
                            },
                            update = { chart ->
                                val leaderRenderer = chart.renderer as? LeaderLinePieChartRenderer
                                chart.setOnChartValueSelectedListener(
                                    object : OnChartValueSelectedListener {
                                        override fun onValueSelected(e: Entry?, h: Highlight?) {
                                            val idx = h?.x?.toInt()
                                            selectedKey = idx
                                                ?.takeIf { it in pieGroups.indices }
                                                ?.let { pieGroups[it].key }
                                        }

                                        override fun onNothingSelected() {
                                            selectedKey = null
                                        }
                                    },
                                )
                                if (chart.tag != dataSignature) {
                                    chart.tag = dataSignature
                                    val entries = pieGroups.map { group ->
                                        PieEntry(group.pieValue(metric).toFloat(), group.key)
                                    }
                                    val dataSet = PieDataSet(entries, "").apply {
                                        colors = sliceColors.map { it.toArgb() }
                                        sliceSpace = 1.2f
                                        selectionShift = 12f
                                        setDrawValues(true)
                                        valueTextSize = 9f
                                        valueTypeface = android.graphics.Typeface.DEFAULT_BOLD
                                        valueTextColor = AndroidColor.DKGRAY
                                    }
                                    chart.data = PieData(dataSet)
                                    chart.notifyDataSetChanged()
                                    chart.invalidate()
                                }
                                leaderRenderer?.labelConfig = PieLeaderLabelConfig(
                                    kinds = labelKinds,
                                    lineColors = sliceColors.map { it.toArgb() },
                                    percents = slicePercents.map { it.toFloat() },
                                    formatLabel = ::formatPieSliceLabel,
                                )
                                val idx = selectedKey?.let { key ->
                                    pieGroups.indexOfFirst { it.key == key }
                                }?.takeIf { it >= 0 }
                                if (idx != null && idx in pieGroups.indices) {
                                    chart.holeRadius = 18f
                                    chart.transparentCircleRadius = 22f
                                    chart.setExtraOffsets(12f, 10f, 12f, 10f)
                                    chart.highlightValue(idx.toFloat(), 0, false)
                                } else {
                                    chart.holeRadius = 2f
                                    chart.transparentCircleRadius = 2f
                                    chart.setExtraOffsets(12f, 10f, 12f, 10f)
                                    chart.highlightValues(null)
                                }
                                chart.invalidate()
                            },
                            modifier = Modifier.fillMaxSize(),
                        )

                        val selectedGroup = selectedKey
                            ?.let { key -> pieGroups.firstOrNull { it.key == key } }
                        if (selectedGroup != null) {
                            val selectedValue = selectedGroup.pieValue(metric)
                            val selectedPercent = selectedValue * 100.0 / total
                            Column(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f),
                                        shape = RoundedCornerShape(28.dp),
                                    )
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = selectedGroup.key,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = String.format("%.1f%%", selectedPercent),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    Text(
                        text = "点击扇区可在中间查看该项",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val legendOrder = selectedKey
                            ?.let { key ->
                                val selected = legendGroups.indexOfFirst { it.key == key }
                                if (selected < 0) {
                                    legendGroups.indices.toList()
                                } else {
                                    listOf(selected) + legendGroups.indices.filterNot { it == selected }
                                }
                            }
                            ?: legendGroups.indices.toList()
                        legendOrder.forEach { index ->
                            val group = legendGroups[index]
                            val value = group.pieValue(metric)
                            val percent = value * 100.0 / total
                            val selected = selectedKey == group.key
                            val sliceColor = colorByKey[group.key] ?: PieColors[index % PieColors.size]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (selected) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surface
                                        },
                                    )
                                    .border(
                                        width = if (selected) 2.dp else 1.dp,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                    )
                                    .clickable {
                                        selectedKey = if (selected) null else group.key
                                    }
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Spacer(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .height(28.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(sliceColor),
                                )
                                Canvas(modifier = Modifier.size(12.dp)) {
                                    drawCircle(color = sliceColor)
                                }
                                Text(
                                    text = String.format(
                                        "%s  %.1f%% · %s",
                                        group.key,
                                        percent,
                                        formatYuan(value),
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BarChartSection(groups: List<GroupMetrics>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "分组对比（预算 / 已付 / 预计）",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
            if (groups.isEmpty()) {
                Text(
                    text = "暂无数据",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val displayGroups = groups.take(8)
                AndroidView(
                    factory = { context ->
                        BarChart(context).apply {
                            description.isEnabled = false
                            legend.isEnabled = false
                            setDrawGridBackground(false)
                            setFitBars(true)
                            setNoDataText("暂无数据")
                            setScaleEnabled(false)
                            setPinchZoom(false)
                            axisRight.isEnabled = false
                            axisLeft.apply {
                                axisMinimum = 0f
                                setDrawGridLines(true)
                                textColor = AndroidColor.GRAY
                                granularity = 1f
                            }
                            xAxis.apply {
                                position = XAxis.XAxisPosition.BOTTOM
                                setDrawGridLines(false)
                                granularity = 1f
                                textColor = AndroidColor.GRAY
                                labelRotationAngle = -25f
                            }
                        }
                    },
                    update = { chart ->
                        val budgetEntries = ArrayList<BarEntry>()
                        val paidEntries = ArrayList<BarEntry>()
                        val projectedEntries = ArrayList<BarEntry>()
                        displayGroups.forEachIndexed { index, group ->
                            val x = index.toFloat()
                            // 以「元」展示，避免分值过大导致柱图刻度难看
                            budgetEntries.add(BarEntry(x, group.budget / 100f))
                            paidEntries.add(BarEntry(x, group.paid / 100f))
                            projectedEntries.add(BarEntry(x, group.projected / 100f))
                        }
                        val budgetSet = BarDataSet(budgetEntries, "预算").apply {
                            color = BarColors[0].toArgb()
                            setDrawValues(false)
                        }
                        val paidSet = BarDataSet(paidEntries, "已付").apply {
                            color = BarColors[1].toArgb()
                            setDrawValues(false)
                        }
                        val projectedSet = BarDataSet(projectedEntries, "预计").apply {
                            color = BarColors[2].toArgb()
                            setDrawValues(false)
                        }
                        val groupCount = displayGroups.size
                        val barWidth = 0.2f
                        val barSpace = 0.04f
                        val groupSpace = 0.28f
                        val data = BarData(budgetSet, paidSet, projectedSet).apply {
                            this.barWidth = barWidth
                        }
                        chart.data = data
                        chart.xAxis.apply {
                            valueFormatter = IndexAxisValueFormatter(displayGroups.map { it.key })
                            axisMinimum = 0f
                            axisMaximum = groupCount.toFloat()
                            setCenterAxisLabels(true)
                            labelCount = groupCount
                        }
                        // groupSpace * n + barSpace * (bars-1) * n + barWidth * bars * n = n
                        chart.groupBars(0f, groupSpace, barSpace)
                        chart.invalidate()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("预算" to BarColors[0], "已付" to BarColors[1], "预计" to BarColors[2]).forEach { (label, color) ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Canvas(modifier = Modifier.size(10.dp)) {
                                drawRect(color = color)
                            }
                            Text(text = label, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupMetricsList(
    groups: List<GroupMetrics>,
    healthColorEnabled: Boolean,
    viewModel: StatsViewModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "分组明细",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
        )
        if (groups.isEmpty()) {
            Text(
                text = "暂无分组数据",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            groups.forEach { group ->
                val overspend = group.overspend()
                val health = viewModel.overspendHealth(overspend.coerceAtLeast(0L), group.budget)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = group.key,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "预算 ${formatYuan(group.budget)} · 已付 ${formatYuan(group.paid)} · 预计 ${formatYuan(group.projected)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = overspendLabel(overspend),
                            style = MaterialTheme.typography.bodySmall,
                            color = healthColor(health, healthColorEnabled),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopContractGapSection(
    rows: List<ContractGapRow>,
    healthColorEnabled: Boolean,
    viewModel: StatsViewModel,
    onOpenItem: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "合同超预算 TOP5",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
        )
        if (rows.isEmpty()) {
            Text(
                text = "暂无已签合同项",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            rows.forEach { row ->
                val health = viewModel.overspendHealth(row.gap.coerceAtLeast(0L), row.budget)
                Card(
                    onClick = { onOpenItem(row.itemId) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = row.itemName,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "预算 ${formatYuan(row.budget)} → 合同 ${formatYuan(row.contract)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = if (row.gap >= 0L) "+${formatYuan(row.gap)}" else formatYuan(row.gap),
                            style = MaterialTheme.typography.bodyMedium,
                            color = healthColor(health, healthColorEnabled),
                        )
                    }
                }
            }
        }
    }
}

private fun overspendLabel(overspend: Long): String = when {
    overspend > 0L -> "超支 ${formatYuan(overspend)}"
    overspend < 0L -> "节余 ${formatYuan(-overspend)}"
    else -> "与预算持平"
}
