package com.nowtuneup.app.ui.dashboard.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nowtuneup.app.data.dashboard.DashboardCodec
import com.nowtuneup.app.data.dashboard.DashboardDefaults
import com.nowtuneup.app.domain.model.BezelFinish
import com.nowtuneup.app.domain.model.ColorConfig
import com.nowtuneup.app.domain.model.DashboardConfig
import com.nowtuneup.app.domain.model.DashboardLayout
import com.nowtuneup.app.domain.model.DashboardMode
import com.nowtuneup.app.domain.model.DashboardWidgetConfig
import com.nowtuneup.app.domain.model.DashboardWidgetType
import com.nowtuneup.app.domain.model.DigitalRingColorPreset
import com.nowtuneup.app.domain.model.DigitalRingConfig
import com.nowtuneup.app.domain.model.DisplayUnit
import com.nowtuneup.app.domain.model.GaugeSmoothing
import com.nowtuneup.app.domain.model.GaugeStyle
import com.nowtuneup.app.domain.model.ThemeConfig
import com.nowtuneup.app.domain.model.VehicleReading
import com.nowtuneup.app.domain.model.WarningThreshold
import com.nowtuneup.app.domain.model.digitalRingPreset
import com.nowtuneup.app.domain.model.premiumGaugePreset
import com.nowtuneup.app.domain.model.resolvedGaugePreset
import com.nowtuneup.app.domain.model.userVisibleGaugeStyles
import com.nowtuneup.app.ui.dashboard.components.DashboardWidgetView
import kotlin.math.cos
import kotlin.math.sin

