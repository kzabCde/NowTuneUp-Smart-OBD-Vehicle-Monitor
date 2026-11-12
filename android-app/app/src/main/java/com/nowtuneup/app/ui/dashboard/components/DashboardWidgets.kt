package com.nowtuneup.app.ui.dashboard.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nowtuneup.app.domain.model.*
import com.nowtuneup.app.util.Contrast
import kotlin.math.cos
import kotlin.math.sin
import java.util.Locale

@Composable
fun DashboardWidgetView(
    config: DashboardWidgetConfig,
    reading: VehicleReading?,
    reduceMotion: Boolean,
    dtcCount: Int?,
    stats: ReadingStats? = null,
    freshness: DataFreshness? = null,
) {
    val binding = bindTelemetryGauge(config, reading, stats, freshness)
    val valueText = binding.value?.let { String.format(Locale.getDefault(), "%.${config.decimals}f", it) } ?: "Unavailable"
    val alertText = if (binding.severity != AlertSeverity.NORMAL) ", ${binding.severity.name.lowercase()}" else ""
    val summary = "${binding.label}, $valueText ${binding.unit.label()}, ${binding.freshness.name.lowercase()}$alertText"
    Card(
        modifier = Modifier.fillMaxSize().alpha(config.opacity.coerceIn(0.20f, 1f)).semantics { contentDescription = summary },
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        when (config.type.primaryGaugePresentation()) {
            PrimaryGaugePresentation.DIAL -> DialGauge(config, binding, reduceMotion)
            PrimaryGaugePresentation.DIGITAL -> DigitalGauge(config, binding)
            null -> when(config.type) {
                DashboardWidgetType.SHIFT_LIGHT -> ShiftLightWidget(config, reading)
                DashboardWidgetType.LINE_CHART -> RealtimeLineChart(config, reading, MaterialTheme.colorScheme.primary)
                DashboardWidgetType.DTC_CARD -> DtcWidget(dtcCount)
                else -> Unit
            }
        }
    }
}

