package com.nowtuneup.app.ui.dashboard.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nowtuneup.app.domain.model.DashboardWidgetConfig
import com.nowtuneup.app.domain.model.DashboardWidgetType
import com.nowtuneup.app.domain.model.DisplayUnit
import com.nowtuneup.app.domain.model.GaugeStyle
import com.nowtuneup.app.domain.model.VehicleReading
import kotlin.math.cos
import kotlin.math.sin

private enum class ReadingStatus(val label: String) {
    NO_DATA("Waiting"),
    NORMAL("Normal"),
    WARNING("Warning"),
    CRITICAL("Critical"),
}

private data class GaugeVisual(
    val tickCount: Int,
    val arcWidth: Float,
    val needleWidth: Float,
    val showProgressArc: Boolean,
    val glow: Boolean,
)

@Composable
fun DashboardWidgetView(
    config: DashboardWidgetConfig,
    reading: VehicleReading?,
    reduceMotion: Boolean,
    dtcCount: Int,
) {
    val value = reading?.takeIf { it.supported }?.value
    val status = config.readingStatus(value)
    val statusColor = when (status) {
        ReadingStatus.NO_DATA -> MaterialTheme.colorScheme.onSurfaceVariant
        ReadingStatus.NORMAL -> Color(config.colors.value)
        ReadingStatus.WARNING -> Color(config.colors.warning)
        ReadingStatus.CRITICAL -> Color(config.colors.critical)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 132.dp)
            .border(1.dp, Color(config.colors.border), MaterialTheme.shapes.large),
        colors = CardDefaults.cardColors(containerColor = Color(config.colors.background)),
    ) {
        when (config.type) {
            DashboardWidgetType.ANALOG,
            DashboardWidgetType.MINI_GAUGE,
            -> AnalogGauge(config, reading, status, statusColor, reduceMotion)

            DashboardWidgetType.PROGRESS -> ProgressWidget(config, value, status, statusColor)
            DashboardWidgetType.DTC_CARD -> DtcWidget(dtcCount)
            DashboardWidgetType.DIGITAL -> DigitalWidget(config, value, status, statusColor)
        }
    }
}

@Composable
private fun DigitalWidget(
    config: DashboardWidgetConfig,
    value: Double?,
    status: ReadingStatus,
    color: Color,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(config.title, color = Color(config.colors.label), fontWeight = FontWeight.SemiBold)
            StatusLabel(status, color)
        }
        Text(
            text = value?.let { "% .${config.decimals}f".format(it).trim() } ?: "--",
            color = color,
            fontSize = config.valueSize.coerceIn(24, 64).sp,
            fontWeight = FontWeight.Black,
        )
        Text(
            if (value == null) "No vehicle data" else config.unit.label(),
            style = MaterialTheme.typography.labelMedium,
            color = Color(config.colors.label),
        )
    }
}

