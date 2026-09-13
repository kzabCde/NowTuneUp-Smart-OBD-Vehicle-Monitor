package com.nowtuneup.app.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.nowtuneup.app.presentation.theme.DaylightTheme
import com.nowtuneup.app.presentation.theme.GraphiteTheme
import com.nowtuneup.app.presentation.theme.NtuTheme
import com.nowtuneup.app.ui.components.NtuEmptyState
import com.nowtuneup.app.ui.components.NtuPanel
import com.nowtuneup.app.ui.components.NtuScreenHeader
import androidx.compose.material3.Text
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DashboardUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun firstRunActionIsExplicitAndOnlyRunsWhenTapped() {
        var creations = 0
        compose.setContent {
            NtuTheme(GraphiteTheme, reduceMotion = true) {
                Surface(Modifier.fillMaxSize()) {
                    Box(contentAlignment = Alignment.Center) {
                        NtuEmptyState("รถของฉัน", "เพิ่มรถเพื่อเริ่มต้น", Icons.Default.DirectionsCar,
                            "เพิ่มรถคันแรก", { creations++ })
                    }
                }
            }
        }
        assertEquals(0, creations)
        compose.onNodeWithText("เพิ่มรถคันแรก").assertIsDisplayed().performClick()
        assertEquals(1, creations)
    }

    @Test fun lightThemeHeaderRetainsReadableContent() {
        compose.setContent {
            NtuTheme(DaylightTheme) { NtuScreenHeader("ข้อมูลสด", "เชื่อมต่อรถเพื่อเริ่มต้น") }
        }
        compose.onNodeWithText("ข้อมูลสด").assertIsDisplayed()
        compose.onNodeWithText("เชื่อมต่อรถเพื่อเริ่มต้น").assertIsDisplayed()
    }

    @Test fun panelRetainsClickSemanticsWithReducedMotion() {
        var clicks = 0
        compose.setContent {
            NtuTheme(reduceMotion = true) {
                NtuPanel(onClick = { clicks++ }) { Text("เลือกเกจ") }
            }
        }
        compose.onNodeWithText("เลือกเกจ").assertHasClickAction().performClick()
        assertEquals(1, clicks)
    }
}
