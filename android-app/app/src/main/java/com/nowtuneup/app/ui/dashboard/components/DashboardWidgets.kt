package com.nowtuneup.app.ui.dashboard.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nowtuneup.app.domain.model.*
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun DashboardWidgetView(config: DashboardWidgetConfig, reading: VehicleReading?, reduceMotion: Boolean, dtcCount: Int) {
    val value = reading?.takeIf { it.supported }?.value
    val statusColor = when {
        value == null -> MaterialTheme.colorScheme.onSurfaceVariant
        config.threshold.criticalLow?.let { value <= it } == true || config.threshold.criticalHigh?.let { value >= it } == true -> Color(config.colors.critical)
        config.threshold.warningLow?.let { value <= it } == true || config.threshold.warningHigh?.let { value >= it } == true -> Color(config.colors.warning)
        else -> Color(config.colors.value)
    }
    Card(modifier = Modifier.fillMaxWidth().heightIn(min = 132.dp).border(1.dp, Color(config.colors.border), MaterialTheme.shapes.large)) {
        when (config.type) {
            DashboardWidgetType.ANALOG, DashboardWidgetType.MINI_GAUGE -> AnalogGauge(config, reading, statusColor, reduceMotion)
            DashboardWidgetType.PROGRESS -> ProgressWidget(config, value, statusColor)
            DashboardWidgetType.DTC_CARD -> InfoWidget("DTC status", if (dtcCount == 0) "No stored codes" else "$dtcCount stored code(s)")
            DashboardWidgetType.DIGITAL -> DigitalWidget(config, value, statusColor)
        }
    }
}

@Composable private fun DigitalWidget(config: DashboardWidgetConfig, value: Double?, color: Color) {
    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(config.title, color = Color(config.colors.label))
        Text(value?.let { "% .${config.decimals}f".format(it).trim() } ?: "--", color = color, fontSize = config.valueSize.sp, fontWeight = FontWeight.Black)
        Text(if (value == null) "No Data" else config.unit.label(), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable private fun ProgressWidget(config: DashboardWidgetConfig, value: Double?, color: Color) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(config.title)
        Text(value?.let { "%.${config.decimals}f ${config.unit.label()}".format(it) } ?: "-- · No Data", fontSize = 25.sp, color = color)
        LinearProgressIndicator(progress = { ((value ?: 0.0) / 100).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = color)
    }
}

@Composable private fun AnalogGauge(config: DashboardWidgetConfig, reading: VehicleReading?, color: Color, reduceMotion: Boolean) {
    val minimum = reading?.minimum ?: 0.0
    val maximum = reading?.maximum ?: 100.0
    val target = reading?.value?.let { ((it - minimum) / (maximum - minimum)).toFloat().coerceIn(0f, 1f) } ?: 0f
    val animated by animateFloatAsState(target, if (reduceMotion) spring(stiffness = 10_000f) else spring(dampingRatio = .82f, stiffness = 90f), label = "gauge needle")
    Box(Modifier.fillMaxWidth().height(180.dp).padding(8.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension * .38f
            drawArc(Color(config.colors.border), 145f, 250f, false, style = Stroke(9f, cap = StrokeCap.Round))
            drawArc(color.copy(alpha = .6f), 145f, 250f * animated, false, style = Stroke(9f, cap = StrokeCap.Round))
            repeat(11) { tick ->
                val angle = Math.toRadians((145 + tick * 25).toDouble())
                val outer = Offset(center.x + cos(angle).toFloat() * radius, center.y + sin(angle).toFloat() * radius)
                val inner = Offset(center.x + cos(angle).toFloat() * (radius - 12), center.y + sin(angle).toFloat() * (radius - 12))
                drawLine(Color(config.colors.label), inner, outer, 2f)
            }
            val angle = Math.toRadians((145 + animated * 250).toDouble())
            drawLine(Color(config.colors.value), center, Offset(center.x + cos(angle).toFloat() * (radius - 16), center.y + sin(angle).toFloat() * (radius - 16)), 6f, StrokeCap.Round)
            drawCircle(Color(config.colors.value), 7f, center)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 54.dp)) {
            Text(config.title, style = MaterialTheme.typography.labelMedium)
            Text(reading?.value?.let { "%.${config.decimals}f".format(it) } ?: "--", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = color)
            Text(if (reading?.value == null) "No Data" else config.unit.label(), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable private fun InfoWidget(title: String, detail: String) = Column(Modifier.padding(18.dp)) { Text(title, fontWeight = FontWeight.Bold); Text(detail) }

fun DisplayUnit.label() = when (this) {
    DisplayUnit.KMH -> "km/h"; DisplayUnit.MPH -> "mph"; DisplayUnit.CELSIUS -> "°C"; DisplayUnit.FAHRENHEIT -> "°F"
    DisplayUnit.VOLT -> "V"; DisplayUnit.PERCENT -> "%"; DisplayUnit.KPA -> "kPa"; DisplayUnit.BAR -> "bar"; DisplayUnit.PSI -> "psi"
    DisplayUnit.LITER -> "L"; DisplayUnit.GALLON -> "gal"; DisplayUnit.RPM -> "rpm"; DisplayUnit.NONE -> ""
}
