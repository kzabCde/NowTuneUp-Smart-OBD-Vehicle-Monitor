package com.nowtuneup.app.ui.dashboard.editor

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.nowtuneup.app.data.dashboard.DashboardCodec
import com.nowtuneup.app.data.dashboard.DashboardDefaults
import com.nowtuneup.app.domain.model.ColorConfig
import com.nowtuneup.app.domain.model.DashboardConfig
import com.nowtuneup.app.domain.model.DashboardLayout
import com.nowtuneup.app.domain.model.DashboardWidgetConfig
import com.nowtuneup.app.domain.model.DashboardWidgetType
import com.nowtuneup.app.domain.model.DisplayUnit
import com.nowtuneup.app.domain.model.GaugeStyle
import com.nowtuneup.app.domain.model.HUD_CANVAS_COLUMNS
import com.nowtuneup.app.domain.model.HUD_CANVAS_ROWS
import com.nowtuneup.app.domain.model.ThemeConfig
import com.nowtuneup.app.domain.model.VehicleReading
import com.nowtuneup.app.domain.model.WarningThreshold
import com.nowtuneup.app.domain.model.premiumGaugePreset
import com.nowtuneup.app.domain.model.userVisibleGaugeStyles
import com.nowtuneup.app.ui.components.NtuScreenHeader
import com.nowtuneup.app.ui.dashboard.components.DashboardWidgetView
import kotlin.math.roundToInt

