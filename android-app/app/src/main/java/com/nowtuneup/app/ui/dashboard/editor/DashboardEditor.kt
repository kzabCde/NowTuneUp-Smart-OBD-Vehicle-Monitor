package com.nowtuneup.app.ui.dashboard.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nowtuneup.app.data.dashboard.DashboardCodec
import com.nowtuneup.app.data.dashboard.DashboardDefaults
import com.nowtuneup.app.domain.model.ColorConfig
import com.nowtuneup.app.domain.model.DashboardConfig
import com.nowtuneup.app.domain.model.DashboardLayout
import com.nowtuneup.app.domain.model.DashboardMode
import com.nowtuneup.app.domain.model.DashboardWidgetConfig
import com.nowtuneup.app.domain.model.DashboardWidgetType
import com.nowtuneup.app.domain.model.DigitalRingColorPreset
import com.nowtuneup.app.domain.model.DigitalRingConfig
import com.nowtuneup.app.domain.model.DisplayUnit
import com.nowtuneup.app.domain.model.GaugeStyle
import com.nowtuneup.app.domain.model.ThemeConfig
import com.nowtuneup.app.domain.model.WarningThreshold
import com.nowtuneup.app.domain.model.digitalRingPreset

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
                title = { Text("Responsive dashboard editor") },
                navigationIcon = { TextButton(onClick = onCancel) { Text("Cancel") } },
                actions = { TextButton(onClick = { onSave(draft) }) { Text("Save") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { draft = draft.copy(name = it.take(40)) },
                    label = { Text("Dashboard name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            item {
                SectionTitle("Edit orientation", "Portrait and landscape keep independent columns, order and sizes.")
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
                SectionTitle("Display mode", "Apply a shared base style to both orientations.")
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
                SectionTitle("${orientation.label()} grid", "Use fewer columns on phones and more columns in landscape or tablets.")
                Text("Columns: ${activeLayout.columns}")
                Slider(
                    value = activeLayout.columns.toFloat(),
                    onValueChange = { value ->
                        val columns = value.toInt().coerceIn(1, 6)
                        draft = draft.withLayout(
                            orientation,
                            activeLayout.copy(
                                columns = columns,
                                widgets = activeLayout.widgets.map {
                                    it.copy(columnSpan = it.columnSpan.coerceAtMost(columns))
                                },
                            ),
                        )
                    },
                    valueRange = 1f..6f,
                    steps = 4,
                )
            }
            item {
                SectionTitle("Dashboard theme", "Theme changes are applied to portrait and landscape widgets.")
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
                    "Press and hold to reorder. Tap Configure to resize or change the gauge.",
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
                SectionTitle("Import and export", "JSON includes both orientations, colors, thresholds and Digital Ring settings.")
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
        Text(detail, style = MaterialTheme.typography.bodySmall)
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
    Card(
        onClick = onEdit,
        modifier = modifier.fillMaxWidth().alpha(if (dragging) 0.58f else 1f),
        colors = CardDefaults.cardColors(containerColor = Color(widget.colors.background)),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(widget.title, color = Color(widget.colors.label), fontWeight = FontWeight.Bold)
                    Text(
                        "${widget.type.label()} · ${widget.columnSpan}×${widget.rowSpan} · ${widget.unit.label()}",
                        color = Color(widget.colors.label),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text("☰", color = Color(widget.colors.value), style = MaterialTheme.typography.titleLarge)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = { onMove(index - 1) }, enabled = index > 0) { Text("↑") }
                TextButton(onClick = { onMove(index + 1) }, enabled = index < total - 1) { Text("↓") }
                TextButton(onClick = onEdit) { Text("Configure") }
                TextButton(onClick = onRemove) { Text("Remove") }
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
    var draft by remember(widget.id) { mutableStateOf(widget) }
    var warningLow by remember(widget.id) { mutableStateOf(widget.threshold.warningLow.text()) }
    var warningHigh by remember(widget.id) { mutableStateOf(widget.threshold.warningHigh.text()) }
    var criticalLow by remember(widget.id) { mutableStateOf(widget.threshold.criticalLow.text()) }
    var criticalHigh by remember(widget.id) { mutableStateOf(widget.threshold.criticalHigh.text()) }
    var valueColor by remember(widget.id) { mutableStateOf(widget.colors.value.hex()) }
    var labelColor by remember(widget.id) { mutableStateOf(widget.colors.label.hex()) }
    var backgroundColor by remember(widget.id) { mutableStateOf(widget.colors.background.hex()) }
    var borderColor by remember(widget.id) { mutableStateOf(widget.colors.border.hex()) }
    var warningColor by remember(widget.id) { mutableStateOf(widget.colors.warning.hex()) }
    var criticalColor by remember(widget.id) { mutableStateOf(widget.colors.critical.hex()) }
    var ringPreset by remember(widget.id) { mutableStateOf(initialRing.preset) }
    var segmentCount by remember(widget.id) { mutableFloatStateOf(initialRing.segmentCount.toFloat()) }
    var showScaleLabels by remember(widget.id) { mutableStateOf(initialRing.showScaleLabels) }
    var digitColor by remember(widget.id) { mutableStateOf(initialRing.digitColor.hex()) }
    var activeSegmentColor by remember(widget.id) { mutableStateOf(initialRing.activeSegmentColor.hex()) }
    var inactiveSegmentColor by remember(widget.id) { mutableStateOf(initialRing.inactiveSegmentColor.hex()) }
    var scaleColor by remember(widget.id) { mutableStateOf(initialRing.scaleColor.hex()) }
    var titleColor by remember(widget.id) { mutableStateOf(initialRing.titleColor.hex()) }
    var bezelColor by remember(widget.id) { mutableStateOf(initialRing.bezelColor.hex()) }

    fun applyPreset(preset: DigitalRingColorPreset) {
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

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Widget configuration", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = draft.title,
                onValueChange = { draft = draft.copy(title = it.take(32)) },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            SheetHeading("Widget type")
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
                SheetHeading("Analog gauge style")
                ScrollableChips {
                    GaugeStyle.entries.forEach { style ->
                        FilterChip(
                            selected = draft.gaugeStyle == style,
                            onClick = { draft = draft.copy(gaugeStyle = style) },
                            label = { Text(style.label()) },
                        )
                    }
                }
            }
            if (draft.type == DashboardWidgetType.DIGITAL_RING) {
                HorizontalDivider()
                SheetHeading("Digital Ring Gauge")
                ScrollableChips {
                    DigitalRingColorPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = ringPreset == preset,
                            onClick = {
                                if (preset == DigitalRingColorPreset.CUSTOM) ringPreset = preset else applyPreset(preset)
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
            SheetHeading("Unit")
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
            Text("Value text size: ${draft.valueSize}")
            Slider(
                value = draft.valueSize.toFloat(),
                onValueChange = { draft = draft.copy(valueSize = it.toInt().coerceIn(20, 80)) },
                valueRange = 20f..80f,
                steps = 11,
            )
            HorizontalDivider()
            SheetHeading("Warning thresholds")
            ThresholdRow("Warning low", warningLow) { warningLow = it }
            ThresholdRow("Warning high", warningHigh) { warningHigh = it }
            ThresholdRow("Critical low", criticalLow) { criticalLow = it }
            ThresholdRow("Critical high", criticalHigh) { criticalHigh = it }
            HorizontalDivider()
            SheetHeading("Widget colors")
            ColorField("Value / needle", valueColor) { valueColor = it }
            ColorField("Label / tick", labelColor) { labelColor = it }
            ColorField("Background", backgroundColor) { backgroundColor = it }
            ColorField("Border", borderColor) { borderColor = it }
            ColorField("Warning", warningColor) { warningColor = it }
            ColorField("Critical", criticalColor) { criticalColor = it }
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
            Text("Dashboard theme editor", style = MaterialTheme.typography.headlineSmall)
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
        onValueChange = { input ->
            onValueChange(input.uppercase().filter { it in "#0123456789ABCDEF" }.take(9))
        },
        label = { Text("$label · #AARRGGBB") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        isError = value.argbOrNull() == null,
    )
}

private fun DashboardConfig.layout(orientation: EditorOrientation): DashboardLayout =
    if (orientation == EditorOrientation.PORTRAIT) portrait else landscape

private fun DashboardConfig.withLayout(
    orientation: EditorOrientation,
    layout: DashboardLayout,
): DashboardConfig = if (orientation == EditorOrientation.PORTRAIT) copy(portrait = layout) else copy(landscape = layout)

private fun DashboardConfig.updateWidget(
    orientation: EditorOrientation,
    changed: DashboardWidgetConfig,
): DashboardConfig {
    val layout = layout(orientation)
    return withLayout(orientation, layout.copy(widgets = layout.widgets.map { if (it.id == changed.id) changed else it }))
}

private fun DashboardConfig.moveWidget(
    orientation: EditorOrientation,
    from: Int,
    to: Int,
): DashboardConfig {
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
        gaugeNeedle = colors.value,
        gaugeTick = colors.label,
        warning = colors.warning,
        critical = colors.critical,
        border = colors.border,
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
private fun GaugeStyle.label(): String = name.lowercase().replaceFirstChar(Char::uppercase)
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
