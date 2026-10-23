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
import com.nowtuneup.app.data.dashboard.DashboardDefaults
import com.nowtuneup.app.domain.model.DashboardConfig
import com.nowtuneup.app.presentation.theme.DaylightTheme
import com.nowtuneup.app.presentation.theme.GraphiteTheme
import com.nowtuneup.app.presentation.theme.NtuTheme
import com.nowtuneup.app.ui.components.NtuEmptyState
import com.nowtuneup.app.ui.components.NtuPanel
import com.nowtuneup.app.ui.components.NtuScreenHeader
import com.nowtuneup.app.ui.dashboard.editor.DashboardEditor
import androidx.compose.material3.Text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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


    @Test fun customHudStudioAddsAndSavesAFreeformWidget() {
        var saved: DashboardConfig? = null
        val empty = DashboardDefaults.newProfile(id = "ui-custom-hud", name = "Custom HUD Test")

        compose.setContent {
            NtuTheme(GraphiteTheme, reduceMotion = true) {
                DashboardEditor(
                    config = empty,
                    drivingMode = false,
                    onSave = { saved = it },
                    onCancel = {},
                )
            }
        }

        compose.onNodeWithText("Custom HUD Studio").assertIsDisplayed()
        compose.onNodeWithText("+ เพิ่ม Widget").performClick()
        compose.onNodeWithText("Widget Library").assertIsDisplayed()
        compose.onNodeWithText("รอบเครื่อง").performClick()
        compose.onNodeWithText("Widget Properties").assertIsDisplayed()
        compose.onNodeWithText("ใช้การตั้งค่า").performScrollTo().performClick()
        compose.onNodeWithText("บันทึก").performClick()
        compose.waitForIdle()

        val result = checkNotNull(saved)
        val widget = result.portrait.widgets.single()
        assertEquals(0x0C, widget.pid)
        assertTrue(widget.canvasWidth > 0)
        assertTrue(widget.canvasHeight > 0)
        assertTrue(widget.canvasX + widget.canvasWidth <= result.portrait.columns)
        assertTrue(widget.canvasY + widget.canvasHeight <= result.portrait.rows)
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