private enum class EditorOrientation { PORTRAIT, LANDSCAPE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardEditor(
    config: DashboardConfig,
    drivingMode: Boolean,
    onSave: (DashboardConfig) -> Unit,
    onCancel: () -> Unit,
) {
    BackHandler(onBack = onCancel)
    val context = LocalContext.current
    var draft by remember(config) { mutableStateOf(config.copy(isDefault = false)) }
    var orientation by remember { mutableStateOf(EditorOrientation.PORTRAIT) }
    var selectedWidgetId by remember { mutableStateOf<String?>(null) }
    var showLibrary by remember { mutableStateOf(false) }
    var transferMessage by remember { mutableStateOf<String?>(null) }
    var undoStack by remember(config) { mutableStateOf(emptyList<DashboardConfig>()) }
    var redoStack by remember(config) { mutableStateOf(emptyList<DashboardConfig>()) }

    fun commit(next: DashboardConfig) {
        if (next == draft) return
        undoStack = (undoStack + draft).takeLast(40)
        redoStack = emptyList()
        draft = next
    }

    fun undo() {
        val previous = undoStack.lastOrNull() ?: return
        redoStack = (redoStack + draft).takeLast(40)
        undoStack = undoStack.dropLast(1)
        draft = previous
        selectedWidgetId = null
    }

    fun redo() {
        val next = redoStack.lastOrNull() ?: return
        undoStack = (undoStack + draft).takeLast(40)
        redoStack = redoStack.dropLast(1)
        draft = next
        selectedWidgetId = null
    }

    val activeLayout = draft.layout(orientation)

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                    it.write(DashboardCodec.export(draft))
                } ?: error("เปิดไฟล์ไม่ได้")
            }.onSuccess {
                transferMessage = "ส่งออก Custom HUD เรียบร้อยแล้ว"
            }.onFailure {
                transferMessage = "ส่งออกไม่สำเร็จ: ${it.message ?: "ไม่ทราบสาเหตุ"}"
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("อ่านไฟล์ไม่ได้")
                DashboardCodec.import(json).getOrThrow()
            }.onSuccess {
                commit(it.copy(id = config.id, isDefault = false))
                selectedWidgetId = null
                transferMessage = "นำเข้า Custom HUD แล้ว ตรวจทั้งแนวตั้งและแนวนอนก่อนบันทึก"
            }.onFailure {
                transferMessage = "นำเข้าไม่สำเร็จ: ${it.message ?: "ไฟล์ไม่ถูกต้อง"}"
            }
        }
    }

    if (drivingMode) {
        AlertDialog(
            onDismissRequest = onCancel,
            confirmButton = { TextButton(onClick = onCancel) { Text("ตกลง") } },
            title = { Text("จอดรถก่อนแก้ไข HUD") },
            text = { Text("Custom HUD ลากและปรับขนาดได้เฉพาะเมื่อปิดโหมดขับรถ") },
        )
        return
    }

    if (showLibrary) {
        WidgetLibrarySheet(
            layout = activeLayout,
            onDismiss = { showLibrary = false },
            onAdd = { source, type ->
                val placed = createWidgetFromSource(source, type, activeLayout)
                commit(draft.withLayout(orientation, activeLayout.copy(widgets = activeLayout.widgets + placed)))
                selectedWidgetId = placed.id
                showLibrary = false
            },
        )
    }

    selectedWidgetId?.let { id ->
        activeLayout.widgets.firstOrNull { it.id == id }?.let { widget ->
            WidgetPropertiesSheet(
                widget = widget,
                layout = activeLayout,
                onDismiss = { selectedWidgetId = null },
                onApply = { changed ->
                    commit(draft.updateWidget(orientation, changed))
                    selectedWidgetId = null
                },
                onDelete = {
                    commit(draft.withLayout(
                        orientation,
                        activeLayout.copy(widgets = activeLayout.widgets.filterNot { item -> item.id == widget.id }),
                    ))
                    selectedWidgetId = null
                },
                onDuplicate = {
                    val duplicate = widget.copy(
                        id = "${widget.id}-copy-${System.currentTimeMillis()}",
                        canvasX = (widget.canvasX + 1).coerceAtMost((activeLayout.columns - widget.canvasWidth).coerceAtLeast(0)),
                        canvasY = (widget.canvasY + 1).coerceAtMost((activeLayout.rows - widget.canvasHeight).coerceAtLeast(0)),
                        zIndex = (activeLayout.widgets.maxOfOrNull { it.zIndex } ?: 0) + 1,
                        locked = false,
                    )
                    commit(draft.withLayout(orientation, activeLayout.copy(widgets = activeLayout.widgets + duplicate)))
                    selectedWidgetId = duplicate.id
                },
            )
        }
    }

    transferMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { transferMessage = null },
            confirmButton = { TextButton(onClick = { transferMessage = null }) { Text("ตกลง") } },
            title = { Text("Custom HUD profile") },
            text = { Text(message) },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Custom HUD Studio") },
                navigationIcon = { TextButton(onClick = onCancel) { Text("ยกเลิก") } },
                actions = {
                    TextButton(onClick = ::undo, enabled = undoStack.isNotEmpty()) { Text("ย้อนกลับ") }
                    TextButton(onClick = ::redo, enabled = redoStack.isNotEmpty()) { Text("ทำซ้ำ") }
                    TextButton(
                        onClick = { onSave(draft.copy(isDefault = false)) },
                        enabled = draft.name.isNotBlank(),
                    ) { Text("บันทึก") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                NtuScreenHeader(
                    "ออกแบบ HUD ของคุณ",
                    "เลือก Widget แล้วลาก วาง ปรับขนาด ซ้อนชั้น และบันทึกเป็นโปรไฟล์ได้อิสระ",
                    eyebrow = "CUSTOM HUD · 12×24 CANVAS",
                )
            }

            item {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { commit(draft.copy(name = it.take(40))) },
                    label = { Text("ชื่อโปรไฟล์") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            item {
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
                    OutlinedButton(
                        onClick = {
                            commit(draft.copyOtherLayoutTo(orientation))
                            selectedWidgetId = null
                        },
                    ) { Text("คัดลอกจากอีกแนว") }
                }
            }

            item {
                HudCanvasEditor(
                    layout = activeLayout,
                    orientation = orientation,
                    selectedWidgetId = selectedWidgetId,
                    onSelect = { selectedWidgetId = it },
                    onMove = { id, dx, dy ->
                        val widget = activeLayout.widgets.firstOrNull { it.id == id } ?: return@HudCanvasEditor
                        if (!widget.locked) {
                            val changed = widget.copy(
                                canvasX = (widget.canvasX + dx).coerceIn(0, (activeLayout.columns - widget.canvasWidth).coerceAtLeast(0)),
                                canvasY = (widget.canvasY + dy).coerceIn(0, (activeLayout.rows - widget.canvasHeight).coerceAtLeast(0)),
                            )
                            commit(draft.updateWidget(orientation, changed))
                        }
                    },
                    onResize = { id, dw, dh ->
                        val widget = activeLayout.widgets.firstOrNull { it.id == id } ?: return@HudCanvasEditor
                        if (!widget.locked) {
                            val maxWidth = activeLayout.columns - widget.canvasX
                            val maxHeight = activeLayout.rows - widget.canvasY
                            val changed = widget.copy(
                                canvasWidth = (widget.canvasWidth + dw).coerceIn(2, maxWidth.coerceAtLeast(2)),
                                canvasHeight = (widget.canvasHeight + dh).coerceIn(2, maxHeight.coerceAtLeast(2)),
                            )
                            commit(draft.updateWidget(orientation, changed))
                        }
                    },
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { showLibrary = true }, modifier = Modifier.weight(1f)) {
                        Text("+ เพิ่ม Widget")
                    }
                    OutlinedButton(
                        onClick = {
                            commit(draft.withLayout(orientation, activeLayout.copy(widgets = emptyList())))
                            selectedWidgetId = null
                        },
                        enabled = activeLayout.widgets.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    ) { Text("ล้าง Canvas") }
                }
            }

            item {
                SectionTitle("ธีมโปรไฟล์", "เปลี่ยนสีทั้ง HUD แล้วปรับแต่ละ Widget ต่อได้")
                ScrollableChips {
                    DashboardDefaults.themes.forEach { theme ->
                        FilterChip(
                            selected = false,
                            onClick = { commit(draft.applyDashboardTheme(theme)) },
                            label = { Text(theme.name) },
                        )
                    }
                }
            }

            item {
                SectionTitle("จัดการโปรไฟล์", "ส่งออกหรือนำเข้า Custom HUD เพื่อย้ายระหว่างเครื่อง")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            val safeName = draft.name.ifBlank { "NowTuneUp-HUD" }
                                .replace(Regex("[^A-Za-z0-9._-]"), "-")
                            exportLauncher.launch("$safeName.json")
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("ส่งออก") }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                        modifier = Modifier.weight(1f),
                    ) { Text("นำเข้า") }
                }
            }
        }
    }
}

