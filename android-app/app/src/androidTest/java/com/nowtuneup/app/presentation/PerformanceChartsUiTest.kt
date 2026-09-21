package com.nowtuneup.app.presentation

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.nowtuneup.app.feature.timeslip.*
import com.nowtuneup.app.presentation.theme.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class PerformanceChartsUiTest {
    @get:Rule val compose = createComposeRule()

    private fun run() = TimeSlipRecord(
        id = "ui-test", elapsedMillis = 5000, timingStartNanos = 0L, launchTimeNanos = 0L,
        completionTimeNanos = 5_000_000_000L, launchSpeedKmh = 0.0,
        rawSamples = (1..50).map { i -> TimeSlipTelemetrySample(i * 100_000_000L, i * 100L, i * 2.0) },
    )

    @Test fun graphTabsAndComparisonWorkOnNarrowScreen() {
        val record = run()
        compose.setContent {
            NtuTheme(GraphiteTheme, reduceMotion = true) {
                Surface {
                    Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                        TimeSlipCharts(record, record.copy(id = "previous", elapsedMillis = 5200))
                    }
                }
            }
        }
        compose.onNodeWithText("ความเร็ว").assertIsDisplayed()
        compose.onNodeWithText("อัตราเร่ง").performClick().assertIsSelected()
        compose.onNodeWithContentDescription("เลือกเวลาในกราฟ อัตราเร่ง").assertIsDisplayed()
        compose.onNodeWithText("ระยะทาง").performClick().assertIsSelected()
        compose.onNodeWithContentDescription("เทียบกราฟกับครั้งก่อน").performScrollTo().performClick().assertIsOn()
        compose.onNodeWithText("เร็วขึ้น 0.200 s").performScrollTo().assertIsDisplayed()
        screenshot("performance-dark.png")
    }

    @Test fun lightGraphRemainsUsableWithLargerText() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                NtuTheme(DaylightTheme, reduceMotion = true) {
                    Surface {
                        Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) { TimeSlipCharts(run()) }
                    }
                }
            }
        }
        compose.onNodeWithText("วิเคราะห์การทดสอบ").assertIsDisplayed()
        compose.onNodeWithContentDescription("เลือกเวลาในกราฟ ความเร็ว").performScrollTo().assertIsDisplayed()
        screenshot("performance-light-large-text.png")
    }

    @Test fun legacyRecordKeepsClearEmptyStateWithoutAFakeChart() {
        compose.setContent { NtuTheme { TimeSlipCharts(TimeSlipRecord(rawSamples = null)) } }
        compose.onNodeWithText("ผลเดิมยังไม่มีจุดเริ่มกราฟที่แน่นอน เริ่มทดสอบใหม่เพื่อบันทึกกราฟ").assertIsDisplayed()
        compose.onNodeWithContentDescription("เลือกเวลาในกราฟ ความเร็ว").assertDoesNotExist()
    }

    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val additionalOutputDir = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        val directory = additionalOutputDir
            ?.takeIf { it.isNotBlank() }
            ?.let { File(it) }
            ?: instrumentation.targetContext.getExternalFilesDir("review")
            ?: error("No writable screenshot directory is available")
        check(directory.exists() || directory.mkdirs()) { "Unable to create screenshot directory: $directory" }
        File(directory, name).outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
