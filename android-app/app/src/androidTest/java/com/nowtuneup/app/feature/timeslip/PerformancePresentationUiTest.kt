package com.nowtuneup.app.feature.timeslip

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.nowtuneup.app.presentation.theme.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PerformancePresentationUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun legacyResultDoesNotInventElapsedTimeOrARecordingDate() {
        compose.setContent {
            NtuTheme(GraphiteTheme, reduceMotion = true) {
                Surface {
                    Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                        ResultCard(TimeSlipRecord(rawSamples = null), isPersonalBest = false, onShare = {})
                    }
                }
            }
        }
        compose.onNodeWithText("Elapsed unavailable").assertIsDisplayed()
        compose.onNodeWithText("0.000 s").assertDoesNotExist()
        compose.onNodeWithText("Date unavailable · legacy record").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Best local").assertDoesNotExist()
    }

    @Test fun measuredReportAndShareActionRemainAccessibleWithLargeText() {
        var shares = 0
        val record = TimeSlipRecord(elapsedMillis = 5421, measurementQuality = MeasurementQuality.MEDIUM,
            vehicleProfileId = "instrumentation-fixture", estimatedTimingErrorMillis = 180, obdSampleRateHz = 4.2)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.4f)) {
                NtuTheme(DaylightTheme, reduceMotion = true) {
                    Surface {
                        Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                            ResultCard(record, isPersonalBest = false, onShare = { shares++ })
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("5.421 s").assertIsDisplayed()
        compose.onNodeWithText("แชร์รายงาน").performScrollTo().performClick()
        assertEquals(1, shares)
    }
}
