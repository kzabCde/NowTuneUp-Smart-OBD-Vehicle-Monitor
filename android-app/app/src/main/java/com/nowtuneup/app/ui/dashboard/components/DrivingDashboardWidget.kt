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
import com.nowtuneup.app.domain.model.displayableReading
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
    dtcCount: Int?,
    showPeakHold: Boolean,
    showMinMax: Boolean,
) {
    val protectedReading = displayableReading(reading, freshness)?.takeUnless {
        config.type == com.nowtuneup.app.domain.model.DashboardWidgetType.SHIFT_LIGHT && freshness != DataFreshness.LIVE
    }
    androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
        androidx.compose.foundation.layout.Box(Modifier.weight(1f).fillMaxSize()) {
            DashboardWidgetView(config, protectedReading, reduceMotion, dtcCount,
                stats = stats?.copy(peak = null), freshness = freshness)
        }
        val footer = listOfNotNull(
            freshnessLabel(freshness).takeIf { freshness != DataFreshness.LIVE },
            stats?.takeIf { showMinMax }?.let { "ต่ำ/สูง ${it.minimum.short()}–${it.maximum.short()}" },
        ).joinToString(" · ")
        if (footer.isNotEmpty()) Text(footer, Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (freshness == DataFreshness.STALE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
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

private fun Double?.short(): String = this?.let { value ->
    if (kotlin.math.abs(value) >= 100.0) "%.0f".format(value) else "%.1f".format(value)
} ?: "--"
