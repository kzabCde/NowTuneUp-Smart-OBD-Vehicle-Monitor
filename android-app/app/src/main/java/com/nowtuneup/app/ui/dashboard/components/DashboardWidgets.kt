package com.nowtuneup.app.ui.dashboard.components

import android.graphics.Paint
import android.graphics.Typeface
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nowtuneup.app.domain.model.BezelFinish
import com.nowtuneup.app.domain.model.DashboardWidgetConfig
import com.nowtuneup.app.domain.model.DashboardWidgetType
import com.nowtuneup.app.domain.model.DigitalRingColorPreset
import com.nowtuneup.app.domain.model.DisplayUnit
import com.nowtuneup.app.domain.model.GaugeSmoothing
import com.nowtuneup.app.domain.model.ReadingStats
import com.nowtuneup.app.domain.model.VehicleReading
import com.nowtuneup.app.domain.model.digitalRingPreset
import com.nowtuneup.app.domain.model.resolvedGaugePreset
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

private enum class ReadingStatus(val label: String) {
    NO_DATA("Waiting"),
    NORMAL("Live"),
    WARNING("Warning"),
    CRITICAL("Critical"),
}

@Composable
fun DashboardWidgetView(
    config: DashboardWidgetConfig,
    reading: VehicleReading?,
    reduceMotion: Boolean,
    dtcCount: Int,
    stats: ReadingStats? = null,
) {
    val value = reading?.takeIf { it.supported }?.value
    val status = config.readingStatus(value)
    val normalColor = if (config.type == DashboardWidgetType.DIGITAL_RING) {
        Color((config.digitalRing ?: digitalRingPreset(DigitalRingColorPreset.AMBER)).digitColor)
    } else {
        Color(config.resolvedGaugePreset().value)
    }
    val statusColor = when (status) {
        ReadingStatus.NO_DATA -> MaterialTheme.colorScheme.onSurfaceVariant
        ReadingStatus.NORMAL -> normalColor
        ReadingStatus.WARNING -> Color(config.colors.warning)
        ReadingStatus.CRITICAL -> Color(config.colors.critical)
    }
    val minimumHeight = config.minimumHeight()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minimumHeight)
            .border(
                width = 1.dp,
                color = Color(config.colors.border).copy(alpha = 0.55f),
                shape = MaterialTheme.shapes.extraLarge,
            ),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = Color(config.colors.background)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        when (config.type) {
            DashboardWidgetType.ANALOG,
            DashboardWidgetType.MINI_GAUGE,
            -> PremiumAnalogGauge(config, reading, stats, status, statusColor, reduceMotion, minimumHeight)

            DashboardWidgetType.DIGITAL_RING -> DigitalRingGauge(
                config = config,
                reading = reading,
                status = status,
                statusColor = statusColor,
                reduceMotion = reduceMotion,
                minimumHeight = minimumHeight,
            )

            DashboardWidgetType.PROGRESS -> ProgressWidget(config, reading, status, statusColor)
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
        modifier = Modifier.fillMaxSize().padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(config.title, color = Color(config.colors.label), fontWeight = FontWeight.SemiBold)
            StatusLabel(status, color)
        }
        Text(
            text = value?.let { "%.${config.decimals}f".format(it) } ?: "--",
            color = color,
            fontSize = config.valueSize.coerceIn(20, 80).sp,
            fontWeight = FontWeight.Black,
        )
        Text(
            if (value == null) "Waiting for ECU" else config.unit.label(),
            style = MaterialTheme.typography.labelMedium,
            color = Color(config.colors.label),
        )
    }
}

@Composable
private fun ProgressWidget(
    config: DashboardWidgetConfig,
    reading: VehicleReading?,
    status: ReadingStatus,
    color: Color,
) {
    val value = reading?.takeIf { it.supported }?.value
    val scale = config.resolveScale(reading)
    Column(
        modifier = Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.SpaceBetween,
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
            fontWeight = FontWeight.Bold,
        )
        LinearProgressIndicator(
            progress = { value?.let { normalize(it, scale.minimum, scale.maximum) } ?: 0f },
            modifier = Modifier.fillMaxWidth(),
            color = color,
            trackColor = Color(config.colors.border).copy(alpha = 0.55f),
        )
    }
}

