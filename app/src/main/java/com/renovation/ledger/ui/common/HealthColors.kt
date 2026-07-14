package com.renovation.ledger.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.renovation.ledger.domain.model.HealthLevel

val HealthGreen = Color(0xFF2E7D32)
val HealthOrange = Color(0xFFEF6C00)
val HealthRed = Color(0xFFC62828)

val HealthGreenContainer = Color(0xFFC8E6C9)
val HealthOrangeContainer = Color(0xFFFFE0B2)
val HealthRedContainer = Color(0xFFFFCDD2)

val HealthGreenBg = Color(0xFFE8F5E9)
val HealthOrangeBg = Color(0xFFFFF3E0)
val HealthRedBg = Color(0xFFFFEBEE)

@Composable
fun healthColor(level: HealthLevel, enabled: Boolean): Color {
    if (!enabled) return MaterialTheme.colorScheme.onSurface
    return when (level) {
        HealthLevel.WITHIN -> HealthGreen
        HealthLevel.MILD_OVER -> HealthOrange
        HealthLevel.SEVERE_OVER -> HealthRed
    }
}

/**
 * 超支 / 节余文案色：不受「预算健康色」开关影响。
 * 默认主题下也要能一眼看出超支金额。
 */
@Composable
fun overspendHintColor(overspend: Long, level: HealthLevel): Color {
    return when {
        overspend < 0L -> HealthGreen
        overspend == 0L -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> when (level) {
            HealthLevel.MILD_OVER -> HealthOrange
            HealthLevel.SEVERE_OVER -> HealthRed
            HealthLevel.WITHIN -> HealthGreen
        }
    }
}

/** 进度百分比文案：超过 100% 时用健康档着色（同样不受主题开关影响）。 */
@Composable
fun progressPercentColor(percent: Int, level: HealthLevel): Color {
    if (percent <= 100) return MaterialTheme.colorScheme.onSurface
    return when (level) {
        HealthLevel.WITHIN -> HealthGreen
        HealthLevel.MILD_OVER -> HealthOrange
        HealthLevel.SEVERE_OVER -> HealthRed
    }
}

@Composable
fun healthContainerColor(level: HealthLevel, enabled: Boolean): Color {
    if (!enabled) return MaterialTheme.colorScheme.surfaceContainerHighest
    return when (level) {
        HealthLevel.WITHIN -> HealthGreenContainer
        HealthLevel.MILD_OVER -> HealthOrangeContainer
        HealthLevel.SEVERE_OVER -> HealthRedContainer
    }
}
