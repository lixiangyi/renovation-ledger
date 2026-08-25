package com.renovation.ledger.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.renovation.ledger.domain.model.HealthLevel
import com.renovation.ledger.ui.common.HealthGreenBg
import com.renovation.ledger.ui.common.HealthOrangeBg
import com.renovation.ledger.ui.common.HealthRedBg

/**
 * Cold-start theme bootstrap: last session's health theme so the first frame
 * does not flash the default green (WITHIN) before Room metrics arrive.
 */
object HealthThemeBootstrap {
    private val NeutralBg = Color(0xFFF6FBF4)

    fun parseLevel(raw: String?): HealthLevel = when (raw) {
        HealthLevel.MILD_OVER.name -> HealthLevel.MILD_OVER
        HealthLevel.SEVERE_OVER.name -> HealthLevel.SEVERE_OVER
        HealthLevel.WITHIN.name -> HealthLevel.WITHIN
        else -> HealthLevel.WITHIN
    }

    fun serializeLevel(level: HealthLevel): String = level.name

    fun pageBackground(level: HealthLevel, enabled: Boolean): Color {
        if (!enabled) return NeutralBg
        return when (level) {
            HealthLevel.WITHIN -> HealthGreenBg
            HealthLevel.MILD_OVER -> HealthOrangeBg
            HealthLevel.SEVERE_OVER -> HealthRedBg
        }
    }

    fun pageBackgroundArgb(level: HealthLevel, enabled: Boolean): Int =
        pageBackground(level, enabled).toArgb()
}
