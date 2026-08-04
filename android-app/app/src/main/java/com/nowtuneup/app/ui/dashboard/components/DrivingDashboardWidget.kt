package com.nowtuneup.app.ui.dashboard.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nowtuneup.app.domain.model.DataFreshness
import com.nowtuneup.app.domain.model.DashboardWidgetConfig
import com.nowtuneup.app.domain.model.ReadingStats
import com.nowtuneup.app.domain.model.VehicleReading

@Composable
@Suppress("UNUSED_PARAMETER")
fun DrivingDashboardWidget(
    config: DashboardWidgetConfig,
    reading: VehicleReading?,
    stats: ReadingStats?,
    freshness: DataFreshness,
    reduceMotion: Boolean,
    dtcCount: Int,
    showPeakHold: Boolean,
    showMinMax: Boolean,
) {
    val protectedReading = if (freshness in setOf(DataFreshness.STALE, DataFreshness.RECONNECTING)) {
        reading?.copy(value = null)
    } else {
        reading
    }
    val contentAlpha = when (freshness) {
        DataFreshness.STALE, DataFreshness.UNSUPPORTED -> 0.60f
        DataFreshness.NO_DATA, DataFreshness.RECONNECTING -> 0.76f
        else -> 1f
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().alpha(contentAlpha)) {
            DashboardWidgetView(
                config = config,
                reading = protectedReading,
                reduceMotion = reduceMotion,
                dtcCount = dtcCount,
                // Peak and session maximum previously used the same calculation.
                // Keep only the useful session Min/Max data and suppress the duplicate marker.
                stats = stats?.copy(peak = null),
            )
        }

        if (freshness != DataFreshness.LIVE) {
            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                color = freshnessColor(freshness).copy(alpha = 0.94f),
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = freshnessLabel(freshness),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (freshness == DataFreshness.STALE) Color.White else Color.Black,
                )
            }
        }

        if (stats != null && showMinMax) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
                color = Color.Black.copy(alpha = 0.70f),
                shape = MaterialTheme.shapes.small,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "ต่ำสุด ${stats.minimum.short()}  สูงสุด ${stats.maximum.short()}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

private fun freshnessLabel(value: DataFreshness): String = when (value) {
    DataFreshness.LIVE -> "สด"
    DataFreshness.DELAYED -> "ล่าช้า"
    DataFreshness.STALE -> "ข้อมูลเก่า"
    DataFreshness.NO_DATA -> "รอข้อมูล ECU"
    DataFreshness.UNSUPPORTED -> "รถไม่รองรับ"
    DataFreshness.RECONNECTING -> "กำลังเชื่อมต่อใหม่"
}

private fun freshnessColor(value: DataFreshness): Color = when (value) {
    DataFreshness.LIVE -> Color(0xFF4CAF50)
    DataFreshness.DELAYED -> Color(0xFFFFC107)
    DataFreshness.STALE -> Color(0xFFD84315)
    DataFreshness.NO_DATA -> Color(0xFFB0BEC5)
    DataFreshness.UNSUPPORTED -> Color(0xFF90A4AE)
    DataFreshness.RECONNECTING -> Color(0xFF29B6F6)
}

private fun Double?.short(): String = this?.let { value ->
    if (kotlin.math.abs(value) >= 100.0) "%.0f".format(value) else "%.1f".format(value)
} ?: "--"
