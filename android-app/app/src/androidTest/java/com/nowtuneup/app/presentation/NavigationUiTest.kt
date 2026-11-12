package com.nowtuneup.app.presentation

import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.nowtuneup.app.presentation.theme.*
import com.nowtuneup.app.ui.navigation.*
import org.junit.Rule
import org.junit.Test

class NavigationUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun allFiveDestinationsRemainReachableAt320dpAndLargeType() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                NtuTheme(DaylightTheme, reduceMotion = true) {
                    var selected by remember { mutableStateOf(AppDestination.HOME) }
                    Surface(Modifier.width(320.dp)) { AppBottomNavigation(selected, { selected = it }) }
                }
            }
        }
        listOf("Home", "Performance", "History", "Garage", "Settings").forEach {
            compose.onNodeWithContentDescription(it).assertIsDisplayed().performClick().assertIsSelected()
        }
    }
    @Test fun activeMeasurementCannotBeNavigatedAwayThroughPrimaryChrome() {
        compose.setContent { NtuTheme(GraphiteTheme, reduceMotion = true) {
            AppBottomNavigation(AppDestination.PERFORMANCE, {}, enabled = false)
        } }
        compose.onNodeWithContentDescription("Home").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Performance").assertIsSelected()
    }
}
