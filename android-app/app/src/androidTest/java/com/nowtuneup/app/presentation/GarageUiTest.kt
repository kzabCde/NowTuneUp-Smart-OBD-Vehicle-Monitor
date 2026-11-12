package com.nowtuneup.app.presentation

import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.nowtuneup.app.data.obd.session.AdapterHealthState
import com.nowtuneup.app.domain.model.ConnectionUiState
import com.nowtuneup.app.presentation.theme.DaylightTheme
import com.nowtuneup.app.presentation.theme.GraphiteTheme
import com.nowtuneup.app.presentation.theme.NtuTheme
import com.nowtuneup.app.ui.connection.ConnectionHealthPanel
import com.nowtuneup.app.ui.vehicle.GarageContent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class GarageUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun compactLargeTextGarageRetainsVehicleActionAndHonestEmptyHealth() {
        var managed = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                NtuTheme(GraphiteTheme, reduceMotion = true) {
                    Surface(Modifier.width(320.dp)) {
                        GarageContent(null, 0, ConnectionUiState(), AdapterHealthState(), { managed++ }, {}, {}, {})
                    }
                }
            }
        }
        compose.onNodeWithText("จัดการโปรไฟล์รถ").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(1, managed)
        // Lazy items outside the viewport are not composed until the list scrolls to them.
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("ยังไม่มีข้อมูลคำสั่งจากอะแดปเตอร์"))
        compose.onNodeWithText("ยังไม่มีข้อมูลคำสั่งจากอะแดปเตอร์").assertIsDisplayed()
        compose.onNodeWithText("100.0 %").assertDoesNotExist()
    }

    @Test fun disconnectedHealthKeepsMeasuredMetricsExplicitlyHistoricalInLightTheme() {
        compose.setContent {
            NtuTheme(DaylightTheme, reduceMotion = true) {
                Surface {
                    ConnectionHealthPanel(AdapterHealthState(totalCommands = 10, updatedAtMillis = 1,
                        successRate = 0.9, averageLatencyMillis = 120, commandsPerSecond = 2.0), connected = false)
                }
            }
        }
        compose.onNodeWithText("ข้อมูลที่วัดได้ล่าสุด · ขณะนี้ไม่ได้เชื่อมต่อ").assertIsDisplayed()
        compose.onNodeWithText("90.0 %").assertIsDisplayed()
        compose.onNodeWithText("2.0 คำสั่ง/s").assertIsDisplayed()
    }
}
