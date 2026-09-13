package com.nowtuneup.app.ui.motion

import androidx.compose.animation.animateContentSize
import androidx.compose.runtime.staticCompositionLocalOf
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
    const val Splash = 750
}

val LocalNtuReduceMotion = staticCompositionLocalOf { false }

@Composable
fun Modifier.ntuAnimateContentSize(): Modifier =
    animateContentSize(tween(if (LocalNtuReduceMotion.current) 0 else NtuMotion.Standard))

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
                animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
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
    foreground: Color = NtuWhite,
    accent: Color = NtuElectricGreen,
    background: Color = NtuGraphite,
) {
    // Match the launcher vector in a 108-unit viewport, inside the adaptive icon safe zone.
    Canvas(modifier) {
        val unit = size.minDimension / 108f
        val origin = Offset((size.width - 108f * unit) / 2f, (size.height - 108f * unit) / 2f)
        fun point(x: Float, y: Float) = origin + Offset(x * unit, y * unit)
        val progress = sweepProgress.coerceIn(0f, 1f)
        drawArc(foreground.copy(alpha = 0.20f), 135f, 270f, false,
            point(24f, 24f), Size(60f * unit, 60f * unit), style = Stroke(4f * unit, cap = StrokeCap.Round))
        drawArc(accent, 135f, 270f * progress, false,
            point(24f, 24f), Size(60f * unit, 60f * unit), style = Stroke(4f * unit, cap = StrokeCap.Round))
        // A legible N monogram with an ascending telemetry slash, without an overlapping needle.
        drawLine(foreground, point(40f, 67f), point(40f, 41f), 6f * unit, StrokeCap.Square)
        drawLine(foreground, point(40f, 41f), point(66f, 67f), 6f * unit, StrokeCap.Square)
        drawLine(foreground, point(66f, 67f), point(66f, 41f), 6f * unit, StrokeCap.Square)
        drawLine(background, point(51f, 57f), point(70f, 38f), 7f * unit, StrokeCap.Square)
        drawLine(accent, point(51f, 57f), point(70f, 38f), 3.5f * unit, StrokeCap.Square)
        drawLine(accent, point(48f, 80f), point(60f, 80f), 3f * unit, StrokeCap.Round)
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