@Composable
private fun ProgressWidget(
    config: DashboardWidgetConfig,
    value: Double?,
    status: ReadingStatus,
    color: Color,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(config.title, color = Color(config.colors.label), fontWeight = FontWeight.SemiBold)
            StatusLabel(status, color)
        }
        Text(
            value?.let { "%.${config.decimals}f ${config.unit.label()}".format(it) }
                ?: "-- · Waiting for ECU",
            fontSize = 25.sp,
            color = color,
        )
        LinearProgressIndicator(
            progress = { ((value ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = color,
            trackColor = Color(config.colors.border),
        )
    }
}

@Composable
private fun AnalogGauge(
    config: DashboardWidgetConfig,
    reading: VehicleReading?,
    status: ReadingStatus,
    color: Color,
    reduceMotion: Boolean,
) {
    val minimum = reading?.minimum ?: 0.0
    val maximum = reading?.maximum ?: 100.0
    val value = reading?.takeIf { it.supported }?.value
    val target = value?.let {
        if (maximum <= minimum) 0f
        else ((it - minimum) / (maximum - minimum)).toFloat().coerceIn(0f, 1f)
    } ?: 0f
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = if (reduceMotion) {
            spring(stiffness = 10_000f)
        } else {
            spring(dampingRatio = 0.82f, stiffness = 90f)
        },
        label = "gauge needle",
    )
    val visual = config.gaugeStyle.visual()
    val needleColor = when (config.gaugeStyle) {
        GaugeStyle.SPORT -> Color(config.colors.critical)
        GaugeStyle.NEON -> Color(config.colors.value)
        else -> color
    }

    Box(
        modifier = Modifier.fillMaxWidth().height(190.dp).padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension * 0.38f
            val border = Color(config.colors.border)
            val ticks = Color(config.colors.label)

            if (visual.glow) {
                drawArc(
                    color = color.copy(alpha = 0.15f),
                    startAngle = 145f,
                    sweepAngle = 250f * animated,
                    useCenter = false,
                    style = Stroke(visual.arcWidth * 2.8f, cap = StrokeCap.Round),
                )
            }
            drawArc(
                color = border,
                startAngle = 145f,
                sweepAngle = 250f,
                useCenter = false,
                style = Stroke(visual.arcWidth, cap = StrokeCap.Round),
            )
            if (visual.showProgressArc) {
                drawArc(
                    color = color.copy(alpha = if (config.gaugeStyle == GaugeStyle.NEON) 0.95f else 0.65f),
                    startAngle = 145f,
                    sweepAngle = 250f * animated,
                    useCenter = false,
                    style = Stroke(visual.arcWidth, cap = StrokeCap.Round),
                )
            }

            repeat(visual.tickCount) { tick ->
                val denominator = (visual.tickCount - 1).coerceAtLeast(1)
                val angle = Math.toRadians((145f + tick * (250f / denominator)).toDouble())
                val outer = Offset(
                    center.x + cos(angle).toFloat() * radius,
                    center.y + sin(angle).toFloat() * radius,
                )
                val tickLength = if (tick % 2 == 0) 15f else 9f
                val inner = Offset(
                    center.x + cos(angle).toFloat() * (radius - tickLength),
                    center.y + sin(angle).toFloat() * (radius - tickLength),
                )
                drawLine(ticks, inner, outer, if (tick % 2 == 0) 2.5f else 1.5f)
            }

            val angle = Math.toRadians((145f + animated * 250f).toDouble())
            drawLine(
                color = needleColor,
                start = center,
                end = Offset(
                    center.x + cos(angle).toFloat() * (radius - 16f),
                    center.y + sin(angle).toFloat() * (radius - 16f),
                ),
                strokeWidth = visual.needleWidth,
                cap = StrokeCap.Round,
            )
            drawCircle(needleColor, 7f, center)
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 58.dp),
        ) {
            Text(config.title, style = MaterialTheme.typography.labelMedium, color = Color(config.colors.label))
            Text(
                value?.let { "%.${config.decimals}f".format(it) } ?: "--",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            Text(
                if (value == null) "No vehicle data" else config.unit.label(),
                style = MaterialTheme.typography.labelSmall,
                color = Color(config.colors.label),
            )
            StatusLabel(status, color)
        }
    }
}

@Composable
private fun DtcWidget(dtcCount: Int) {
    val detail = if (dtcCount == 0) "No stored codes found" else "$dtcCount stored code(s)"
    Column(
        modifier = Modifier.fillMaxWidth().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("DTC status", fontWeight = FontWeight.Bold)
        Text(detail, style = MaterialTheme.typography.titleMedium)
        Text("Read-only result", style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun StatusLabel(status: ReadingStatus, color: Color) {
    Text(
        status.label,
        color = color,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
    )
}

private fun DashboardWidgetConfig.readingStatus(value: Double?): ReadingStatus = when {
    value == null -> ReadingStatus.NO_DATA
    threshold.criticalLow?.let { value <= it } == true ||
        threshold.criticalHigh?.let { value >= it } == true -> ReadingStatus.CRITICAL
    threshold.warningLow?.let { value <= it } == true ||
        threshold.warningHigh?.let { value >= it } == true -> ReadingStatus.WARNING
    else -> ReadingStatus.NORMAL
}

private fun GaugeStyle.visual(): GaugeVisual = when (this) {
    GaugeStyle.CLASSIC -> GaugeVisual(11, 9f, 6f, true, false)
    GaugeStyle.SPORT -> GaugeVisual(13, 12f, 8f, true, false)
    GaugeStyle.MINIMAL -> GaugeVisual(5, 5f, 4f, false, false)
    GaugeStyle.NEON -> GaugeVisual(11, 8f, 6f, true, true)
    GaugeStyle.OEM -> GaugeVisual(9, 10f, 5f, true, false)
}

fun DisplayUnit.label(): String = when (this) {
    DisplayUnit.KMH -> "km/h"
    DisplayUnit.MPH -> "mph"
    DisplayUnit.CELSIUS -> "°C"
    DisplayUnit.FAHRENHEIT -> "°F"
    DisplayUnit.VOLT -> "V"
    DisplayUnit.PERCENT -> "%"
    DisplayUnit.KPA -> "kPa"
    DisplayUnit.BAR -> "bar"
    DisplayUnit.PSI -> "psi"
    DisplayUnit.LITER -> "L"
    DisplayUnit.GALLON -> "gal"
    DisplayUnit.RPM -> "rpm"
    DisplayUnit.NONE -> ""
}
