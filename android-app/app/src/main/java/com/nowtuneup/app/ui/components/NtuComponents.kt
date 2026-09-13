package com.nowtuneup.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import com.nowtuneup.app.ui.motion.LocalNtuReduceMotion
import com.nowtuneup.app.ui.motion.NtuMotion
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nowtuneup.app.ui.motion.NowTuneUpLogoMark

@Composable
fun NtuPanel(
    modifier: Modifier = Modifier,
    colors: CardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    shape: Shape = MaterialTheme.shapes.large,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
    if (onClick != null) {
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val scale by animateFloatAsState(if (pressed) 0.985f else 1f,
            tween(if (LocalNtuReduceMotion.current) 0 else NtuMotion.Quick), label = "panel press")
        Card(onClick = onClick, modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale },
            shape = shape, colors = colors, border = border, interactionSource = interaction, content = content)
        return
    }
    Card(
        modifier = modifier,
        shape = shape,
        colors = colors,
        border = border,
        content = content,
    )
}

@Composable
fun NtuScreenHeader(title: String, detail: String, modifier: Modifier = Modifier, eyebrow: String = "NOWTUNEUP") {
    val compact = LocalConfiguration.current.screenHeightDp < 480
    NtuPanel(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.09f), Color.Transparent,
            ))).padding(if (compact) 12.dp else 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                if (!compact) Text(eyebrow, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(title, style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (compact) 2 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis)
            }
            NowTuneUpLogoMark(Modifier.size(48.dp), foreground = MaterialTheme.colorScheme.onSurface,
                accent = MaterialTheme.colorScheme.primary, background = MaterialTheme.colorScheme.surface)
        }
    }
}

@Composable
fun NtuEmptyState(title: String, detail: String, icon: ImageVector, action: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    NtuPanel(modifier.widthIn(max = 520.dp).fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(icon, null, Modifier.padding(16.dp).size(32.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onAction, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(action, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
