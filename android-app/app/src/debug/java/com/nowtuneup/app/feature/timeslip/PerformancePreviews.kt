package com.nowtuneup.app.feature.timeslip

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.nowtuneup.app.presentation.theme.*

@Preview(name = "Performance report · fixture · 320dp", widthDp = 320, heightDp = 900)
@Preview(name = "Performance report · fixture · landscape", widthDp = 720, heightDp = 420)
@Composable
private fun PerformanceReportPreview() {
    NtuTheme(GraphiteTheme, reduceMotion = true) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("PREVIEW FIXTURE — not vehicle measurements")
            ResultCard(TimeSlipRecord(id = "preview", vehicleProfileId = "Preview vehicle", elapsedMillis = 5400,
                measurementQuality = MeasurementQuality.MEDIUM, qualityScore = 70, obdSampleRateHz = 4.0,
                estimatedTimingErrorMillis = 150), isPersonalBest = false, onShare = {})
        }
    }
}
