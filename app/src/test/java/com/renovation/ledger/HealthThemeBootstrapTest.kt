package com.renovation.ledger

import com.renovation.ledger.domain.model.HealthLevel
import com.renovation.ledger.ui.theme.HealthThemeBootstrap
import org.junit.Assert.assertEquals
import org.junit.Test

class HealthThemeBootstrapTest {
    @Test
    fun parseLevel_defaultsWithin_andRoundTrips() {
        assertEquals(HealthLevel.WITHIN, HealthThemeBootstrap.parseLevel(null))
        assertEquals(HealthLevel.WITHIN, HealthThemeBootstrap.parseLevel(""))
        assertEquals(HealthLevel.WITHIN, HealthThemeBootstrap.parseLevel("nope"))
        assertEquals(HealthLevel.MILD_OVER, HealthThemeBootstrap.parseLevel("MILD_OVER"))
        assertEquals(HealthLevel.SEVERE_OVER, HealthThemeBootstrap.parseLevel("SEVERE_OVER"))
        assertEquals(
            HealthLevel.SEVERE_OVER,
            HealthThemeBootstrap.parseLevel(HealthThemeBootstrap.serializeLevel(HealthLevel.SEVERE_OVER)),
        )
    }

    @Test
    fun pageBackgroundArgb_severeIsPinkNotGreen() {
        // SEVERE page bg (#FFF0F0) — cold start must not flash Within green (#E8F5E9)
        assertEquals(0xFFFFF0F0.toInt(), HealthThemeBootstrap.pageBackgroundArgb(HealthLevel.SEVERE_OVER, true))
        assertEquals(0xFFE8F5E9.toInt(), HealthThemeBootstrap.pageBackgroundArgb(HealthLevel.WITHIN, true))
        assertEquals(0xFFFFF3E0.toInt(), HealthThemeBootstrap.pageBackgroundArgb(HealthLevel.MILD_OVER, true))
        assertEquals(0xFFF6FBF4.toInt(), HealthThemeBootstrap.pageBackgroundArgb(HealthLevel.SEVERE_OVER, false))
    }
}
