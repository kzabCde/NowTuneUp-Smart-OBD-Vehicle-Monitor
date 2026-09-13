package com.nowtuneup.app.presentation.theme

import com.nowtuneup.app.util.Contrast
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeContrastTest {
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
