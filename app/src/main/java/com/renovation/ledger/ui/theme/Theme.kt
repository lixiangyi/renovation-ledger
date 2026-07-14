package com.renovation.ledger.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.renovation.ledger.domain.model.HealthLevel
import com.renovation.ledger.ui.common.HealthGreen
import com.renovation.ledger.ui.common.HealthGreenBg
import com.renovation.ledger.ui.common.HealthGreenContainer
import com.renovation.ledger.ui.common.HealthOrange
import com.renovation.ledger.ui.common.HealthOrangeBg
import com.renovation.ledger.ui.common.HealthOrangeContainer
import com.renovation.ledger.ui.common.HealthRed
import com.renovation.ledger.ui.common.HealthRedBg
import com.renovation.ledger.ui.common.HealthRedContainer

private fun baseScheme(
    primary: Color,
    onPrimary: Color = Color.White,
    primaryContainer: Color,
    onPrimaryContainer: Color,
    background: Color,
    surface: Color,
    surfaceContainerLowest: Color,
    surfaceContainerLow: Color,
    surfaceContainer: Color,
    surfaceContainerHigh: Color,
    surfaceContainerHighest: Color,
    secondaryContainer: Color,
    tertiaryContainer: Color,
): ColorScheme = lightColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    secondary = Color(0xFF4F6354),
    onSecondary = Color.White,
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = Color(0xFF0C1F14),
    tertiary = Color(0xFF3B6471),
    onTertiary = Color.White,
    tertiaryContainer = tertiaryContainer,
    onTertiaryContainer = Color(0xFF001F27),
    background = background,
    onBackground = Color(0xFF171D19),
    surface = surface,
    onSurface = Color(0xFF171D19),
    surfaceVariant = surfaceContainerHigh,
    onSurfaceVariant = Color(0xFF404942),
    surfaceContainerLowest = surfaceContainerLowest,
    surfaceContainerLow = surfaceContainerLow,
    surfaceContainer = surfaceContainer,
    surfaceContainerHigh = surfaceContainerHigh,
    surfaceContainerHighest = surfaceContainerHighest,
    outline = Color(0xFF707972),
    outlineVariant = Color(0xFFC0C9C0),
)

private val NeutralScheme = baseScheme(
    primary = Color(0xFF2E6B4F),
    primaryContainer = Color(0xFFB8F1D0),
    onPrimaryContainer = Color(0xFF002113),
    background = Color(0xFFF6FBF4),
    surface = Color(0xFFF6FBF4),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F5EE),
    surfaceContainer = Color(0xFFEAEFE8),
    surfaceContainerHigh = Color(0xFFE4EAE2),
    surfaceContainerHighest = Color(0xFFDEE4DC),
    secondaryContainer = Color(0xFFD1E8D5),
    tertiaryContainer = Color(0xFFBFE9F4),
)

private val WithinScheme = baseScheme(
    primary = HealthGreen,
    primaryContainer = HealthGreenContainer,
    onPrimaryContainer = Color(0xFF003910),
    background = HealthGreenBg,
    surface = Color(0xFFF1F8F1),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFE4F0E5),
    surfaceContainer = Color(0xFFDCECDC),
    surfaceContainerHigh = Color(0xFFD2E6D3),
    surfaceContainerHighest = HealthGreenContainer,
    secondaryContainer = Color(0xFFC8E6C9),
    tertiaryContainer = Color(0xFFB2DFDB),
)

private val MildScheme = baseScheme(
    primary = HealthOrange,
    primaryContainer = HealthOrangeContainer,
    onPrimaryContainer = Color(0xFF3E2000),
    background = HealthOrangeBg,
    surface = Color(0xFFFFF8F1),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF0DE),
    surfaceContainer = Color(0xFFFFE8CC),
    surfaceContainerHigh = Color(0xFFFFE0B8),
    surfaceContainerHighest = HealthOrangeContainer,
    secondaryContainer = Color(0xFFFFE0B2),
    tertiaryContainer = Color(0xFFFFECB3),
)

private val SevereScheme = baseScheme(
    primary = HealthRed,
    primaryContainer = HealthRedContainer,
    onPrimaryContainer = Color(0xFF3B0005),
    background = HealthRedBg,
    surface = Color(0xFFFFF5F5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF0F0),
    surfaceContainer = Color(0xFFFFE4E4),
    surfaceContainerHigh = Color(0xFFFFD6D6),
    surfaceContainerHighest = HealthRedContainer,
    secondaryContainer = Color(0xFFFFC9C9),
    tertiaryContainer = Color(0xFFFFCCBC),
)

fun healthColorScheme(level: HealthLevel, enabled: Boolean): ColorScheme {
    if (!enabled) return NeutralScheme
    return when (level) {
        HealthLevel.WITHIN -> WithinScheme
        HealthLevel.MILD_OVER -> MildScheme
        HealthLevel.SEVERE_OVER -> SevereScheme
    }
}

@Composable
fun RenovationLedgerTheme(
    healthLevel: HealthLevel = HealthLevel.WITHIN,
    healthColorEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val scheme = remember(healthLevel, healthColorEnabled) {
        healthColorScheme(healthLevel, healthColorEnabled)
    }
    MaterialTheme(
        colorScheme = scheme,
        content = content,
    )
}