@Composable
private fun DigitalRingGauge(
    config: DashboardWidgetConfig,
    reading: VehicleReading?,
    status: ReadingStatus,
    statusColor: Color,
    reduceMotion: Boolean,
    minimumHeight: Dp,
) {
    val ring = config.digitalRing ?: digitalRingPreset(DigitalRingColorPreset.AMBER)
    val scale = config.resolveScale(reading)
    val minimum = scale.minimum
    val maximum = scale.maximum
    val value = reading?.takeIf { it.supported }?.value
    val target = value?.let { ((it - minimum) / (maximum - minimum)).toFloat().coerceIn(0f, 1f) } ?: 0f
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = if (reduceMotion) spring(stiffness = 10_000f)
        else spring(dampingRatio = 0.86f, stiffness = 110f),
        label = "digital ring progress",
    )
    val activeColor = when (status) {
        ReadingStatus.WARNING -> Color(config.colors.warning)
        ReadingStatus.CRITICAL -> Color(config.colors.critical)
        else -> Color(ring.activeSegmentColor)
    }
    val digitColor = when (status) {
        ReadingStatus.WARNING, ReadingStatus.CRITICAL -> statusColor
        else -> Color(ring.digitColor)
    }
    val segmentCount = ring.segmentCount.coerceIn(12, 72)
    val activeSegments = ceil(progress * segmentCount).toInt().coerceIn(0, segmentCount)

    Box(
        modifier = Modifier.fillMaxWidth().height(minimumHeight.coerceAtLeast(230.dp)).padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val startAngle = 135f
            val sweepAngle = 270f
            val radius = size.minDimension * 0.40f
            val segmentStroke = (radius * 0.13f).coerceIn(10f, 28f)
            val gapDegrees = (sweepAngle / segmentCount) * 0.24f
            val segmentSweep = (sweepAngle / segmentCount) - gapDegrees
            val arcTopLeft = Offset(center.x - radius, center.y - radius)
            val arcSize = Size(radius * 2f, radius * 2f)

            drawCircle(
                brush = Brush.linearGradient(
                    listOf(Color(0xFF111418), Color(ring.bezelColor), Color(0xFF9CA2A8), Color(0xFF20242A)),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                ),
                radius = radius + segmentStroke * 1.20f,
                center = center,
            )
            drawCircle(Color.Black, radius + segmentStroke * 0.75f, center)
            drawCircle(Color(0xFF050607), radius - segmentStroke * 0.58f, center)

            repeat(segmentCount) { index ->
                val lit = index < activeSegments
                val segmentColor = if (lit) activeColor else Color(ring.inactiveSegmentColor)
                val segmentStart = startAngle + index * (sweepAngle / segmentCount) + gapDegrees / 2f
                if (lit) {
                    drawArc(
                        color = segmentColor.copy(alpha = 0.16f),
                        startAngle = segmentStart,
                        sweepAngle = segmentSweep,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = segmentStroke * 1.75f, cap = StrokeCap.Butt),
                    )
                }
                drawArc(
                    color = segmentColor,
                    startAngle = segmentStart,
                    sweepAngle = segmentSweep,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = segmentStroke, cap = StrokeCap.Butt),
                )
            }

            if (ring.showScaleLabels && radius > 90f) {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color(ring.scaleColor).toArgb()
                    textAlign = Paint.Align.CENTER
                    textSize = (radius * 0.13f).coerceIn(12f, 26f)
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                }
                val labelRadius = radius - segmentStroke * 1.55f
                repeat(5) { index ->
                    val fraction = index / 4f
                    val angle = Math.toRadians((startAngle + fraction * sweepAngle).toDouble())
                    val labelValue = minimum + (maximum - minimum) * fraction
                    val label = if (maximum - minimum <= 20.0) "%.1f".format(labelValue) else "%.0f".format(labelValue)
                    val x = center.x + cos(angle).toFloat() * labelRadius
                    val y = center.y + sin(angle).toFloat() * labelRadius - (paint.ascent() + paint.descent()) / 2f
                    drawContext.canvas.nativeCanvas.drawText(label, x, y, paint)
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(top = 18.dp),
        ) {
            Text(
                config.title.uppercase(),
                color = Color(ring.titleColor),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                value?.let { "%.${config.decimals}f".format(it) } ?: "--",
                color = digitColor,
                fontSize = config.valueSize.coerceIn(34, 80).sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                if (value == null) "WAITING" else config.unit.label().uppercase(),
                color = Color(ring.scaleColor),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            StatusLabel(status, statusColor)
        }
    }
}

