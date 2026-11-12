package com.nowtuneup.app.ui.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nowtuneup.app.domain.model.*

@Composable
fun ShiftLightWidget(config: DashboardWidgetConfig, reading: VehicleReading?) {
    val settings = config.shiftLight ?: ShiftLightConfig()
    val rpm = reading?.takeIf { it.pid == 0x0C && it.supported }?.value
    val stage = settings.stage(rpm)
    Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.Center) {
        if (config.showLabel) Text(config.title, color = Color(config.colors.label), style = MaterialTheme.typography.labelSmall)
        if (stage == null) {
            Text(if (!settings.enabled) "Shift lights off · configure RPM" else "RPM unavailable", color = Color(config.colors.label))
        } else {
            val redline = settings.redlineRpm?.let { rpm != null && rpm >= it } == true
            Row(Modifier.fillMaxWidth().height(20.dp).semantics {
                contentDescription = if (redline) "Configured redline reached" else "Shift stage $stage of ${settings.thresholdsRpm.size}"
            }, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                settings.thresholdsRpm.forEachIndexed { index, _ ->
                    val active = index < stage
                    Box(Modifier.weight(1f).fillMaxHeight().background(
                        if (!active) Color(config.colors.border) else
                            Color(if (redline) config.colors.critical else config.colors.warning)
                                .copy(alpha = 0.35f + settings.intensity * 0.65f)))
                }
            }
        }
    }
}
