package com.renovation.ledger.ui.stats

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.github.mikephil.charting.animation.ChartAnimator
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.renderer.PieChartRenderer
import com.github.mikephil.charting.utils.Utils
import com.github.mikephil.charting.utils.ViewPortHandler
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

internal data class PieLeaderLabelConfig(
    val kinds: List<PieLabelKind>,
    val lineColors: List<Int>,
    val percents: List<Float>,
    val formatLabel: (PieLabelKind, String, Float) -> String,
)

/**
 * 饼图外侧引线：第一段必须沿圆心径向射出，再接一小段横线到文字。
 * 重叠时只拉长径向长度，不把折点纵向挪开。
 */
internal class LeaderLinePieChartRenderer(
    chart: PieChart,
    animator: ChartAnimator,
    viewPortHandler: ViewPortHandler,
) : PieChartRenderer(chart, animator, viewPortHandler) {

    var labelConfig: PieLeaderLabelConfig? = null

    private class Candidate(
        val label: String,
        val onRight: Boolean,
        val color: Int,
        val cosA: Float,
        val sinA: Float,
        val sliceRadius: Float,
        val textWidth: Float,
        var radialLen: Float,
    )

    override fun drawValues(c: Canvas) {
        val config = labelConfig ?: return
        val pieChart = mChart ?: return
        val data = pieChart.data ?: return
        if (data.dataSetCount <= 0) return
        val dataSet = data.getDataSetByIndex(0) as? PieDataSet ?: return
        if (!dataSet.isDrawValuesEnabled) return

        val drawAngles = pieChart.drawAngles ?: return
        val absoluteAngles = pieChart.absoluteAngles ?: return
        val center = pieChart.centerCircleBox ?: return
        val radius = pieChart.radius
        if (radius <= 0f) return

        val rotationAngle = pieChart.rotationAngle
        val phaseX = mAnimator.phaseX
        val phaseY = mAnimator.phaseY
        if (phaseY == 0f) return

        val sliceSpace = dataSet.sliceSpace
        val baseRadial = Utils.convertDpToPixel(10f)
        val radialStep = Utils.convertDpToPixel(10f)
        val maxRadial = Utils.convertDpToPixel(28f)
        val maxHorizontal = Utils.convertDpToPixel(8f)
        val textSizePx = Utils.convertDpToPixel(9f)
        val lineWidthPx = Utils.convertDpToPixel(1f)
        val textGapPx = Utils.convertDpToPixel(3f)
        val edgePad = Utils.convertDpToPixel(4f)
        val chartW = pieChart.width.toFloat()
        val chartH = pieChart.height.toFloat()

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = lineWidthPx
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = textSizePx
            typeface = Typeface.DEFAULT_BOLD
            color = dataSet.valueTextColor
        }
        val metrics = textPaint.fontMetrics
        // 给小比例更足的纵向“留白”，防止外侧引线文字挤在一块
        val minGap = (metrics.descent - metrics.ascent) + Utils.convertDpToPixel(4f)

        val candidates = mutableListOf<Candidate>()
        val entryCount = minOf(dataSet.entryCount, drawAngles.size, absoluteAngles.size)
        for (i in 0 until entryCount) {
            val kind = config.kinds.getOrElse(i) { PieLabelKind.NAME_PCT }
            val entry = dataSet.getEntryForIndex(i)
            val key = entry.label.orEmpty()
            val percent = config.percents.getOrElse(i) { entry.y }
            val label = config.formatLabel(kind, key, percent).ifEmpty {
                config.formatLabel(PieLabelKind.NAME_PCT, key, percent)
            }
            if (label.isEmpty()) continue

            var angle = if (i == 0) 0f else absoluteAngles[i - 1] * phaseX
            val sliceAngle = drawAngles[i]
            val sliceSpaceMiddleAngle = sliceSpace / (Utils.FDEG2RAD * radius)
            val angleOffset = (sliceAngle - sliceSpaceMiddleAngle / 2f) / 2f
            angle += angleOffset
            val transformedAngle = rotationAngle + angle * phaseY
            val rad = Math.toRadians(transformedAngle.toDouble())
            val cosA = cos(rad).toFloat()
            val sinA = sin(rad).toFloat()
            val highlightShift = if (pieChart.needsHighlight(i)) dataSet.selectionShift else 0f

            candidates += Candidate(
                label = label,
                onRight = cosA >= 0f,
                color = dataSet.getColor(i),
                cosA = cosA,
                sinA = sinA,
                sliceRadius = radius + highlightShift,
                textWidth = textPaint.measureText(label),
                radialLen = baseRadial,
            )
        }

        resolveRadialLengths(candidates.filter { it.onRight }, center.y, minGap, baseRadial, radialStep, maxRadial)
        resolveRadialLengths(candidates.filter { !it.onRight }, center.y, minGap, baseRadial, radialStep, maxRadial)

        for (item in candidates) {
            val elbowR = item.sliceRadius + item.radialLen
            val xSlice = center.x + item.sliceRadius * item.cosA
            val ySlice = center.y + item.sliceRadius * item.sinA
            val xElbow = center.x + elbowR * item.cosA
            val yElbow = center.y + elbowR * item.sinA
            val remaining = if (item.onRight) {
                chartW - edgePad - item.textWidth - textGapPx - xElbow
            } else {
                xElbow - edgePad - item.textWidth - textGapPx
            }
            val horizontal = remaining.coerceIn(0f, maxHorizontal)
            val xLineEnd = if (item.onRight) xElbow + horizontal else xElbow - horizontal
            val textX = if (item.onRight) {
                (xLineEnd + textGapPx).coerceAtMost(chartW - edgePad - item.textWidth).coerceAtLeast(edgePad)
            } else {
                (xLineEnd - textGapPx).coerceAtLeast(edgePad + item.textWidth).coerceAtMost(chartW - edgePad)
            }
            val textY = yElbow - (metrics.ascent + metrics.descent) / 2f

            linePaint.color = item.color
            c.drawLine(xSlice, ySlice, xElbow, yElbow, linePaint)
            c.drawLine(xElbow, yElbow, xLineEnd, yElbow, linePaint)
            textPaint.textAlign = if (item.onRight) Paint.Align.LEFT else Paint.Align.RIGHT
            c.drawText(item.label, textX, textY, textPaint)
        }
    }

    private fun resolveRadialLengths(
        items: List<Candidate>,
        centerY: Float,
        minGap: Float,
        baseRadial: Float,
        radialStep: Float,
        maxRadial: Float,
    ) {
        if (items.size < 2) return
        val ordered = items.sortedBy { centerY + (it.sliceRadius + it.radialLen) * it.sinA }
        for (i in 1 until ordered.size) {
            val prev = ordered[i - 1]
            val cur = ordered[i]
            var tries = 0
            while (tries < 6) {
                val yPrev = centerY + (prev.sliceRadius + prev.radialLen) * prev.sinA
                val yCur = centerY + (cur.sliceRadius + cur.radialLen) * cur.sinA
                if (abs(yCur - yPrev) >= minGap) break
                if (cur.radialLen >= maxRadial) break
                cur.radialLen = (cur.radialLen + radialStep).coerceAtMost(maxRadial)
                tries++
            }
            if (tries == 0 && i % 2 == 1) {
                cur.radialLen = (baseRadial + radialStep).coerceAtMost(maxRadial)
            }
        }
    }
}