@Composable
private fun HudCanvasEditor(
    layout: DashboardLayout,
    orientation: EditorOrientation,
    selectedWidgetId: String?,
    onSelect: (String) -> Unit,
    onMove: (String, Int, Int) -> Unit,
    onResize: (String, Int, Int) -> Unit,
) {
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnResize by rememberUpdatedState(onResize)
    val aspect = if (orientation == EditorOrientation.PORTRAIT) 9f / 16f else 16f / 9f
    Surface(
        modifier = Modifier.fillMaxWidth().aspectRatio(aspect),
        color = layout.background?.let { Color(it) } ?: Color(0xFF050708),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 0.dp,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val cellWidth = maxWidth / layout.columns
            val cellHeight = maxHeight / layout.rows
            val cellWidthPx = with(density) { cellWidth.toPx() }.coerceAtLeast(1f)
            val cellHeightPx = with(density) { cellHeight.toPx() }.coerceAtLeast(1f)

            Canvas(Modifier.fillMaxSize()) {
                val stepX = size.width / layout.columns
                val stepY = size.height / layout.rows
                repeat(layout.columns + 1) { i ->
                    drawLine(
                        Color.White.copy(alpha = if (i % 3 == 0) 0.12f else 0.055f),
                        Offset(stepX * i, 0f),
                        Offset(stepX * i, size.height),
                        1f,
                    )
                }
                repeat(layout.rows + 1) { i ->
                    drawLine(
                        Color.White.copy(alpha = if (i % 3 == 0) 0.12f else 0.055f),
                        Offset(0f, stepY * i),
                        Offset(size.width, stepY * i),
                        1f,
                    )
                }
            }

            layout.widgets.sortedBy { it.zIndex }.forEach { widget ->
                var dragRemainder by remember(widget.id) { mutableStateOf(Offset.Zero) }
                var resizeRemainder by remember(widget.id) { mutableStateOf(Offset.Zero) }
                val selected = selectedWidgetId == widget.id
                val width = widget.canvasWidth.coerceIn(1, layout.columns - widget.canvasX)
                val height = widget.canvasHeight.coerceIn(1, layout.rows - widget.canvasY)

                Box(
                    modifier = Modifier
                        .offset(x = cellWidth * widget.canvasX, y = cellHeight * widget.canvasY)
                        .size(width = cellWidth * width, height = cellHeight * height)
                        .padding(2.dp)
                        .zIndex(widget.zIndex.toFloat())
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.18f),
                            shape = MaterialTheme.shapes.medium,
                        )
                        .clickable { currentOnSelect(widget.id) }
                        .pointerInput(widget.id, widget.locked, cellWidthPx, cellHeightPx) {
                            if (!widget.locked) {
                                detectDragGestures(
                                    onDragStart = {
                                        currentOnSelect(widget.id)
                                        dragRemainder = Offset.Zero
                                    },
                                    onDragEnd = { dragRemainder = Offset.Zero },
                                    onDragCancel = { dragRemainder = Offset.Zero },
                                ) { change, amount ->
                                    change.consume()
                                    dragRemainder += amount
                                    val dx = (dragRemainder.x / cellWidthPx).roundToInt()
                                    val dy = (dragRemainder.y / cellHeightPx).roundToInt()
                                    if (dx != 0 || dy != 0) {
                                        currentOnMove(widget.id, dx, dy)
                                        dragRemainder -= Offset(dx * cellWidthPx, dy * cellHeightPx)
                                    }
                                }
                            }
                        },
                ) {
                    DashboardWidgetView(
                        config = widget,
                        reading = previewReading(widget),
                        reduceMotion = true,
                        dtcCount = if (widget.type == DashboardWidgetType.DTC_CARD) 2 else 0,
                    )

                    if (selected) {
                        Surface(
                            modifier = Modifier.align(Alignment.TopStart).padding(5.dp),
                            color = Color.Black.copy(alpha = 0.78f),
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(
                                "${widget.canvasX},${widget.canvasY} · ${widget.canvasWidth}×${widget.canvasHeight}",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                            )
                        }

                        if (!widget.locked) {
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(34.dp)
                                    .pointerInput(widget.id, cellWidthPx, cellHeightPx) {
                                        detectDragGestures(
                                            onDragStart = { resizeRemainder = Offset.Zero },
                                            onDragEnd = { resizeRemainder = Offset.Zero },
                                            onDragCancel = { resizeRemainder = Offset.Zero },
                                        ) { change, amount ->
                                            change.consume()
                                            resizeRemainder += amount
                                            val dw = (resizeRemainder.x / cellWidthPx).roundToInt()
                                            val dh = (resizeRemainder.y / cellHeightPx).roundToInt()
                                            if (dw != 0 || dh != 0) {
                                                currentOnResize(widget.id, dw, dh)
                                                resizeRemainder -= Offset(dw * cellWidthPx, dh * cellHeightPx)
                                            }
                                        }
                                    },
                                color = MaterialTheme.colorScheme.primary,
                                shape = MaterialTheme.shapes.small,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("↘", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }
                }
            }

            if (layout.widgets.isEmpty()) {
                Text(
                    "Canvas ว่าง · กด “เพิ่ม Widget”",
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White.copy(alpha = 0.65f),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetLibrarySheet(
    layout: DashboardLayout,
    onDismiss: () -> Unit,
    onAdd: (DashboardWidgetConfig, DashboardWidgetType) -> Unit,
) {
    var type by remember { mutableStateOf(DashboardWidgetType.DIGITAL) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Widget Library", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text("เลือกเครื่องมือก่อน แล้วเลือกข้อมูล OBD ที่ต้องการแสดง", color = MaterialTheme.colorScheme.onSurfaceVariant)

            ScrollableChips {
                listOf(
                    DashboardWidgetType.DIGITAL,
                    DashboardWidgetType.ANALOG,
                    DashboardWidgetType.MINI_GAUGE,
                    DashboardWidgetType.DIGITAL_RING,
                    DashboardWidgetType.PROGRESS,
                    DashboardWidgetType.LINE_CHART,
                ).forEach { candidate ->
                    FilterChip(
                        selected = type == candidate,
                        onClick = { type = candidate },
                        label = { Text(candidate.label()) },
                    )
                }
            }

            DashboardDefaults.widgetCatalog
                .filter { it.type != DashboardWidgetType.DTC_CARD }
                .forEach { source ->
                    OutlinedButton(
                        onClick = { onAdd(source, type) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(source.title, fontWeight = FontWeight.Bold)
                            Text(
                                "${source.unit.label()} · PID ${if (source.pid >= 0) "0x%02X".format(source.pid) else "derived"}",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }

            HorizontalDivider()
            DashboardDefaults.widgetCatalog.firstOrNull { it.type == DashboardWidgetType.DTC_CARD }?.let { dtc ->
                OutlinedButton(
                    onClick = { onAdd(dtc, DashboardWidgetType.DTC_CARD) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("เพิ่ม DTC Status") }
            }
            Text(
                "เพิ่มข้อมูลชนิดเดียวกันหลาย Widget ได้ เช่น RPM แบบเข็ม + ตัวเลข + กราฟพร้อมกัน โดยระบบ OBD จะอ่าน PID เพียงชุดเดียว",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetPropertiesSheet(
    widget: DashboardWidgetConfig,
    layout: DashboardLayout,
    onDismiss: () -> Unit,
    onApply: (DashboardWidgetConfig) -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
) {
    var draft by remember(widget) { mutableStateOf(widget) }
    var scaleMin by remember(widget) { mutableStateOf(widget.scaleMinimum?.toString().orEmpty()) }
    var scaleMax by remember(widget) { mutableStateOf(widget.scaleMaximum?.toString().orEmpty()) }
    var warningLow by remember(widget) { mutableStateOf(widget.threshold.warningLow?.toString().orEmpty()) }
    var warningHigh by remember(widget) { mutableStateOf(widget.threshold.warningHigh?.toString().orEmpty()) }
    var criticalLow by remember(widget) { mutableStateOf(widget.threshold.criticalLow?.toString().orEmpty()) }
    var criticalHigh by remember(widget) { mutableStateOf(widget.threshold.criticalHigh?.toString().orEmpty()) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Widget Properties", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)

            OutlinedTextField(
                value = draft.title,
                onValueChange = { draft = draft.copy(title = it.take(40)) },
                label = { Text("ชื่อ Widget") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            SheetTitle("รูปแบบ")
            ScrollableChips {
                DashboardWidgetType.entries.forEach { candidate ->
                    FilterChip(
                        selected = draft.type == candidate,
                        onClick = { draft = draft.copy(type = candidate) },
                        label = { Text(candidate.label()) },
                    )
                }
            }

            if (draft.type in setOf(
                    DashboardWidgetType.ANALOG,
                    DashboardWidgetType.MINI_GAUGE,
                    DashboardWidgetType.DIGITAL_RING,
                )
            ) {
                SheetTitle("สไตล์เกจ")
                ScrollableChips {
                    userVisibleGaugeStyles.forEach { style ->
                        FilterChip(
                            selected = draft.gaugeStyle == style,
                            onClick = {
                                val preset = premiumGaugePreset(style)
                                draft = draft.copy(
                                    gaugeStyle = style,
                                    bezelFinish = preset.bezelFinish,
                                    colors = draft.colors.copy(
                                        value = preset.value,
                                        label = preset.label,
                                        background = preset.face,
                                        border = preset.bezel,
                                        warning = preset.warning,
                                        critical = preset.critical,
                                        face = preset.face,
                                        bezel = preset.bezel,
                                        tick = preset.tick,
                                        needle = preset.needle,
                                        needleHighlight = preset.needleHighlight,
                                        glow = preset.glow,
                                    ),
                                )
                            },
                            label = { Text(style.label()) },
                        )
                    }
                }
            }

            SheetTitle("ขนาดและตำแหน่ง")
            Text("ตำแหน่ง X ${draft.canvasX} · Y ${draft.canvasY}")
            Text("กว้าง ${draft.canvasWidth} / ${layout.columns}")
            Slider(
                value = draft.canvasWidth.toFloat(),
                onValueChange = {
                    val max = (layout.columns - draft.canvasX).coerceAtLeast(2)
                    draft = draft.copy(canvasWidth = it.roundToInt().coerceIn(2, max))
                },
                valueRange = 2f..(layout.columns - draft.canvasX).coerceAtLeast(2).toFloat(),
            )
            Text("สูง ${draft.canvasHeight} / ${layout.rows}")
            Slider(
                value = draft.canvasHeight.toFloat(),
                onValueChange = {
                    val max = (layout.rows - draft.canvasY).coerceAtLeast(2)
                    draft = draft.copy(canvasHeight = it.roundToInt().coerceIn(2, max))
                },
                valueRange = 2f..(layout.rows - draft.canvasY).coerceAtLeast(2).toFloat(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = draft.locked,
                    onClick = { draft = draft.copy(locked = !draft.locked) },
                    label = { Text(if (draft.locked) "ล็อกตำแหน่ง" else "ปลดล็อก") },
                )
                FilterChip(
                    selected = draft.showLabel,
                    onClick = { draft = draft.copy(showLabel = !draft.showLabel) },
                    label = { Text("ชื่อ") },
                )
                FilterChip(
                    selected = draft.showUnit,
                    onClick = { draft = draft.copy(showUnit = !draft.showUnit) },
                    label = { Text("หน่วย") },
                )
            }

            Text("ความโปร่งใส ${(draft.opacity * 100).roundToInt()}%")
            Slider(
                value = draft.opacity,
                onValueChange = { draft = draft.copy(opacity = it.coerceIn(0.20f, 1f)) },
                valueRange = 0.20f..1f,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { draft = draft.copy(zIndex = (draft.zIndex - 1).coerceAtLeast(0)) },
                    modifier = Modifier.weight(1f),
                ) { Text("ส่งไปหลัง") }
                OutlinedButton(
                    onClick = { draft = draft.copy(zIndex = (draft.zIndex + 1).coerceAtMost(10_000)) },
                    modifier = Modifier.weight(1f),
                ) { Text("นำมาหน้า") }
            }

            SheetTitle("หน่วยและตัวเลข")
            ScrollableChips {
                DisplayUnit.entries.forEach { unit ->
                    FilterChip(
                        selected = draft.unit == unit,
                        onClick = { draft = draft.copy(unit = unit) },
                        label = { Text(unit.label().ifBlank { "ไม่มี" }) },
                    )
                }
            }
            Text("ทศนิยม ${draft.decimals}")
            Slider(
                value = draft.decimals.toFloat(),
                onValueChange = { draft = draft.copy(decimals = it.roundToInt().coerceIn(0, 3)) },
                valueRange = 0f..3f,
                steps = 2,
            )
            Text("ขนาดตัวเลข ${draft.valueSize}")
            Slider(
                value = draft.valueSize.toFloat(),
                onValueChange = { draft = draft.copy(valueSize = it.roundToInt().coerceIn(16, 96)) },
                valueRange = 16f..96f,
            )

            SheetTitle("ช่วงสเกล")
            NumberField("ค่าต่ำสุด (ว่าง = Auto)", scaleMin) { scaleMin = it }
            NumberField("ค่าสูงสุด (ว่าง = Auto)", scaleMax) { scaleMax = it }

            SheetTitle("ช่วงเตือน")
            NumberField("Warning ต่ำกว่า", warningLow) { warningLow = it }
            NumberField("Warning สูงกว่า", warningHigh) { warningHigh = it }
            NumberField("Critical ต่ำกว่า", criticalLow) { criticalLow = it }
            NumberField("Critical สูงกว่า", criticalHigh) { criticalHigh = it }

            Button(
                onClick = {
                    val min = scaleMin.toDoubleOrNull()
                    val max = scaleMax.toDoubleOrNull()
                    onApply(
                        draft.copy(
                            scaleMinimum = min,
                            scaleMaximum = max,
                            threshold = WarningThreshold(
                                warningLow = warningLow.toDoubleOrNull(),
                                warningHigh = warningHigh.toDoubleOrNull(),
                                criticalLow = criticalLow.toDoubleOrNull(),
                                criticalHigh = criticalHigh.toDoubleOrNull(),
                            ),
                        ),
                    )
                },
                enabled = draft.title.isNotBlank() && (
                    (scaleMin.isBlank() && scaleMax.isBlank()) ||
                        (scaleMin.toDoubleOrNull() != null && scaleMax.toDoubleOrNull() != null &&
                            scaleMax.toDouble() > scaleMin.toDouble())
                    ),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("ใช้การตั้งค่า") }

            OutlinedButton(onClick = onDuplicate, modifier = Modifier.fillMaxWidth()) { Text("ทำสำเนา Widget") }
            OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) { Text("ลบ Widget") }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("ปิด") }
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
private fun SheetTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun NumberField(label: String, value: String, onValueChange: (String) -> Unit) {
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
private fun ScrollableChips(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

private fun createWidgetFromSource(
    source: DashboardWidgetConfig,
    type: DashboardWidgetType,
    layout: DashboardLayout,
): DashboardWidgetConfig {
    val size = when (type) {
        DashboardWidgetType.ANALOG -> 5 to 7
        DashboardWidgetType.MINI_GAUGE -> 4 to 5
        DashboardWidgetType.DIGITAL_RING -> 6 to 7
        DashboardWidgetType.PROGRESS -> 6 to 3
        DashboardWidgetType.LINE_CHART -> 8 to 5
        DashboardWidgetType.DTC_CARD -> 6 to 4
        DashboardWidgetType.DIGITAL -> 4 to 4
    }
    val width = size.first.coerceAtMost(layout.columns)
    val height = size.second.coerceAtMost(layout.rows)
    val position = findOpenPosition(layout, width, height)
    val topZ = (layout.widgets.maxOfOrNull { it.zIndex } ?: -1) + 1
    return source.copy(
        id = "${source.id}-${System.currentTimeMillis()}",
        type = type,
        canvasX = position.first,
        canvasY = position.second,
        canvasWidth = width,
        canvasHeight = height,
        zIndex = topZ,
        locked = false,
    )
}

private fun findOpenPosition(layout: DashboardLayout, width: Int, height: Int): Pair<Int, Int> {
    for (y in 0..(layout.rows - height).coerceAtLeast(0)) {
        for (x in 0..(layout.columns - width).coerceAtLeast(0)) {
            val overlaps = layout.widgets.any { widget ->
                x < widget.canvasX + widget.canvasWidth &&
                    x + width > widget.canvasX &&
                    y < widget.canvasY + widget.canvasHeight &&
                    y + height > widget.canvasY
            }
            if (!overlaps) return x to y
        }
    }
    return 0 to 0
}

private fun previewReading(widget: DashboardWidgetConfig): VehicleReading? {
    if (widget.type == DashboardWidgetType.DTC_CARD || widget.pid < 0) return null
    val minimum = widget.scaleMinimum ?: when (widget.unit) {
        DisplayUnit.RPM, DisplayUnit.KMH, DisplayUnit.MPH, DisplayUnit.PERCENT, DisplayUnit.KPA -> 0.0
        DisplayUnit.CELSIUS -> -40.0
        DisplayUnit.FAHRENHEIT -> -40.0
        DisplayUnit.VOLT -> 8.0
        DisplayUnit.BAR -> 0.0
        DisplayUnit.PSI -> -15.0
        DisplayUnit.LITER, DisplayUnit.GALLON -> 0.0
        DisplayUnit.NONE -> 0.0
    }
    val maximum = widget.scaleMaximum ?: when (widget.unit) {
        DisplayUnit.RPM -> 7_000.0
        DisplayUnit.KMH -> 200.0
        DisplayUnit.MPH -> 120.0
        DisplayUnit.CELSIUS -> 130.0
        DisplayUnit.FAHRENHEIT -> 266.0
        DisplayUnit.VOLT -> 18.0
        DisplayUnit.PERCENT -> 100.0
        DisplayUnit.KPA -> 255.0
        DisplayUnit.BAR -> 3.0
        DisplayUnit.PSI -> 30.0
        DisplayUnit.LITER -> 100.0
        DisplayUnit.GALLON -> 30.0
        DisplayUnit.NONE -> 100.0
    }
    return VehicleReading(
        pid = widget.pid,
        name = widget.title,
        value = minimum + (maximum - minimum) * 0.58,
        unit = widget.unit.label(),
        minimum = minimum,
        maximum = maximum,
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
    return withLayout(
        orientation,
        layout.copy(widgets = layout.widgets.map { if (it.id == changed.id) changed else it }),
    )
}

private fun DashboardConfig.copyOtherLayoutTo(target: EditorOrientation): DashboardConfig {
    val source = if (target == EditorOrientation.PORTRAIT) landscape else portrait
    val copiedWidgets = source.widgets.map { widget ->
        widget.copy(
            canvasX = widget.canvasX.coerceIn(0, (HUD_CANVAS_COLUMNS - widget.canvasWidth).coerceAtLeast(0)),
            canvasY = widget.canvasY.coerceIn(0, (HUD_CANVAS_ROWS - widget.canvasHeight).coerceAtLeast(0)),
        )
    }
    return withLayout(
        target,
        source.copy(
            columns = HUD_CANVAS_COLUMNS,
            rows = HUD_CANVAS_ROWS,
            widgets = copiedWidgets,
        ),
    )
}

private fun DashboardConfig.applyDashboardTheme(theme: ThemeConfig): DashboardConfig {
    fun themed(widget: DashboardWidgetConfig): DashboardWidgetConfig {
        val colors = ColorConfig(
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
            needleHighlight = theme.text,
            glow = theme.accent,
        )
        return widget.copy(colors = colors)
    }
    return copy(
        portrait = portrait.copy(background = theme.background, widgets = portrait.widgets.map(::themed)),
        landscape = landscape.copy(background = theme.background, widgets = landscape.widgets.map(::themed)),
    )
}

private fun EditorOrientation.label(): String = when (this) {
    EditorOrientation.PORTRAIT -> "แนวตั้ง"
    EditorOrientation.LANDSCAPE -> "แนวนอน"
}

private fun DashboardWidgetType.label(): String = when (this) {
    DashboardWidgetType.DIGITAL -> "ตัวเลข"
    DashboardWidgetType.DIGITAL_RING -> "วงแหวน"
    DashboardWidgetType.ANALOG -> "เข็ม Analog"
    DashboardWidgetType.MINI_GAUGE -> "เกจเล็ก"
    DashboardWidgetType.PROGRESS -> "แถบค่า"
    DashboardWidgetType.LINE_CHART -> "กราฟสด"
    DashboardWidgetType.DTC_CARD -> "DTC Status"
}

private fun GaugeStyle.label(): String = when (this) {
    GaugeStyle.CLASSIC_METAL -> "Classic"
    GaugeStyle.SPORT_RED -> "Sport Red"
    GaugeStyle.NEO_CYAN -> "Neo Cyan"
    GaugeStyle.RACING_AMBER -> "Racing Amber"
    GaugeStyle.OEM_BLUE -> "OEM Blue"
    GaugeStyle.HUD_GREEN -> "HUD Green"
    GaugeStyle.CUSTOM -> "Custom"
}

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
