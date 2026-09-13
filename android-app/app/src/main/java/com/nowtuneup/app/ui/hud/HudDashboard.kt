package com.nowtuneup.app.ui.hud

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nowtuneup.app.data.obd.pid.DerivedPids
import com.nowtuneup.app.domain.model.AlertSeverity
import com.nowtuneup.app.domain.model.ConnectionState
import com.nowtuneup.app.domain.model.DashboardAlert
import com.nowtuneup.app.domain.model.DashboardPreferences
import com.nowtuneup.app.domain.model.DisplayUnit
import com.nowtuneup.app.domain.model.HudColorPreset
import com.nowtuneup.app.domain.model.VehicleReading
import com.nowtuneup.app.util.DisplayReadingAdapter
import kotlinx.coroutines.delay

@Composable
fun HudDashboard(
    readings: List<VehicleReading>,
    preferences: DashboardPreferences,
    connectionState: ConnectionState,
    activeAlerts: List<DashboardAlert>,
    onExitHud: () -> Unit,
    onToggleMirror: () -> Unit,
    onToggleTouchLock: () -> Unit,
) {
    val hudColor = preferences.hudColorPreset.color()
    var controlsVisible by remember { mutableStateOf(false) }
    var burnInStep by remember { mutableIntStateOf(0) }

    LaunchedEffect(controlsVisible, preferences.controlsAutoHideSeconds, preferences.touchLock) {
        if (controlsVisible && !preferences.touchLock) {
            delay(preferences.controlsAutoHideSeconds * 1_000L)
            controlsVisible = false
        }
    }
    LaunchedEffect(preferences.hudBurnInProtection) {
        while (preferences.hudBurnInProtection) {
            delay(60_000L)
            burnInStep = (burnInStep + 1) % 5
        }
    }

    val movement = if (preferences.hudBurnInProtection) {
        listOf(0.dp to 0.dp, 4.dp to 0.dp, 0.dp to 3.dp, (-4).dp to 0.dp, 0.dp to (-3).dp)[burnInStep]
    } else {
        0.dp to 0.dp
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(preferences.touchLock) {
                detectTapGestures(
                    onTap = {
                        if (!preferences.touchLock) controlsVisible = !controlsVisible
                    },
                    onLongPress = { onToggleTouchLock() },
                )
            },
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .offset(movement.first, movement.second)
                .graphicsLayer { scaleX = if (preferences.hudMirror) -1f else 1f },
        ) {
            val landscape = maxWidth > maxHeight
            val speed = readings.reading(0x0D, DisplayUnit.KMH, preferences.staleAfterMillis)
            val rpm = readings.reading(0x0C, DisplayUnit.RPM, preferences.staleAfterMillis)
            val turbo = readings.reading(DerivedPids.TURBO_PRESSURE, DisplayUnit.PSI, preferences.staleAfterMillis)
            val statusText = when (connectionState) {
                ConnectionState.CONNECTED -> "LIVE"
                ConnectionState.CONNECTING, ConnectionState.INITIALIZING, ConnectionState.DEVICE_DETECTED -> "RECONNECTING"
                ConnectionState.ERROR -> "ERROR"
                else -> "DISCONNECTED"
            }

            if (landscape) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HudValue("SPEED", speed?.value, speed?.unit ?: "km/h", hudColor, 112)
                    HudValue("RPM", rpm?.value, "rpm", hudColor, 84)
                    HudValue("TURBO", turbo?.value, turbo?.unit ?: "PSI", hudColor, 84, decimals = 1)
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 26.dp),
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    HudValue("SPEED", speed?.value, speed?.unit ?: "km/h", hudColor, 118)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        HudValue("RPM", rpm?.value, "rpm", hudColor, 58)
                        HudValue("TURBO", turbo?.value, turbo?.unit ?: "PSI", hudColor, 58, decimals = 1)
                    }
                }
            }

            Surface(
                modifier = Modifier.align(Alignment.TopCenter),
                color = Color.Transparent,
            ) {
                Text(
                    text = statusText,
                    color = if (connectionState == ConnectionState.CONNECTED) hudColor else Color(0xFFFF5252),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }

        activeAlerts.firstOrNull()?.let { alert ->
            val alertColor = if (alert.severity == AlertSeverity.CRITICAL) Color.Red else Color(0xFFFFB300)
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                color = alertColor.copy(alpha = 0.92f),
            ) {
                Text(
                    text = "${alert.severity.name}: ${alert.title} ${"%.1f".format(alert.value)}",
                    color = Color.Black,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }

        if (preferences.touchLock) {
            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                color = Color.Black.copy(alpha = 0.7f),
                shape = CircleShape,
            ) {
                Text("ล็อกอยู่ · แตะค้างเพื่อปลดล็อก", color = hudColor, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
            }
        } else if (controlsVisible) {
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(8.dp),
                color = Color(0xDD101010),
                shape = CircleShape,
            ) {
                Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onToggleMirror) { Text(if (preferences.hudMirror) "ภาพปกติ" else "กลับภาพ", color = hudColor) }
                    TextButton(onClick = onToggleTouchLock) { Text("ล็อก", color = hudColor) }
                    TextButton(onClick = onExitHud) { Text("ออกจาก HUD", color = hudColor) }
                }
            }
        }
    }
}

@Composable
private fun HudValue(
    title: String,
    value: Double?,
    unit: String,
    color: Color,
    size: Int,
    decimals: Int = 0,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = color.copy(alpha = 0.75f), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(
            text = value?.let { "% .${decimals}f".format(it).trim() } ?: "--",
            color = color,
            fontSize = size.sp,
            lineHeight = size.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )
        Text(unit.uppercase(), color = color.copy(alpha = 0.82f), fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

private fun List<VehicleReading>.reading(pid: Int, unit: DisplayUnit, staleAfterMillis: Long): VehicleReading? {
    val reading = firstOrNull { it.pid == pid } ?: return null
    val fresh = reading.supported && reading.value != null && System.currentTimeMillis() - reading.updatedAt <= staleAfterMillis
    return if (fresh) DisplayReadingAdapter.reading(reading, unit) else null
}

private fun HudColorPreset.color(): Color = when (this) {
    HudColorPreset.GREEN -> Color(0xFF39FF14)
    HudColorPreset.AMBER -> Color(0xFFFFC400)
    HudColorPreset.CYAN -> Color(0xFF00D9FF)
    HudColorPreset.WHITE -> Color(0xFFF7FAFC)
    HudColorPreset.RED -> Color(0xFFFF3045)
}
