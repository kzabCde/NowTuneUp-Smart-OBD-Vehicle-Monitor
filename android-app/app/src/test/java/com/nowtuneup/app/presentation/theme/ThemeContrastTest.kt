package com.nowtuneup.app.presentation.theme

import com.nowtuneup.app.util.Contrast
import org.junit.Assert.assertTrue
import org.junit.Assert.assertSame
import org.junit.Assert.assertEquals
import com.nowtuneup.app.domain.model.ColorConfig
import com.nowtuneup.app.domain.model.ThemeConfig
import org.junit.Test

class ThemeContrastTest {
    @Test fun gaugeAlertsKeepReadableContrastOnLightAndDarkCards() {
        val colors = ColorConfig()
        listOf(GraphiteTheme, DaylightTheme).forEach { theme ->
            assertTrue(Contrast.isReadable(Contrast.readableColor(colors.warning, theme.card, theme.warning), theme.card))
            assertTrue(Contrast.isReadable(Contrast.readableColor(colors.critical, theme.card, theme.critical), theme.card))
        }
        val custom = 0xFF00FFFFL
        assertEquals(custom, Contrast.readableColor(custom, GraphiteTheme.card, GraphiteTheme.warning))
    }

    @Test fun legacyStockColorsUpgradeWithoutReplacingCustomizedPalettes() {
        val oldStock = ThemeConfig(name = "Graphite", primary = 0xFF52FF8A,
            secondary = 0xFFFFC66D, accent = 0xFF52FF8A, background = 0xFF080B0E,
            card = 0xFF12191E, text = 0xFFF1F6F4, gaugeNeedle = 0xFF52FF8A,
            border = 0xFF334139)
        assertSame(GraphiteTheme, effectiveAppTheme(oldStock))
        val custom = oldStock.copy(background = 0xFF122334)
        assertSame(custom, effectiveAppTheme(custom))
        assertSame(DaylightTheme, effectiveAppTheme(DaylightTheme))
    }

    @Test fun shippedPalettesKeepTextAndActionLabelsReadable() {
        listOf(GraphiteTheme, DaylightTheme).forEach { theme ->
            assertTrue("${theme.name} body", Contrast.isReadable(theme.text, theme.background))
            assertTrue("${theme.name} card", Contrast.isReadable(theme.text, theme.card))
            assertTrue("${theme.name} accent", Contrast.isReadable(theme.primary, theme.card))
            val black = Contrast.ratio(0xFF000000, theme.primary)
            val white = Contrast.ratio(0xFFFFFFFF, theme.primary)
            assertTrue("${theme.name} button", maxOf(black, white) >= 4.5)
        }
    }
}
