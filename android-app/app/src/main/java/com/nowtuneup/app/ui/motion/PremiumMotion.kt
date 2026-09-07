package com.nowtuneup.app.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

object NtuMotion {
    const val Quick = 150
    const val Standard = 220
    const val Emphasis = 300
    const val Splash = 1_050
}

val NtuGraphite = Color(0xFF080B0E)
val NtuWhite = Color(0xFFF5F7F8)
val NtuElectricGreen = Color(0xFF52FF8A)

@Composable
fun NowTuneUpSplash(
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val progress = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) {
            progress.snapTo(1f)
        } else {
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            )
        }
    }

    Box(
        modifier = modifier.fillMaxSize().background(NtuGraphite),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.graphicsLayer {
                alpha = 0.35f + progress.value * 0.65f
                val scale = 0.88f + progress.value * 0.12f
                scaleX = scale
                scaleY = scale
            },
        ) {
            NowTuneUpLogoMark(
                sweepProgress = progress.value,
                modifier = Modifier.size(156.dp),
            )
            Text(
                text = "NOWTUNEUP",
                color = NtuWhite,
                fontSize = 21.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.4.sp,
            )
            Text(
                text = "SMART OBD • PERFORMANCE TELEMETRY",
                color = NtuWhite.copy(alpha = 0.56f),
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.1.sp,
            )
        }
    }
}

@Composable
fun NowTuneUpLogoMark(
    modifier: Modifier = Modifier,
    sweepProgress: Float = 1f,
) {
    Canvas(modifier) {
        val stroke = size.minDimension * 0.065f
        val center = center
        val radius = size.minDimension * 0.40f
        val topLeft = Offset(center.x - radius, center.y - radius)
        val arcSize = Size(radius * 2f, radius * 2f)
        val progress = sweepProgress.coerceIn(0f, 1f)

        drawArc(
            color = NtuWhite.copy(alpha = 0.18f),
            startAngle = 145f,
            sweepAngle = 250f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawArc(
            color = NtuElectricGreen,
            startAngle = 145f,
            sweepAngle = 250f * progress,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )

        val nLeftX = center.x - radius * 0.48f
        val nRightX = center.x + radius * 0.48f
        val nTopY = center.y - radius * 0.42f
        val nBottomY = center.y + radius * 0.42f
        val nStroke = stroke * 1.22f
        drawLine(NtuWhite, Offset(nLeftX, nBottomY), Offset(nLeftX, nTopY), nStroke, StrokeCap.Square)
        drawLine(NtuWhite, Offset(nLeftX, nTopY), Offset(nRightX, nBottomY), nStroke, StrokeCap.Square)
        drawLine(NtuWhite, Offset(nRightX, nBottomY), Offset(nRightX, nTopY), nStroke, StrokeCap.Square)

        val needleDegrees = 145f + 250f * progress
        val needleRadians = Math.toRadians(needleDegrees.toDouble())
        val needleLength = radius * 0.73f
        val needleEnd = Offset(
            center.x + cos(needleRadians).toFloat() * needleLength,
            center.y + sin(needleRadians).toFloat() * needleLength,
        )
        drawLine(
            color = NtuElectricGreen,
            start = center,
            end = needleEnd,
            strokeWidth = stroke * 0.50f,
            cap = StrokeCap.Round,
        )
        drawCircle(NtuGraphite, radius = stroke * 0.75f, center = center)
        drawCircle(NtuElectricGreen, radius = stroke * 0.42f, center = center)
    }
}

@Composable
fun connectionPulseAlpha(active: Boolean, reduceMotion: Boolean): Float {
    val target = if (active) 1f else 0.72f
    val value by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(if (reduceMotion) 0 else NtuMotion.Standard),
        label = "connection state alpha",
    )
    return value
}
