package com.nowtuneup.app.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import org.junit.Assert.assertFalse
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
        compose.onNodeWithTag("hud-editor-list").performScrollToNode(hasText("+ เพิ่ม Widget"))
        compose.onNodeWithText("+ เพิ่ม Widget").performClick()
        compose.onNodeWithText("Widget Library").assertIsDisplayed()
        compose.onNodeWithText("รอบเครื่อง").performScrollTo().performClick()
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

    @Test fun disconnectedCachedReadingIsUnavailableInTheInstrumentCluster() {
        val widget = DashboardDefaults.widgetCatalog.first { it.pid == 0x0D }
        compose.setContent {
            NtuTheme(GraphiteTheme, reduceMotion = true) {
                com.nowtuneup.app.ui.dashboard.components.DrivingDashboardWidget(
                    config = widget,
                    reading = com.nowtuneup.app.domain.model.VehicleReading(0x0D, "Speed", 123.4, "km/h"),
                    stats = null,
                    freshness = com.nowtuneup.app.domain.model.DataFreshness.NO_DATA,
                    reduceMotion = true, dtcCount = null, showPeakHold = false, showMinMax = false,
                )
            }
        }
        compose.onNodeWithText("123.4").assertDoesNotExist()
        compose.onNodeWithText("Unavailable").assertIsDisplayed()
    }

    @Test fun compactDialAndDigitalGaugesKeepLabelsWithoutShowingCachedValues() {
        val speed = DashboardDefaults.widgetCatalog.first { it.pid == 0x0D }.copy(title = "Speed", decimals = 0)
        val rpm = DashboardDefaults.widgetCatalog.first { it.pid == 0x0C }.copy(title = "RPM", decimals = 0)
        compose.setContent {
            NtuTheme(GraphiteTheme, reduceMotion = true) {
                Surface {
                    Column(Modifier.width(150.dp)) {
                        listOf(speed to 12345.0, rpm to 6789.0).forEach { (config, cached) ->
                            Box(Modifier.height(72.dp)) {
                                com.nowtuneup.app.ui.dashboard.components.DrivingDashboardWidget(
                                    config, com.nowtuneup.app.domain.model.VehicleReading(config.pid, config.title, cached, ""),
                                    stats = null, freshness = com.nowtuneup.app.domain.model.DataFreshness.NO_DATA,
                                    reduceMotion = true, dtcCount = null, showPeakHold = false, showMinMax = false,
                                )
                            }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Speed").assertIsDisplayed()
        compose.onNodeWithText("RPM").assertIsDisplayed()
        compose.onNodeWithText("12345").assertDoesNotExist()
        compose.onNodeWithText("6789").assertDoesNotExist()
    }

    @Test fun fullscreenConnectionStatusDoesNotCoverTheFirstGaugeLabel() {
        val speed = DashboardDefaults.widgetCatalog.first { it.pid == 0x0D }
            .copy(title = "Speed", canvasX = 0, canvasY = 0, canvasWidth = 3, canvasHeight = 24)
        val profile = DashboardDefaults.newProfile().copy(
            portrait = com.nowtuneup.app.domain.model.DashboardLayout(widgets = listOf(speed)))
        compose.setContent {
            NtuTheme(GraphiteTheme, reduceMotion = true) {
                Surface(Modifier.fillMaxSize()) {
                    com.nowtuneup.app.ui.dashboard.DashboardScreen(
                        config = profile, readings = emptyList(), readingStats = emptyMap(),
                        preferences = com.nowtuneup.app.domain.model.DashboardPreferences(focusMode = true, reduceMotion = true),
                        connectionState = com.nowtuneup.app.domain.model.ConnectionState.DISCONNECTED,
                        dtcCount = null, activeAlerts = emptyList(), pageIndex = 0, pageCount = 2,
                        controlsVisible = false, onConnectionAction = {}, onEdit = {}, onEnterFocus = {},
                        onExitFocus = {}, onExitHud = {}, onToggleHudMirror = {}, onToggleControls = {},
                        onToggleTouchLock = {}, onResetStats = {},
                    )
                }
            }
        }
        val status = compose.onNodeWithText("ยังไม่เชื่อมต่อ").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val label = compose.onNodeWithText("Speed").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertFalse("Fullscreen status must not cover the configured gauge label", status.overlaps(label))
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