@Composable
private fun PremiumAnalogGauge(
    config: DashboardWidgetConfig,
    reading: VehicleReading?,
    stats: ReadingStats?,
    status: ReadingStatus,
    statusColor: Color,
    reduceMotion: Boolean,
    minimumHeight: Dp,
) {
    val preset = config.resolvedGaugePreset()
    val scale = config.resolveScale(reading)
    val minimum = scale.minimum
    val maximum = scale.maximum
    val value = reading?.takeIf { it.supported }?.value
    val target = value?.let { normalize(it, minimum, maximum) } ?: 0f
    val smoothing = config.gaugeSmoothing ?: GaugeSmoothing.BALANCED
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = when {
            reduceMotion -> spring(stiffness = 10_000f)
            smoothing == GaugeSmoothing.FAST -> spring(dampingRatio = 0.72f, stiffness = 260f)
            smoothing == GaugeSmoothing.SMOOTH -> spring(dampingRatio = 0.92f, stiffness = 65f)
            else -> spring(dampingRatio = 0.84f, stiffness = 120f)
        },
        label = "premium gauge needle",
    )
    val labelPaint = remember(preset.tick, preset.label) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
    }
    val needleColor = when (status) {
        ReadingStatus.WARNING -> Color(preset.warning)
        ReadingStatus.CRITICAL -> Color(preset.critical)
        else -> Color(preset.needle)
    }
    val valueColor = when (status) {
        ReadingStatus.WARNING -> Color(preset.warning)
        ReadingStatus.CRITICAL -> Color(preset.critical)
        else -> Color(preset.value)
    }

    Box(
        modifier = Modifier.fillMaxWidth().height(minimumHeight.coerceAtLeast(228.dp)).padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension * 0.46f
            val centerPoint = center
            val startAngle = 120f
            val sweepAngle = 300f
            val scaleRadius = radius * 0.73f
            val faceRadius = radius * 0.84f
            val bezelBrush = bezelBrush(preset.bezelFinish, Color(preset.bezel), size)

            drawCircle(Color.Black.copy(alpha = 0.72f), radius = radius * 1.03f, center = centerPoint + Offset(0f, radius * 0.035f))
            drawCircle(brush = bezelBrush, radius = radius, center = centerPoint)
            drawCircle(
                color = Color.White.copy(alpha = 0.28f),
                radius = radius * 0.965f,
                center = centerPoint,
                style = Stroke(width = (radius * 0.018f).coerceAtLeast(1.5f)),
            )
            drawCircle(Color(0xFF050608), radius = radius * 0.91f, center = centerPoint)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(preset.face).copy(alpha = 0.95f), Color(preset.face), Color.Black),
                    center = centerPoint - Offset(radius * 0.10f, radius * 0.16f),
                    radius = faceRadius * 1.15f,
                ),
                radius = faceRadius,
                center = centerPoint,
            )
            drawCircle(
                color = Color(preset.tick).copy(alpha = 0.16f),
                radius = faceRadius * 0.97f,
                center = centerPoint,
                style = Stroke(width = (radius * 0.018f).coerceAtLeast(1f)),
            )

            val scaleTopLeft = Offset(centerPoint.x - scaleRadius, centerPoint.y - scaleRadius)
            val scaleSize = Size(scaleRadius * 2f, scaleRadius * 2f)
            drawArc(
                color = Color(preset.tick).copy(alpha = 0.20f),
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = scaleTopLeft,
                size = scaleSize,
                style = Stroke(width = (radius * 0.035f).coerceAtLeast(4f), cap = StrokeCap.Round),
            )

            drawThresholdArcs(
                config = config,
                minimum = minimum,
                maximum = maximum,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                topLeft = scaleTopLeft,
                arcSize = scaleSize,
                width = (radius * 0.042f).coerceAtLeast(5f),
            )

            val compact = radius < 105f
            val minorTickCount = if (compact) 30 else 50
            val majorEvery = 5
            repeat(minorTickCount + 1) { index ->
                val fraction = index / minorTickCount.toFloat()
                val angleDegrees = startAngle + sweepAngle * fraction
                val angle = Math.toRadians(angleDegrees.toDouble())
                val major = index % majorEvery == 0
                val outerRadius = scaleRadius * 0.98f
                val length = if (major) radius * 0.105f else radius * 0.055f
                val outer = Offset(
                    centerPoint.x + cos(angle).toFloat() * outerRadius,
                    centerPoint.y + sin(angle).toFloat() * outerRadius,
                )
                val inner = Offset(
                    centerPoint.x + cos(angle).toFloat() * (outerRadius - length),
                    centerPoint.y + sin(angle).toFloat() * (outerRadius - length),
                )
                drawLine(
                    color = Color(preset.tick).copy(alpha = if (major) 1f else 0.62f),
                    start = inner,
                    end = outer,
                    strokeWidth = if (major) (radius * 0.018f).coerceAtLeast(2f) else (radius * 0.009f).coerceAtLeast(1f),
                    cap = StrokeCap.Round,
                )
            }

            if (!compact) {
                labelPaint.color = Color(preset.label).toArgb()
                labelPaint.textSize = (radius * 0.105f).coerceIn(12f, 28f)
                val labels = 6
                repeat(labels + 1) { index ->
                    val fraction = index / labels.toFloat()
                    val angle = Math.toRadians((startAngle + sweepAngle * fraction).toDouble())
                    val labelRadius = scaleRadius * 0.69f
                    val labelValue = minimum + (maximum - minimum) * fraction
                    val text = formatScale(labelValue, maximum - minimum)
                    val x = centerPoint.x + cos(angle).toFloat() * labelRadius
                    val y = centerPoint.y + sin(angle).toFloat() * labelRadius - (labelPaint.ascent() + labelPaint.descent()) / 2f
                    drawContext.canvas.nativeCanvas.drawText(text, x, y, labelPaint)
                }
            }

            if (config.showPeakMarker) {
                stats?.peak?.let { peak ->
                    val peakFraction = normalize(peak, minimum, maximum)
                    val peakAngle = Math.toRadians((startAngle + sweepAngle * peakFraction).toDouble())
                    val outer = Offset(
                        centerPoint.x + cos(peakAngle).toFloat() * scaleRadius * 1.02f,
                        centerPoint.y + sin(peakAngle).toFloat() * scaleRadius * 1.02f,
                    )
                    val inner = Offset(
                        centerPoint.x + cos(peakAngle).toFloat() * scaleRadius * 0.87f,
                        centerPoint.y + sin(peakAngle).toFloat() * scaleRadius * 0.87f,
                    )
                    drawLine(Color(preset.warning), inner, outer, (radius * 0.025f).coerceAtLeast(3f), StrokeCap.Round)
                }
            }

            val needleAngle = Math.toRadians((startAngle + sweepAngle * animated).toDouble())
            val needleTip = Offset(
                centerPoint.x + cos(needleAngle).toFloat() * scaleRadius * 0.88f,
                centerPoint.y + sin(needleAngle).toFloat() * scaleRadius * 0.88f,
            )
            val perpendicular = needleAngle + Math.PI / 2.0
            val halfBase = radius * 0.045f
            val baseLeft = Offset(
                centerPoint.x + cos(perpendicular).toFloat() * halfBase,
                centerPoint.y + sin(perpendicular).toFloat() * halfBase,
            )
            val baseRight = Offset(
                centerPoint.x - cos(perpendicular).toFloat() * halfBase,
                centerPoint.y - sin(perpendicular).toFloat() * halfBase,
            )
            val shadowOffset = Offset(radius * 0.018f, radius * 0.022f)
            val shadowPath = Path().apply {
                moveTo(baseLeft.x + shadowOffset.x, baseLeft.y + shadowOffset.y)
                lineTo(needleTip.x + shadowOffset.x, needleTip.y + shadowOffset.y)
                lineTo(baseRight.x + shadowOffset.x, baseRight.y + shadowOffset.y)
                close()
            }
            drawPath(shadowPath, Color.Black.copy(alpha = 0.65f))
            val needlePath = Path().apply {
                moveTo(baseLeft.x, baseLeft.y)
                lineTo(needleTip.x, needleTip.y)
                lineTo(baseRight.x, baseRight.y)
                close()
            }
            drawPath(needlePath, needleColor)
            drawLine(
                color = Color(preset.needleHighlight).copy(alpha = 0.75f),
                start = centerPoint,
                end = Offset(
                    centerPoint.x + (needleTip.x - centerPoint.x) * 0.72f,
                    centerPoint.y + (needleTip.y - centerPoint.y) * 0.72f,
                ),
                strokeWidth = (radius * 0.012f).coerceAtLeast(1.5f),
                cap = StrokeCap.Round,
            )
            drawCircle(Color.Black, radius = radius * 0.105f, center = centerPoint)
            drawCircle(brush = bezelBrush, radius = radius * 0.082f, center = centerPoint)
            drawCircle(needleColor, radius = radius * 0.046f, center = centerPoint)

            if (status in setOf(ReadingStatus.WARNING, ReadingStatus.CRITICAL)) {
                drawCircle(
                    color = valueColor.copy(alpha = 0.20f),
                    radius = radius * 0.89f,
                    center = centerPoint,
                    style = Stroke(width = (radius * 0.055f).coerceAtLeast(6f)),
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(top = 74.dp),
        ) {
            Text(
                config.title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = Color(preset.label),
                fontWeight = FontWeight.Bold,
            )
            Text(
                value?.let { "%.${config.decimals}f".format(it) } ?: "--",
                fontSize = config.valueSize.coerceIn(24, 58).sp,
                fontWeight = FontWeight.Black,
                color = valueColor,
            )
            Text(
                if (value == null) "WAITING FOR ECU" else config.unit.label().uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Color(preset.label).copy(alpha = 0.82f),
            )
            StatusLabel(status, statusColor)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawThresholdArcs(
    config: DashboardWidgetConfig,
    minimum: Double,
    maximum: Double,
    startAngle: Float,
    sweepAngle: Float,
    topLeft: Offset,
    arcSize: Size,
    width: Float,
) {
    config.threshold.warningHigh?.let { warning ->
        val warningStart = normalize(warning, minimum, maximum)
        val criticalStart = config.threshold.criticalHigh?.let { normalize(it, minimum, maximum) } ?: 1f
        val span = (criticalStart - warningStart).coerceAtLeast(0f)
        if (span > 0f) {
            drawArc(
                color = Color(config.colors.warning),
                startAngle = startAngle + sweepAngle * warningStart,
                sweepAngle = sweepAngle * span,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = width, cap = StrokeCap.Butt),
            )
        }
    }
    config.threshold.criticalHigh?.let { critical ->
        val criticalStart = normalize(critical, minimum, maximum)
        val span = (1f - criticalStart).coerceAtLeast(0f)
        if (span > 0f) {
            drawArc(
                color = Color(config.colors.critical),
                startAngle = startAngle + sweepAngle * criticalStart,
                sweepAngle = sweepAngle * span,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = width, cap = StrokeCap.Butt),
            )
        }
    }
    config.threshold.warningLow?.let { warning ->
        val end = normalize(warning, minimum, maximum)
        if (end > 0f) {
            drawArc(
                color = Color(config.colors.warning),
                startAngle = startAngle,
                sweepAngle = sweepAngle * end,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = width, cap = StrokeCap.Butt),
            )
        }
    }
    config.threshold.criticalLow?.let { critical ->
        val end = normalize(critical, minimum, maximum)
        if (end > 0f) {
            drawArc(
                color = Color(config.colors.critical),
                startAngle = startAngle,
                sweepAngle = sweepAngle * end,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = width, cap = StrokeCap.Butt),
            )
        }
    }
}