private enum class EditorOrientation { PORTRAIT, LANDSCAPE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardEditor(
    config: DashboardConfig,
    drivingMode: Boolean,
    onSave: (DashboardConfig) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    var draft by remember(config) { mutableStateOf(config) }
    var orientation by remember { mutableStateOf(EditorOrientation.PORTRAIT) }
    var selectedWidgetId by remember { mutableStateOf<String?>(null) }
    var draggingWidgetId by remember { mutableStateOf<String?>(null) }
    var showThemeEditor by remember { mutableStateOf(false) }
    var transferMessage by remember { mutableStateOf<String?>(null) }
    val activeLayout = draft.layout(orientation)

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                    it.write(DashboardCodec.export(draft))
                } ?: error("Unable to open export file")
            }.onSuccess {
                transferMessage = "Dashboard exported with portrait and landscape layouts."
            }.onFailure {
                transferMessage = "Export failed: ${it.message ?: "Unknown error"}"
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Unable to read import file")
                DashboardCodec.import(json).getOrThrow()
            }.onSuccess {
                draft = it.copy(id = config.id, isDefault = false)
                selectedWidgetId = null
                transferMessage = "Dashboard imported. Review both orientations before saving."
            }.onFailure {
                transferMessage = "Import failed: ${it.message ?: "Invalid dashboard file"}"
            }
        }
    }

    if (drivingMode) {
        AlertDialog(
            onDismissRequest = onCancel,
            confirmButton = { TextButton(onClick = onCancel) { Text("OK") } },
            title = { Text("Park before editing") },
            text = { Text("Dashboard editing is locked while Driving Mode is active.") },
        )
        return
    }

    selectedWidgetId?.let { id ->
        activeLayout.widgets.firstOrNull { it.id == id }?.let { widget ->
            WidgetConfigurationSheet(
                widget = widget,
                columns = activeLayout.columns,
                onDismiss = { selectedWidgetId = null },
                onSave = { changed ->
                    draft = draft.updateWidget(orientation, changed)
                    selectedWidgetId = null
                },
            )
        }
    }

    if (showThemeEditor) {
        DashboardThemeSheet(
            initial = draft.dashboardTheme(orientation),
            onDismiss = { showThemeEditor = false },
            onApply = {
                draft = draft.applyDashboardTheme(it)
                showThemeEditor = false
            },
        )
    }

    transferMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { transferMessage = null },
            confirmButton = { TextButton(onClick = { transferMessage = null }) { Text("OK") } },
            title = { Text("Dashboard file") },
            text = { Text(message) },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Premium dashboard editor") },
                navigationIcon = { TextButton(onClick = onCancel) { Text("Cancel") } },
                actions = { TextButton(onClick = { onSave(draft) }) { Text("Save") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SectionTitle("Dashboard", "Name and global presentation settings.")
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { draft = draft.copy(name = it.take(40)) },
                    label = { Text("Dashboard name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            item {
                SectionTitle("Layout", "Portrait and landscape keep independent columns, order and sizes.")
                ScrollableChips {
                    EditorOrientation.entries.forEach { item ->
                        FilterChip(
                            selected = orientation == item,
                            onClick = {
                                orientation = item
                                selectedWidgetId = null
                            },
                            label = { Text(item.label()) },
                        )
                    }
                }
                Text("Editing ${orientation.label()}: ${activeLayout.widgets.size} widgets, ${activeLayout.columns} columns")
                Text("Columns: ${activeLayout.columns}")
                Slider(
                    value = activeLayout.columns.toFloat(),
                    onValueChange = { value ->
                        val columns = value.toInt().coerceIn(1, 6)
                        draft = draft.withLayout(
                            orientation,
                            activeLayout.copy(
                                columns = columns,
                                widgets = activeLayout.widgets.map { it.copy(columnSpan = it.columnSpan.coerceAtMost(columns)) },
                            ),
                        )
                    },
                    valueRange = 1f..6f,
                    steps = 4,
                )
                OutlinedButton(
                    onClick = {
                        draft = draft.copyOtherLayoutTo(orientation)
                        selectedWidgetId = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (orientation == EditorOrientation.PORTRAIT) "Copy Landscape to Portrait" else "Copy Portrait to Landscape")
                }
            }
            item {
                SectionTitle("Display mode", "Apply a shared widget type base without removing individual colors.")
                ScrollableChips {
                    DashboardMode.entries.forEach { mode ->
                        FilterChip(
                            selected = draft.mode == mode,
                            onClick = { draft = draft.applyMode(mode) },
                            label = { Text(mode.label()) },
                        )
                    }
                }
            }
            item {
                SectionTitle("Automotive theme", "Colored themes replace the old grayscale-only choices.")
                ScrollableChips {
                    DashboardDefaults.themes.forEach { theme ->
                        FilterChip(
                            selected = false,
                            onClick = { draft = draft.applyDashboardTheme(theme) },
                            label = { Text(theme.name) },
                        )
                    }
                    OutlinedButton(onClick = { showThemeEditor = true }) { Text("Custom colors") }
                }
            }
            item {
                SectionTitle(
                    "${orientation.label()} widgets",
                    "Press and hold to reorder. Configure opens a live gauge preview and grouped controls.",
                )
            }
            itemsIndexed(
                items = activeLayout.widgets,
                key = { _, widget -> "${orientation.name}-${widget.id}" },
            ) { index, widget ->
                var dragDistance by remember(orientation, widget.id) { mutableFloatStateOf(0f) }
                WidgetEditorCard(
                    widget = widget,
                    index = index,
                    total = activeLayout.widgets.size,
                    dragging = draggingWidgetId == widget.id,
                    modifier = Modifier.pointerInput(orientation, widget.id, index, activeLayout.widgets.size) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggingWidgetId = widget.id
                                dragDistance = 0f
                            },
                            onDragCancel = {
                                draggingWidgetId = null
                                dragDistance = 0f
                            },
                            onDragEnd = {
                                draggingWidgetId = null
                                dragDistance = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragDistance += amount.y
                                val threshold = 56.dp.toPx()
                                when {
                                    dragDistance > threshold && index < activeLayout.widgets.lastIndex -> {
                                        draft = draft.moveWidget(orientation, index, index + 1)
                                        dragDistance = 0f
                                    }
                                    dragDistance < -threshold && index > 0 -> {
                                        draft = draft.moveWidget(orientation, index, index - 1)
                                        dragDistance = 0f
                                    }
                                }
                            },
                        )
                    },
                    onEdit = { selectedWidgetId = widget.id },
                    onMove = { draft = draft.moveWidget(orientation, index, it) },
                    onRemove = {
                        draft = draft.withLayout(
                            orientation,
                            activeLayout.copy(widgets = activeLayout.widgets.filterNot { item -> item.id == widget.id }),
                        )
                    },
                )
            }
            item {
                Button(
                    onClick = {
                        val source = DashboardDefaults.presets
                            .flatMap { it.portrait.widgets }
                            .firstOrNull { candidate -> activeLayout.widgets.none { it.pid == candidate.pid } }
                        if (source != null) {
                            draft = draft.withLayout(
                                orientation,
                                activeLayout.copy(
                                    widgets = activeLayout.widgets + source.copy(
                                        id = "${source.id}-${orientation.name.lowercase()}-${System.currentTimeMillis()}",
                                        columnSpan = source.columnSpan.coerceAtMost(activeLayout.columns),
                                    ),
                                ),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Add available widget") }
            }
            item {
                SectionTitle("Import and export", "JSON keeps both orientations, premium styles, colors and thresholds.")
                OutlinedButton(
                    onClick = {
                        val safeName = draft.name.ifBlank { "NowTuneUp-dashboard" }
                            .replace(Regex("[^A-Za-z0-9._-]"), "-")
                        exportLauncher.launch("$safeName.json")
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Export JSON") }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Import JSON") }
            }
            item {
                OutlinedButton(
                    onClick = {
                        val default = DashboardDefaults.presets.first()
                        draft = default.copy(id = config.id, name = config.name)
                        orientation = EditorOrientation.PORTRAIT
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Reset dashboard") }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, detail: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WidgetEditorCard(
    widget: DashboardWidgetConfig,
    index: Int,
    total: Int,
    dragging: Boolean,
    modifier: Modifier = Modifier,
    onEdit: () -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    val preset = widget.resolvedGaugePreset()
    Card(
        onClick = onEdit,
        modifier = modifier.fillMaxWidth().alpha(if (dragging) 0.58f else 1f),
        colors = CardDefaults.cardColors(containerColor = Color(widget.colors.background)),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (widget.type in setOf(DashboardWidgetType.ANALOG, DashboardWidgetType.MINI_GAUGE)) {
                MiniGaugePreview(widget.gaugeStyle, Modifier.size(72.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(widget.title, color = Color(preset.label), fontWeight = FontWeight.Bold)
                Text(
                    "${widget.type.label()} · ${widget.columnSpan}×${widget.rowSpan} · ${widget.unit.label()}",
                    color = Color(preset.label).copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { onMove(index - 1) }, enabled = index > 0) { Text("↑") }
                    TextButton(onClick = { onMove(index + 1) }, enabled = index < total - 1) { Text("↓") }
                    TextButton(onClick = onEdit) { Text("Configure") }
                    TextButton(onClick = onRemove) { Text("Remove") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetConfigurationSheet(
    widget: DashboardWidgetConfig,
    columns: Int,
    onDismiss: () -> Unit,
    onSave: (DashboardWidgetConfig) -> Unit,
) {
    val initialRing = widget.digitalRing ?: digitalRingPreset(DigitalRingColorPreset.AMBER)
    val initialPreset = widget.resolvedGaugePreset()
    var draft by remember(widget.id) { mutableStateOf(widget) }
    var warningLow by remember(widget.id) { mutableStateOf(widget.threshold.warningLow.text()) }
    var warningHigh by remember(widget.id) { mutableStateOf(widget.threshold.warningHigh.text()) }
    var criticalLow by remember(widget.id) { mutableStateOf(widget.threshold.criticalLow.text()) }
    var criticalHigh by remember(widget.id) { mutableStateOf(widget.threshold.criticalHigh.text()) }
    var valueColor by remember(widget.id) { mutableStateOf(initialPreset.value.hex()) }
    var labelColor by remember(widget.id) { mutableStateOf(initialPreset.label.hex()) }
    var backgroundColor by remember(widget.id) { mutableStateOf(initialPreset.face.hex()) }
    var borderColor by remember(widget.id) { mutableStateOf(initialPreset.bezel.hex()) }
    var tickColor by remember(widget.id) { mutableStateOf(initialPreset.tick.hex()) }
    var needleColor by remember(widget.id) { mutableStateOf(initialPreset.needle.hex()) }
    var needleHighlightColor by remember(widget.id) { mutableStateOf(initialPreset.needleHighlight.hex()) }
    var glowColor by remember(widget.id) { mutableStateOf(initialPreset.glow.hex()) }
    var warningColor by remember(widget.id) { mutableStateOf(initialPreset.warning.hex()) }
    var criticalColor by remember(widget.id) { mutableStateOf(initialPreset.critical.hex()) }
    var ringPreset by remember(widget.id) { mutableStateOf(initialRing.preset) }
    var segmentCount by remember(widget.id) { mutableFloatStateOf(initialRing.segmentCount.toFloat()) }
    var showScaleLabels by remember(widget.id) { mutableStateOf(initialRing.showScaleLabels) }
    var digitColor by remember(widget.id) { mutableStateOf(initialRing.digitColor.hex()) }
    var activeSegmentColor by remember(widget.id) { mutableStateOf(initialRing.activeSegmentColor.hex()) }
    var inactiveSegmentColor by remember(widget.id) { mutableStateOf(initialRing.inactiveSegmentColor.hex()) }
    var scaleColor by remember(widget.id) { mutableStateOf(initialRing.scaleColor.hex()) }
    var titleColor by remember(widget.id) { mutableStateOf(initialRing.titleColor.hex()) }
    var bezelColor by remember(widget.id) { mutableStateOf(initialRing.bezelColor.hex()) }

    fun applyAnalogPreset(style: GaugeStyle) {
        val selected = premiumGaugePreset(style)
        draft = draft.copy(gaugeStyle = style, bezelFinish = selected.bezelFinish)
        valueColor = selected.value.hex()
        labelColor = selected.label.hex()
        backgroundColor = selected.face.hex()
        borderColor = selected.bezel.hex()
        tickColor = selected.tick.hex()
        needleColor = selected.needle.hex()
        needleHighlightColor = selected.needleHighlight.hex()
        glowColor = selected.glow.hex()
        warningColor = selected.warning.hex()
        criticalColor = selected.critical.hex()
    }

    fun applyRingPreset(preset: DigitalRingColorPreset) {
        val selected = digitalRingPreset(preset, segmentCount.toInt())
        ringPreset = preset
        digitColor = selected.digitColor.hex()
        activeSegmentColor = selected.activeSegmentColor.hex()
        inactiveSegmentColor = selected.inactiveSegmentColor.hex()
        scaleColor = selected.scaleColor.hex()
        titleColor = selected.titleColor.hex()
        bezelColor = selected.bezelColor.hex()
        valueColor = selected.digitColor.hex()
        labelColor = selected.scaleColor.hex()
        backgroundColor = "#FF000000"
        borderColor = selected.bezelColor.hex()
    }

    val previewColors = draft.colors.copy(
        value = valueColor.argbOr(widget.colors.value),
        label = labelColor.argbOr(widget.colors.label),
        background = backgroundColor.argbOr(widget.colors.background),
        border = borderColor.argbOr(widget.colors.border),
        warning = warningColor.argbOr(widget.colors.warning),
        critical = criticalColor.argbOr(widget.colors.critical),
        face = backgroundColor.argbOr(widget.colors.background),
        bezel = borderColor.argbOr(widget.colors.border),
        tick = tickColor.argbOr(widget.colors.label),
        needle = needleColor.argbOr(widget.colors.value),
        needleHighlight = needleHighlightColor.argbOr(0xFFFFFFFF),
        glow = glowColor.argbOr(widget.colors.value),
    )
    val previewWidget = draft.copy(colors = previewColors, rowSpan = 2, columnSpan = 1)
    val previewReading = VehicleReading(
        pid = previewWidget.pid,
        name = previewWidget.title,
        value = 62.0,
        unit = previewWidget.unit.name,
        supported = true,
        minimum = 0.0,
        maximum = 100.0,
    )

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Widget configuration", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(previewColors.background)),
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth(),
            ) {
                DashboardWidgetView(previewWidget, previewReading, reduceMotion = false, dtcCount = 0)
            }

            SheetHeading("Widget")
            OutlinedTextField(
                value = draft.title,
                onValueChange = { draft = draft.copy(title = it.take(32)) },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            ScrollableChips {
                DashboardWidgetType.entries.forEach { type ->
                    FilterChip(
                        selected = draft.type == type,
                        onClick = {
                            draft = if (type == DashboardWidgetType.DIGITAL_RING) {
                                draft.copy(type = type, digitalRing = draft.digitalRing ?: initialRing)
                            } else draft.copy(type = type)
                        },
                        label = { Text(type.label()) },
                    )
                }
            }

            if (draft.type == DashboardWidgetType.ANALOG || draft.type == DashboardWidgetType.MINI_GAUGE) {
                HorizontalDivider()
                SheetHeading("Gauge style")
                Text("Choose a colored premium preset. Changes remain temporary until Apply is pressed.")
                GaugePresetGallery(selected = draft.gaugeStyle, onSelect = ::applyAnalogPreset)

                SheetHeading("Bezel")
                ScrollableChips {
                    BezelFinish.entries.forEach { finish ->
                        FilterChip(
                            selected = (draft.bezelFinish ?: initialPreset.bezelFinish) == finish,
                            onClick = { draft = draft.copy(bezelFinish = finish) },
                            label = { Text(finish.label()) },
                        )
                    }
                }
                SheetHeading("Needle smoothing")
                ScrollableChips {
                    GaugeSmoothing.entries.forEach { smoothing ->
                        FilterChip(
                            selected = (draft.gaugeSmoothing ?: GaugeSmoothing.BALANCED) == smoothing,
                            onClick = { draft = draft.copy(gaugeSmoothing = smoothing) },
                            label = { Text(smoothing.label()) },
                        )
                    }
                }
                FilterChip(
                    selected = draft.showPeakMarker,
                    onClick = { draft = draft.copy(showPeakMarker = !draft.showPeakMarker) },
                    label = { Text(if (draft.showPeakMarker) "Peak marker shown" else "Peak marker hidden") },
                )
            }

            if (draft.type == DashboardWidgetType.DIGITAL_RING) {
                HorizontalDivider()
                SheetHeading("Digital Ring Gauge")
                ScrollableChips {
                    DigitalRingColorPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = ringPreset == preset,
                            onClick = {
                                if (preset == DigitalRingColorPreset.CUSTOM) ringPreset = preset else applyRingPreset(preset)
                            },
                            label = { Text(preset.label()) },
                        )
                    }
                }
                Text("Segments: ${segmentCount.toInt()}")
                Slider(
                    value = segmentCount,
                    onValueChange = { segmentCount = it.coerceIn(12f, 72f) },
                    valueRange = 12f..72f,
                    steps = 59,
                )
                FilterChip(
                    selected = showScaleLabels,
                    onClick = { showScaleLabels = !showScaleLabels },
                    label = { Text(if (showScaleLabels) "Scale labels shown" else "Scale labels hidden") },
                )
                ColorField("Center digits", digitColor) { digitColor = it; ringPreset = DigitalRingColorPreset.CUSTOM }
                ColorField("Active segments", activeSegmentColor) { activeSegmentColor = it; ringPreset = DigitalRingColorPreset.CUSTOM }
                ColorField("Inactive segments", inactiveSegmentColor) { inactiveSegmentColor = it; ringPreset = DigitalRingColorPreset.CUSTOM }
                ColorField("Scale numbers", scaleColor) { scaleColor = it; ringPreset = DigitalRingColorPreset.CUSTOM }
                ColorField("Gauge title", titleColor) { titleColor = it; ringPreset = DigitalRingColorPreset.CUSTOM }
                ColorField("Bezel", bezelColor) { bezelColor = it; ringPreset = DigitalRingColorPreset.CUSTOM }
            }

            HorizontalDivider()
            SheetHeading("Scale")
            ScrollableChips {
                DisplayUnit.entries.forEach { unit ->
                    FilterChip(
                        selected = draft.unit == unit,
                        onClick = { draft = draft.copy(unit = unit) },
                        label = { Text(unit.label().ifBlank { "None" }) },
                    )
                }
            }
            Text("Width: ${draft.columnSpan.coerceIn(1, columns)} of $columns columns")
            Slider(
                value = draft.columnSpan.coerceIn(1, columns).toFloat(),
                onValueChange = { draft = draft.copy(columnSpan = it.toInt().coerceIn(1, columns)) },
                valueRange = 1f..columns.toFloat(),
                steps = (columns - 2).coerceAtLeast(0),
            )
            Text("Height: ${draft.rowSpan.coerceIn(1, 4)} rows")
            Slider(
                value = draft.rowSpan.coerceIn(1, 4).toFloat(),
                onValueChange = { draft = draft.copy(rowSpan = it.toInt().coerceIn(1, 4)) },
                valueRange = 1f..4f,
                steps = 2,
            )
            Text("Decimals: ${draft.decimals}")
            Slider(
                value = draft.decimals.toFloat(),
                onValueChange = { draft = draft.copy(decimals = it.toInt().coerceIn(0, 3)) },
                valueRange = 0f..3f,
                steps = 2,
            )
            Text("Digital value size: ${draft.valueSize}")
            Slider(
                value = draft.valueSize.toFloat(),
                onValueChange = { draft = draft.copy(valueSize = it.toInt().coerceIn(20, 80)) },
                valueRange = 20f..80f,
                steps = 11,
            )

            HorizontalDivider()
            SheetHeading("Thresholds")
            ThresholdRow("Warning low", warningLow) { warningLow = it }
            ThresholdRow("Warning high", warningHigh) { warningHigh = it }
            ThresholdRow("Critical low", criticalLow) { criticalLow = it }
            ThresholdRow("Critical high", criticalHigh) { criticalHigh = it }

            HorizontalDivider()
            SheetHeading("Colors")
            Text("Use #RRGGBB or #AARRGGBB. Invalid values are not saved.")
            ColorField("Digital value", valueColor) { valueColor = it; draft = draft.copy(gaugeStyle = GaugeStyle.CUSTOM) }
            ColorField("Label", labelColor) { labelColor = it; draft = draft.copy(gaugeStyle = GaugeStyle.CUSTOM) }
            ColorField("Face / background", backgroundColor) { backgroundColor = it; draft = draft.copy(gaugeStyle = GaugeStyle.CUSTOM) }
            ColorField("Bezel / border", borderColor) { borderColor = it; draft = draft.copy(gaugeStyle = GaugeStyle.CUSTOM) }
            if (draft.type in setOf(DashboardWidgetType.ANALOG, DashboardWidgetType.MINI_GAUGE)) {
                ColorField("Ticks", tickColor) { tickColor = it; draft = draft.copy(gaugeStyle = GaugeStyle.CUSTOM) }
                ColorField("Needle", needleColor) { needleColor = it; draft = draft.copy(gaugeStyle = GaugeStyle.CUSTOM) }
                ColorField("Needle highlight", needleHighlightColor) { needleHighlightColor = it; draft = draft.copy(gaugeStyle = GaugeStyle.CUSTOM) }
                ColorField("Glow", glowColor) { glowColor = it; draft = draft.copy(gaugeStyle = GaugeStyle.CUSTOM) }
            }
            ColorField("Warning", warningColor) { warningColor = it }
            ColorField("Critical", criticalColor) { criticalColor = it }

            HorizontalDivider()
            SheetHeading("Advanced")
            Text("The gauge animation interpolates between real ECU readings and does not increase OBD polling frequency.")

            Button(
                onClick = {
                    val currentRing = draft.digitalRing ?: initialRing
                    val ring = if (draft.type == DashboardWidgetType.DIGITAL_RING) {
                        DigitalRingConfig(
                            preset = ringPreset,
                            segmentCount = segmentCount.toInt().coerceIn(12, 72),
                            digitColor = digitColor.argbOr(currentRing.digitColor),
                            activeSegmentColor = activeSegmentColor.argbOr(currentRing.activeSegmentColor),
                            inactiveSegmentColor = inactiveSegmentColor.argbOr(currentRing.inactiveSegmentColor),
                            scaleColor = scaleColor.argbOr(currentRing.scaleColor),
                            titleColor = titleColor.argbOr(currentRing.titleColor),
                            bezelColor = bezelColor.argbOr(currentRing.bezelColor),
                            showScaleLabels = showScaleLabels,
                        )
                    } else draft.digitalRing
                    onSave(
                        draft.copy(
                            threshold = WarningThreshold(
                                warningLow = warningLow.toDoubleOrNull(),
                                warningHigh = warningHigh.toDoubleOrNull(),
                                criticalLow = criticalLow.toDoubleOrNull(),
                                criticalHigh = criticalHigh.toDoubleOrNull(),
                            ),
                            colors = ColorConfig(
                                value = valueColor.argbOr(widget.colors.value),
                                label = labelColor.argbOr(widget.colors.label),
                                background = backgroundColor.argbOr(widget.colors.background),
                                border = borderColor.argbOr(widget.colors.border),
                                warning = warningColor.argbOr(widget.colors.warning),
                                critical = criticalColor.argbOr(widget.colors.critical),
                                face = backgroundColor.argbOr(widget.colors.background),
                                bezel = borderColor.argbOr(widget.colors.border),
                                tick = tickColor.argbOr(widget.colors.label),
                                needle = needleColor.argbOr(widget.colors.value),
                                needleHighlight = needleHighlightColor.argbOr(0xFFFFFFFF),
                                glow = glowColor.argbOr(widget.colors.value),
                            ),
                            digitalRing = ring,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Apply widget changes") }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        }
    }
}

@Composable
private fun GaugePresetGallery(selected: GaugeStyle, onSelect: (GaugeStyle) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        userVisibleGaugeStyles.chunked(2).forEach { rowStyles ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowStyles.forEach { style ->
                    val preset = premiumGaugePreset(style)
                    Card(
                        onClick = { onSelect(style) },
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected == style) Color(preset.value).copy(alpha = 0.18f)
                            else MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            MiniGaugePreview(style, Modifier.size(96.dp))
                            Text(style.label(), fontWeight = if (selected == style) FontWeight.Bold else FontWeight.Medium)
                            Text(preset.bezelFinish.label(), style = MaterialTheme.typography.labelSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                ColorDot(Color(preset.value))
                                ColorDot(Color(preset.warning))
                                ColorDot(Color(preset.critical))
                            }
                        }
                    }
                }
                if (rowStyles.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MiniGaugePreview(style: GaugeStyle, modifier: Modifier = Modifier) {
    val preset = premiumGaugePreset(style)
    Canvas(modifier) {
        val radius = size.minDimension * 0.46f
        val c = center
        drawCircle(Color.Black.copy(alpha = 0.55f), radius * 1.04f, c + Offset(0f, 2f))
        drawCircle(
            brush = Brush.linearGradient(
                listOf(Color(0xFF111418), Color(preset.bezel), Color(0xFFB7BDC1), Color(0xFF1B1F23)),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            ),
            radius = radius,
            center = c,
        )
        drawCircle(Color(preset.face), radius * 0.82f, c)
        drawArc(
            color = Color(preset.tick),
            startAngle = 120f,
            sweepAngle = 300f,
            useCenter = false,
            style = Stroke(width = (radius * 0.07f).coerceAtLeast(2f), cap = StrokeCap.Round),
        )
        repeat(11) { index ->
            val angle = Math.toRadians((120f + index * 30f).toDouble())
            val outer = Offset(c.x + cos(angle).toFloat() * radius * 0.70f, c.y + sin(angle).toFloat() * radius * 0.70f)
            val inner = Offset(c.x + cos(angle).toFloat() * radius * 0.56f, c.y + sin(angle).toFloat() * radius * 0.56f)
            drawLine(Color(preset.tick), inner, outer, (radius * 0.035f).coerceAtLeast(1.5f), StrokeCap.Round)
        }
        val needleAngle = Math.toRadians(300.0)
        drawLine(
            Color(preset.needle),
            c,
            Offset(c.x + cos(needleAngle).toFloat() * radius * 0.62f, c.y + sin(needleAngle).toFloat() * radius * 0.62f),
            (radius * 0.07f).coerceAtLeast(2f),
            StrokeCap.Round,
        )
        drawCircle(Color(preset.needle), radius * 0.10f, c)
    }
}

@Composable
private fun ColorDot(color: Color) {
    Canvas(Modifier.size(11.dp)) { drawCircle(color) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardThemeSheet(
    initial: ThemeConfig,
    onDismiss: () -> Unit,
    onApply: (ThemeConfig) -> Unit,
) {
    var name by remember(initial) { mutableStateOf(initial.name) }
    var primary by remember(initial) { mutableStateOf(initial.primary.hex()) }
    var background by remember(initial) { mutableStateOf(initial.background.hex()) }
    var card by remember(initial) { mutableStateOf(initial.card.hex()) }
    var text by remember(initial) { mutableStateOf(initial.text.hex()) }
    var needle by remember(initial) { mutableStateOf(initial.gaugeNeedle.hex()) }
    var tick by remember(initial) { mutableStateOf(initial.gaugeTick.hex()) }
    var warning by remember(initial) { mutableStateOf(initial.warning.hex()) }
    var critical by remember(initial) { mutableStateOf(initial.critical.hex()) }
    var border by remember(initial) { mutableStateOf(initial.border.hex()) }
    val preview = initial.copy(
        name = name.ifBlank { "Custom" },
        primary = primary.argbOr(initial.primary),
        background = background.argbOr(initial.background),
        card = card.argbOr(initial.card),
        text = text.argbOr(initial.text),
        gaugeNeedle = needle.argbOr(initial.gaugeNeedle),
        gaugeTick = tick.argbOr(initial.gaugeTick),
        warning = warning.argbOr(initial.warning),
        critical = critical.argbOr(initial.critical),
        border = border.argbOr(initial.border),
    )
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Automotive theme editor", style = MaterialTheme.typography.headlineSmall)
            Card(colors = CardDefaults.cardColors(containerColor = Color(preview.card)), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Live preview", color = Color(preview.text), fontWeight = FontWeight.Bold)
                    Text("2,450 rpm", color = Color(preview.gaugeNeedle), style = MaterialTheme.typography.headlineMedium)
                }
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(30) },
                label = { Text("Theme name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            ColorField("Primary", primary) { primary = it }
            ColorField("Dashboard background", background) { background = it }
            ColorField("Widget background", card) { card = it }
            ColorField("Labels", text) { text = it }
            ColorField("Gauge needle / value", needle) { needle = it }
            ColorField("Gauge ticks", tick) { tick = it }
            ColorField("Warning", warning) { warning = it }
            ColorField("Critical", critical) { critical = it }
            ColorField("Border", border) { border = it }
            Button(onClick = { onApply(preview) }, modifier = Modifier.fillMaxWidth()) { Text("Apply theme") }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        }
    }
}

@Composable
private fun SheetHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun ScrollableChips(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun ThresholdRow(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            if (input.isBlank() || input.matches(Regex("-?\\d*(\\.\\d*)?"))) onValueChange(input)
        },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@Composable
private fun ColorField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onValueChange(input.uppercase().filter { it in "#0123456789ABCDEF" }.take(9)) },
        label = { Text("$label · #AARRGGBB") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        isError = value.argbOrNull() == null,
    )
}

private fun DashboardConfig.layout(orientation: EditorOrientation): DashboardLayout =
    if (orientation == EditorOrientation.PORTRAIT) portrait else landscape

private fun DashboardConfig.withLayout(orientation: EditorOrientation, layout: DashboardLayout): DashboardConfig =
    if (orientation == EditorOrientation.PORTRAIT) copy(portrait = layout) else copy(landscape = layout)

private fun DashboardConfig.updateWidget(
    orientation: EditorOrientation,
    changed: DashboardWidgetConfig,
): DashboardConfig {
    val layout = layout(orientation)
    return withLayout(orientation, layout.copy(widgets = layout.widgets.map { if (it.id == changed.id) changed else it }))
}

private fun DashboardConfig.moveWidget(orientation: EditorOrientation, from: Int, to: Int): DashboardConfig {
    val layout = layout(orientation)
    if (from !in layout.widgets.indices || to !in layout.widgets.indices || from == to) return this
    val reordered = layout.widgets.toMutableList().apply { add(to, removeAt(from)) }
    return withLayout(orientation, layout.copy(widgets = reordered))
}

private fun DashboardConfig.copyOtherLayoutTo(target: EditorOrientation): DashboardConfig {
    val source = if (target == EditorOrientation.PORTRAIT) landscape else portrait
    val destination = layout(target)
    return withLayout(
        target,
        source.copy(
            columns = destination.columns,
            widgets = source.widgets.map { it.copy(columnSpan = it.columnSpan.coerceAtMost(destination.columns)) },
        ),
    )
}

private fun DashboardConfig.applyMode(mode: DashboardMode): DashboardConfig {
    val type = when (mode) {
        DashboardMode.DIGITAL -> DashboardWidgetType.DIGITAL
        DashboardMode.ANALOG -> DashboardWidgetType.ANALOG
        DashboardMode.HYBRID -> null
    }
    fun apply(layout: DashboardLayout) = layout.copy(
        widgets = layout.widgets.map { widget -> type?.let { widget.copy(type = it) } ?: widget },
    )
    return copy(mode = mode, portrait = apply(portrait), landscape = apply(landscape))
}

private fun DashboardConfig.dashboardTheme(orientation: EditorOrientation): ThemeConfig {
    val colors = layout(orientation).widgets.firstOrNull()?.colors ?: ColorConfig()
    return ThemeConfig(
        name = "Custom dashboard",
        primary = colors.value,
        background = colors.background,
        card = colors.background,
        text = colors.label,
        gaugeNeedle = colors.needle ?: colors.value,
        gaugeTick = colors.tick ?: colors.label,
        warning = colors.warning,
        critical = colors.critical,
        border = colors.bezel ?: colors.border,
    )
}

private fun DashboardConfig.applyDashboardTheme(theme: ThemeConfig): DashboardConfig {
    fun themed(widget: DashboardWidgetConfig): DashboardWidgetConfig {
        val ring = widget.digitalRing?.copy(
            preset = DigitalRingColorPreset.CUSTOM,
            digitColor = theme.gaugeNeedle,
            activeSegmentColor = theme.gaugeNeedle,
            scaleColor = theme.gaugeTick,
            titleColor = theme.text,
            bezelColor = theme.border,
        )
        return widget.copy(
            colors = widget.colors.copy(
                value = theme.gaugeNeedle,
                label = theme.text,
                background = theme.card,
                border = theme.border,
                warning = theme.warning,
                critical = theme.critical,
                face = theme.card,
                bezel = theme.border,
                tick = theme.gaugeTick,
                needle = theme.gaugeNeedle,
                glow = theme.accent,
            ),
            digitalRing = ring,
        )
    }
    return copy(
        portrait = portrait.copy(widgets = portrait.widgets.map(::themed)),
        landscape = landscape.copy(widgets = landscape.widgets.map(::themed)),
    )
}

private fun EditorOrientation.label(): String = name.lowercase().replaceFirstChar(Char::uppercase)
private fun DashboardMode.label(): String = name.lowercase().replaceFirstChar(Char::uppercase)
private fun DashboardWidgetType.label(): String = name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)
private fun GaugeStyle.label(): String = name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)
private fun BezelFinish.label(): String = name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)
private fun GaugeSmoothing.label(): String = name.lowercase().replaceFirstChar(Char::uppercase)
private fun DigitalRingColorPreset.label(): String = name.lowercase().replaceFirstChar(Char::uppercase)
private fun DisplayUnit.label(): String = when (this) {
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

private fun Double?.text(): String = this?.toString().orEmpty()
private fun Long.hex(): String = "#%08X".format(this and 0xFFFFFFFFL)
private fun String.argbOr(fallback: Long): Long = argbOrNull() ?: fallback
private fun String.argbOrNull(): Long? = runCatching {
    val clean = trim().removePrefix("#")
    val normalized = when (clean.length) {
        6 -> "FF$clean"
        8 -> clean
        else -> error("Expected RRGGBB or AARRGGBB")
    }
    normalized.toULong(16).toLong()
}.getOrNull()