@Composable
private fun gaugeColor(binding: TelemetryGaugeBinding, config: DashboardWidgetConfig): Color {
    val surface = MaterialTheme.colorScheme.surfaceContainerLow
    return when(binding.severity) {
        AlertSeverity.CRITICAL -> Color(Contrast.readableColor(config.colors.critical, surface.toArgb().toLong(),
            MaterialTheme.colorScheme.error.toArgb().toLong()))
        AlertSeverity.WARNING -> Color(Contrast.readableColor(config.colors.warning, surface.toArgb().toLong(),
            if (surface.luminance() > 0.5f) 0xFF855300 else 0xFFFFB300))
        AlertSeverity.NORMAL -> if(binding.value == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    }
}

@Composable
private fun CompactGaugeReadout(config: DashboardWidgetConfig, binding: TelemetryGaugeBinding, text: String, fontSize: Float) {
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (config.showLabel) Text(binding.label, Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(text, Modifier.weight(1f), color = gaugeColor(binding, config), fontSize = fontSize.sp,
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, maxLines = 1,
            textAlign = if (config.showLabel) TextAlign.End else TextAlign.Center, overflow = TextOverflow.Ellipsis)
        if (config.showUnit && binding.value != null && binding.unit.label().isNotBlank()) Text(binding.unit.label(),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
fun DigitalGauge(config: DashboardWidgetConfig, binding: TelemetryGaugeBinding) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        val fontScale = LocalDensity.current.fontScale
        val value = binding.value?.let { String.format(Locale.getDefault(), "%.${config.decimals}f", it) } ?: "—"
        val fontSize = minOf(
            config.valueSize.toFloat(),
            maxHeight.value * 0.38f / fontScale,
            maxWidth.value / (value.length.coerceAtLeast(2) * 0.65f) / fontScale,
        ).coerceIn(12f, 42f)

        if (maxHeight < 58.dp) {
            CompactGaugeReadout(config, binding, value, fontSize)
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Center,
            ) {
                if (config.showLabel) {
                    Text(
                        config.title,
                        modifier = Modifier.padding(bottom = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        value,
                        color = gaugeColor(binding, config),
                        fontSize = fontSize.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                    if (binding.value != null && config.showUnit && binding.unit.label().isNotBlank()) {
                        Text(
                            binding.unit.label(),
                            modifier = Modifier.padding(bottom = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                if (binding.value == null) {
                    Text(
                        "Unavailable",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun DialGauge(config: DashboardWidgetConfig, binding: TelemetryGaugeBinding, reduceMotion: Boolean) {
    val target = binding.value?.let {
        ((it - binding.minimum) / (binding.maximum - binding.minimum)).toFloat().coerceIn(0f, 1f)
    }
    val animated by animateFloatAsState(
        targetValue = target ?: 0f,
        animationSpec = tween(if (reduceMotion || target == null) 0 else 160),
        label = "dial-display-only",
    )
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val track = MaterialTheme.colorScheme.outlineVariant
    val accent = if (binding.severity == AlertSeverity.NORMAL) MaterialTheme.colorScheme.primary
        else gaugeColor(binding, config)

    BoxWithConstraints(Modifier.fillMaxSize().padding(8.dp)) {
        val text = binding.value?.let { String.format(Locale.getDefault(), "%.${config.decimals}f", it) } ?: "—"
        if (maxHeight < 92.dp || maxWidth < 100.dp) {
            CompactGaugeReadout(config, binding, text, 12f)
            return@BoxWithConstraints
        }

        val labelHeight = if (config.showLabel) 20.dp else 2.dp
        val readoutHeight = (maxHeight * 0.30f).coerceAtLeast(30.dp)
        Canvas(Modifier.fillMaxSize().padding(top = labelHeight, bottom = readoutHeight)) {
            val radius = minOf(size.width / 2f, size.height / 1.7f).coerceAtLeast(1f) * 0.88f
            val center = Offset(size.width / 2f, size.height * 0.68f)
            val origin = Offset(center.x - radius, center.y - radius)
            drawArc(
                track, 135f, 270f, false, origin, Size(radius * 2f, radius * 2f),
                style = Stroke(2.dp.toPx(), cap = StrokeCap.Round),
            )
            // Five quiet index ticks replace the dense twenty-one tick dial.
            for (tick in 0..4) {
                val angle = Math.toRadians((135f + tick * 67.5f).toDouble())
                val outer = Offset(
                    center.x + cos(angle).toFloat() * radius,
                    center.y + sin(angle).toFloat() * radius,
                )
                val inner = Offset(
                    center.x + cos(angle).toFloat() * radius * 0.89f,
                    center.y + sin(angle).toFloat() * radius * 0.89f,
                )
                drawLine(ink.copy(alpha = 0.62f), inner, outer, 1.dp.toPx())
            }
            if (target != null) {
                val angle = Math.toRadians((135f + 270f * animated).toDouble())
                drawLine(
                    accent,
                    center,
                    Offset(
                        center.x + cos(angle).toFloat() * radius * 0.77f,
                        center.y + sin(angle).toFloat() * radius * 0.77f,
                    ),
                    2.dp.toPx(),
                    StrokeCap.Round,
                )
                drawCircle(accent, 3.dp.toPx(), center)
            }
        }

        if (config.showLabel) {
            Text(
                config.title,
                Modifier.align(Alignment.TopCenter),
                style = MaterialTheme.typography.labelSmall,
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val dialValueSize = minOf(
            config.valueSize.toFloat(), maxWidth.value * 0.16f, maxHeight.value * 0.16f,
        ).coerceIn(12f, 30f).sp
        Column(Modifier.align(Alignment.BottomCenter), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                fontSize = dialValueSize,
                color = gaugeColor(binding, config),
                maxLines = 1,
            )
            if (binding.value == null || config.showUnit) {
                Text(
                    if (binding.value == null) "Unavailable" else binding.unit.label(),
                    style = MaterialTheme.typography.labelSmall,
                    color = ink,
                )
            }
        }
    }
}

@Composable
private fun DtcWidget(dtcCount: Int?) {
    Column(Modifier.fillMaxSize().padding(12.dp),verticalArrangement=Arrangement.SpaceBetween) {
        Text("DTC status",style=MaterialTheme.typography.labelLarge)
        Text(when(dtcCount) { null -> "Not scanned"; 0 -> "No codes in last scan"; else -> "$dtcCount code(s) in last scan" },style=MaterialTheme.typography.titleMedium)
        Text("Last read-only scan · not live",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RealtimeLineChart(
    config: DashboardWidgetConfig,
    reading: VehicleReading?,
    color: Color,
) {
    val samples = remember(config.id) { mutableStateListOf<Pair<Long, Double>>() }
    val value = reading?.takeIf { it.supported }?.value

    LaunchedEffect(reading?.sourceTimeNanos, reading?.updatedAt, value) {
        val time = reading?.sourceTimeNanos ?: reading?.updatedAt?.times(1_000_000L)
        if (value == null || !value.isFinite() || time == null) samples.clear()
        else if (time > (samples.lastOrNull()?.first ?: Long.MIN_VALUE)) {
            samples.add(time to value)
            while (samples.size > 72) samples.removeAt(0)
        }
    }

    val configuredMin = config.scaleMinimum
    val configuredMax = config.scaleMaximum
    val sampleMin = samples.minOfOrNull { it.second } ?: 0.0
    val sampleMax = samples.maxOfOrNull { it.second } ?: 1.0
    val minimum = configuredMin ?: sampleMin
    val maximum = configuredMax ?: sampleMax.coerceAtLeast(minimum + 1.0)

    Box(Modifier.fillMaxSize().padding(12.dp)) {
        Canvas(Modifier.fillMaxSize().padding(top = 28.dp, bottom = 24.dp)) {
            if (samples.size < 2 || maximum <= minimum) return@Canvas
            val start = samples.first().first
            val span = (samples.last().first - start).coerceAtLeast(1L)
            val path = Path()
            samples.forEachIndexed { index, (time, sample) ->
                val fraction = ((sample - minimum) / (maximum - minimum)).toFloat().coerceIn(0f, 1f)
                val point = Offset(((time - start).toDouble() / span * size.width).toFloat(), size.height - (fraction * size.height))
                if (index == 0 || time - samples[index - 1].first > 1_500_000_000L) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
            }
            drawLine(
                color = Color(config.colors.border).copy(alpha = 0.45f),
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = 1.dp.toPx(),
            )
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
            )
        }

        if (config.showLabel) {
            Text(
                config.title,
                modifier = Modifier.align(Alignment.TopStart),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = value?.let { "%.${config.decimals}f".format(it) } ?: "--",
            modifier = Modifier.align(Alignment.TopEnd),
            color = color,
            fontWeight = FontWeight.Black,
        )
        if (config.showUnit) {
            Text(
                config.unit.label(),
                modifier = Modifier.align(Alignment.BottomEnd),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
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
