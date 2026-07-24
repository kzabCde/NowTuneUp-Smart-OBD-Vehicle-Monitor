package com.nowtuneup.app.presentation
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.nowtuneup.app.GaugeCard
import com.nowtuneup.app.domain.model.VehicleReading
import com.nowtuneup.app.presentation.theme.NtuTheme
import org.junit.Rule
import org.junit.Test
class DashboardUiTest {@get:Rule val compose=createComposeRule();@Test fun valueRenders(){compose.setContent{NtuTheme{GaugeCard(VehicleReading(0x0C,"Engine RPM",1726.0,"rpm"))}};compose.onNodeWithText("1726.0").assertIsDisplayed();compose.onNodeWithText("Engine RPM").assertIsDisplayed()}@Test fun unsupportedRenders(){compose.setContent{NtuTheme{GaugeCard(VehicleReading(1,"Example",null,"",false))}};compose.onNodeWithText("Not supported").assertIsDisplayed()}}