private fun bezelBrush(finish: BezelFinish, base: Color, size: Size): Brush = when (finish) {
    BezelFinish.BRUSHED_STEEL -> Brush.linearGradient(
        listOf(Color(0xFF171B1F), base, Color(0xFFD9DCDE), Color(0xFF6D7378), Color(0xFF111417)),
        start = Offset.Zero,
        end = Offset(size.width, size.height),
    )
    BezelFinish.BLACK_CHROME -> Brush.linearGradient(
        listOf(Color(0xFF050607), Color(0xFF3B3E43), base, Color(0xFF111214), Color(0xFF555A60)),
        start = Offset(0f, size.height),
        end = Offset(size.width, 0f),
    )
    BezelFinish.TITANIUM_DARK -> Brush.linearGradient(
        listOf(Color(0xFF11161B), base, Color(0xFF707B84), Color(0xFF262D33), Color(0xFF0A0D10)),
        start = Offset.Zero,
        end = Offset(size.width, size.height),
    )
}

@Composable
private fun DtcWidget(dtcCount: Int) {
    val detail = if (dtcCount == 0) "No stored codes found" else "$dtcCount stored code(s)"
    Column(
        modifier = Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("DTC status", fontWeight = FontWeight.Bold)
        Text(detail, style = MaterialTheme.typography.titleMedium)
        Text("Read-only result", style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun StatusLabel(status: ReadingStatus, color: Color) {
    Text(status.label, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
}

private fun DashboardWidgetConfig.minimumHeight(): Dp = when (rowSpan.coerceIn(1, 4)) {
    1 -> when (type) {
        DashboardWidgetType.ANALOG, DashboardWidgetType.MINI_GAUGE -> 228.dp
        DashboardWidgetType.DIGITAL_RING -> 230.dp
        else -> 132.dp
    }
    2 -> 282.dp
    3 -> 350.dp
    else -> 430.dp
}

private fun DashboardWidgetConfig.readingStatus(value: Double?): ReadingStatus = when {
    value == null -> ReadingStatus.NO_DATA
    threshold.criticalLow?.let { value <= it } == true || threshold.criticalHigh?.let { value >= it } == true -> ReadingStatus.CRITICAL
    threshold.warningLow?.let { value <= it } == true || threshold.warningHigh?.let { value >= it } == true -> ReadingStatus.WARNING
    else -> ReadingStatus.NORMAL
}

private data class WidgetScale(val minimum: Double, val maximum: Double)

private fun DashboardWidgetConfig.resolveScale(reading: VehicleReading?): WidgetScale {
    val customMinimum = scaleMinimum
    val customMaximum = scaleMaximum
    if (
        customMinimum != null && customMaximum != null &&
        customMinimum.isFinite() && customMaximum.isFinite() &&
        customMaximum > customMinimum
    ) {
        return WidgetScale(customMinimum, customMaximum)
    }

    val readingMinimum = reading?.minimum?.takeIf { it.isFinite() } ?: 0.0
    val readingMaximum = reading?.maximum?.takeIf { it.isFinite() && it > readingMinimum } ?: 100.0
    return WidgetScale(readingMinimum, readingMaximum)
}

private fun normalize(value: Double, minimum: Double, maximum: Double): Float =
    if (maximum <= minimum) 0f else ((value - minimum) / (maximum - minimum)).toFloat().coerceIn(0f, 1f)

private fun formatScale(value: Double, range: Double): String = when {
    range <= 10.0 -> "%.1f".format(value)
    range <= 100.0 -> "%.0f".format(value)
    else -> "%.0f".format(value)
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
