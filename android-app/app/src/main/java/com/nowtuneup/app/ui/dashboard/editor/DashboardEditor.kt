package com.nowtuneup.app.ui.dashboard.editor

import androidx.activity.compose.BackHandler
import com.nowtuneup.app.ui.components.NtuScreenHeader
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import com.nowtuneup.app.ui.components.NtuPanel as Card
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nowtuneup.app.data.dashboard.DashboardCodec
import com.nowtuneup.app.data.dashboard.DashboardDefaults
import com.nowtuneup.app.domain.model.BezelFinish
import com.nowtuneup.app.domain.model.ColorConfig
import com.nowtuneup.app.domain.model.DashboardConfig
import com.nowtuneup.app.domain.model.DashboardLayout
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

private data class PaletteColor(val label: String, val argb: Long)

private val everydayPalette = listOf(
    PaletteColor("ดำ", 0xFF050607),
    PaletteColor("ดำอมเทา", 0xFF111827),
    PaletteColor("เทาเข้ม", 0xFF334155),
    PaletteColor("เงิน", 0xFFB0BEC5),
    PaletteColor("ขาว", 0xFFFFFFFF),
    PaletteColor("แดง", 0xFFFF1744),
    PaletteColor("ส้ม", 0xFFFF6D00),
    PaletteColor("ส้มอำพัน", 0xFFFFB300),
    PaletteColor("เหลือง", 0xFFFFD600),
    PaletteColor("เขียว", 0xFF48FF8A),
    PaletteColor("เขียวเข้ม", 0xFF00C853),
    PaletteColor("ฟ้าไซแอน", 0xFF35E6FF),
    PaletteColor("ฟ้า", 0xFF29B6F6),
    PaletteColor("น้ำเงิน", 0xFF2979FF),
    PaletteColor("ม่วง", 0xFFAA66FF),
    PaletteColor("ชมพู", 0xFFFF4FA3),
)

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
    var showThemeEditor by remember { mutableStateOf(false) }
    var transferMessage by remember { mutableStateOf<String?>(null) }
    val activeLayout = draft.layout(orientation)

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                    it.write(DashboardCodec.export(draft.copy(isDefault = false)))
                } ?: error("เปิดไฟล์ไม่ได้")
            }.onSuccess {
                transferMessage = "ส่งออกโปรไฟล์เรียบร้อยแล้ว"
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
                draft = it.copy(id = config.id, isDefault = false)
                selectedWidgetId = null
                transferMessage = "นำเข้าโปรไฟล์แล้ว กรุณาตรวจทั้งแนวตั้งและแนวนอนก่อนบันทึก"
            }.onFailure {
                transferMessage = "นำเข้าไม่สำเร็จ: ${it.message ?: "ไฟล์ไม่ถูกต้อง"}"
            }
        }
    }

    if (drivingMode) {
        AlertDialog(
            onDismissRequest = onCancel,
            confirmButton = { TextButton(onClick = onCancel) { Text("ตกลง") } },
            title = { Text("จอดรถก่อนแก้ไข") },
            text = { Text("ปิดโหมดขับรถก่อนสร้างหรือแก้ไขโปรไฟล์หน้าปัด") },
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
            confirmButton = { TextButton(onClick = { transferMessage = null }) { Text("ตกลง") } },
            title = { Text("ไฟล์โปรไฟล์") },
            text = { Text(message) },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("จัดหน้าปัดของฉัน") },
                navigationIcon = { TextButton(onClick = onCancel) { Text("ยกเลิก") } },
                actions = {
                    TextButton(
                        onClick = { onSave(draft.copy(isDefault = false)) },
                        enabled = draft.name.isNotBlank(),
                    ) { Text("บันทึก") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { NtuScreenHeader("ออกแบบหน้าปัด", "จัดวางข้อมูลและเลือกสไตล์เกจให้เป็นของคุณ ทั้งแนวตั้งและแนวนอน", eyebrow = "DASHBOARD STUDIO") }
            item {
                SectionTitle("ชื่อโปรไฟล์", "ตั้งชื่อให้จำง่าย เช่น ขับทุกวัน หรือ ดูเทอร์โบ")
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { draft = draft.copy(name = it.take(40)) },
                    label = { Text("ชื่อที่จะแสดง") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            item {
                SectionTitle("จัดวางหน้าจอ", "แนวตั้งและแนวนอนปรับแยกกันได้")
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
                Text("จำนวนช่องต่อแถว: ${activeLayout.columns}")
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
                    Text(if (orientation == EditorOrientation.PORTRAIT) "ใช้แบบแนวนอนกับแนวตั้ง" else "ใช้แบบแนวตั้งกับแนวนอน")
                }
            }

            item {
                SectionTitle("โทนสีทั้งโปรไฟล์", "เลือกโทนสำเร็จรูป หรือเลือกสีเองจากตัวอย่างสี")
                ScrollableChips {
                    DashboardDefaults.themes.forEach { theme ->
                        FilterChip(
                            selected = false,
                            onClick = { draft = draft.applyDashboardTheme(theme) },
                            label = { Text(theme.name) },
                        )
                    }
                    OutlinedButton(onClick = { showThemeEditor = true }) { Text("เลือกสีเอง") }
                }
            }

            item {
                SectionTitle(
                    "ข้อมูลใน${orientation.label()}",
                    if (activeLayout.widgets.isEmpty()) "ยังไม่มีข้อมูล กดเพิ่มจากรายการด้านล่าง" else "แตะรายการเพื่อตั้งค่า ใช้ปุ่มขึ้นลงเพื่อเรียงตำแหน่ง",
                )
            }

            itemsIndexed(
                items = activeLayout.widgets,
                key = { _, widget -> "${orientation.name}-${widget.id}" },
            ) { index, widget ->
                WidgetEditorCard(
                    widget = widget,
                    index = index,
                    total = activeLayout.widgets.size,
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
                val available = DashboardDefaults.widgetCatalog.filterNot { candidate ->
                    activeLayout.widgets.any { it.pid == candidate.pid }
                }
                SectionTitle("เพิ่มข้อมูล", "เลือกเฉพาะข้อมูลที่ต้องการเห็นบนหน้าปัด")
                if (available.isEmpty()) {
                    Text("เพิ่มข้อมูลที่รองรับทั้งหมดแล้ว")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        available.forEach { source ->
                            OutlinedButton(
                                onClick = {
                                    val added = source.copy(
                                        id = "${source.id}-${System.currentTimeMillis()}",
                                        row = activeLayout.widgets.size,
                                        columnSpan = source.columnSpan.coerceAtMost(activeLayout.columns),
                                    )
                                    draft = draft.withLayout(
                                        orientation,
                                        activeLayout.copy(widgets = activeLayout.widgets + added),
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("เพิ่ม ${source.title}") }
                        }
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = {
                        draft = draft.withLayout(orientation, activeLayout.copy(widgets = emptyList()))
                        selectedWidgetId = null
                    },
                    enabled = activeLayout.widgets.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("ล้างข้อมูลใน${orientation.label()}") }
            }

            item {
                SectionTitle("สำรองโปรไฟล์", "ส่งออกหรือนำเข้าไฟล์เพื่อย้ายโปรไฟล์ระหว่างเครื่อง")
                OutlinedButton(
                    onClick = {
                        val safeName = draft.name.ifBlank { "NowTuneUp-profile" }
                            .replace(Regex("[^A-Za-z0-9._-]"), "-")
                        exportLauncher.launch("$safeName.json")
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("ส่งออกไฟล์") }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("นำเข้าไฟล์") }
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
    onEdit: () -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    val preset = widget.resolvedGaugePreset()
    Card(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth(),
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
                    "${widget.type.label()} · กว้าง ${widget.columnSpan} ช่อง · สูง ${widget.rowSpan} แถว",
                    color = Color(preset.label).copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    TextButton(onClick = { onMove(index - 1) }, enabled = index > 0) { Text("ขึ้น") }
                    TextButton(onClick = { onMove(index + 1) }, enabled = index < total - 1) { Text("ลง") }
                    TextButton(onClick = onEdit) { Text("ตั้งค่า") }
                    TextButton(onClick = onRemove) { Text("ลบ") }
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
    var scaleMinimum by remember(widget.id) { mutableStateOf(widget.scaleMinimum.text()) }
    var scaleMaximum by remember(widget.id) { mutableStateOf(widget.scaleMaximum.text()) }
    var valueColor by remember(widget.id) { mutableLongStateOf(initialPreset.value) }
    var labelColor by remember(widget.id) { mutableLongStateOf(initialPreset.label) }
    var backgroundColor by remember(widget.id) { mutableLongStateOf(initialPreset.face) }
    var borderColor by remember(widget.id) { mutableLongStateOf(initialPreset.bezel) }
    var tickColor by remember(widget.id) { mutableLongStateOf(initialPreset.tick) }
    var needleColor by remember(widget.id) { mutableLongStateOf(initialPreset.needle) }
    var needleHighlightColor by remember(widget.id) { mutableLongStateOf(initialPreset.needleHighlight) }
    var glowColor by remember(widget.id) { mutableLongStateOf(initialPreset.glow) }
    var warningColor by remember(widget.id) { mutableLongStateOf(initialPreset.warning) }
    var criticalColor by remember(widget.id) { mutableLongStateOf(initialPreset.critical) }
    var ringPreset by remember(widget.id) { mutableStateOf(initialRing.preset) }
    var segmentCount by remember(widget.id) { mutableFloatStateOf(initialRing.segmentCount.toFloat()) }
    var showScaleLabels by remember(widget.id) { mutableStateOf(initialRing.showScaleLabels) }
    var digitColor by remember(widget.id) { mutableLongStateOf(initialRing.digitColor) }
    var activeSegmentColor by remember(widget.id) { mutableLongStateOf(initialRing.activeSegmentColor) }
    var inactiveSegmentColor by remember(widget.id) { mutableLongStateOf(initialRing.inactiveSegmentColor) }
    var scaleColor by remember(widget.id) { mutableLongStateOf(initialRing.scaleColor) }
    var titleColor by remember(widget.id) { mutableLongStateOf(initialRing.titleColor) }
    var ringBezelColor by remember(widget.id) { mutableLongStateOf(initialRing.bezelColor) }

    fun applyAnalogPreset(style: GaugeStyle) {
        val selected = premiumGaugePreset(style)
        draft = draft.copy(gaugeStyle = style, bezelFinish = selected.bezelFinish)
        valueColor = selected.value
        labelColor = selected.label
        backgroundColor = selected.face
        borderColor = selected.bezel
        tickColor = selected.tick
        needleColor = selected.needle
        needleHighlightColor = selected.needleHighlight
        glowColor = selected.glow
        warningColor = selected.warning
        criticalColor = selected.critical
    }

    fun applyRingPreset(preset: DigitalRingColorPreset) {
        val selected = digitalRingPreset(preset, segmentCount.toInt())
        ringPreset = preset
        digitColor = selected.digitColor
        activeSegmentColor = selected.activeSegmentColor
        inactiveSegmentColor = selected.inactiveSegmentColor
        scaleColor = selected.scaleColor
        titleColor = selected.titleColor
        ringBezelColor = selected.bezelColor
        valueColor = selected.digitColor
        labelColor = selected.scaleColor
        backgroundColor = 0xFF000000
        borderColor = selected.bezelColor
    }

    val parsedScaleMinimum = scaleMinimum.toDoubleOrNull()
    val parsedScaleMaximum = scaleMaximum.toDoubleOrNull()
    val scaleIsValid = when {
        scaleMinimum.isBlank() && scaleMaximum.isBlank() -> true
        parsedScaleMinimum == null || parsedScaleMaximum == null -> false
        !parsedScaleMinimum.isFinite() || !parsedScaleMaximum.isFinite() -> false
        else -> parsedScaleMaximum > parsedScaleMinimum
    }

    val previewColors = ColorConfig(
        value = valueColor,
        label = labelColor,
        background = backgroundColor,
        border = borderColor,
        warning = warningColor,
        critical = criticalColor,
        face = backgroundColor,
        bezel = borderColor,
        tick = tickColor,
        needle = needleColor,
        needleHighlight = needleHighlightColor,
        glow = glowColor,
    )
    val previewRing = if (draft.type == DashboardWidgetType.DIGITAL_RING) {
        DigitalRingConfig(
            preset = ringPreset,
            segmentCount = segmentCount.toInt().coerceIn(12, 72),
            digitColor = digitColor,
            activeSegmentColor = activeSegmentColor,
            inactiveSegmentColor = inactiveSegmentColor,
            scaleColor = scaleColor,
            titleColor = titleColor,
            bezelColor = ringBezelColor,
            showScaleLabels = showScaleLabels,
        )
    } else draft.digitalRing
    val previewWidget = draft.copy(
        scaleMinimum = parsedScaleMinimum.takeIf { scaleIsValid },
        scaleMaximum = parsedScaleMaximum.takeIf { scaleIsValid },
        colors = previewColors,
        digitalRing = previewRing,
        rowSpan = 2,
        columnSpan = 1,
    )
    val previewMinimum = previewWidget.scaleMinimum ?: 0.0
    val previewMaximum = previewWidget.scaleMaximum?.takeIf { it > previewMinimum }
        ?: if (previewWidget.unit == DisplayUnit.RPM) 7_000.0 else 100.0
    val previewReading = VehicleReading(
        pid = previewWidget.pid,
        name = previewWidget.title,
        value = previewMinimum + (previewMaximum - previewMinimum) * 0.45,
        unit = previewWidget.unit.name,
        supported = true,
        minimum = previewMinimum,
        maximum = previewMaximum,
    )

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("ตั้งค่าข้อมูลที่แสดง", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(previewColors.background)),
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth(),
            ) {
                DashboardWidgetView(previewWidget, previewReading, reduceMotion = false, dtcCount = 0)
            }

            SheetHeading("ชื่อและรูปแบบ")
            OutlinedTextField(
                value = draft.title,
                onValueChange = { draft = draft.copy(title = it.take(32)) },
                label = { Text("ชื่อที่จะแสดง") },
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

            if (draft.type in setOf(DashboardWidgetType.ANALOG, DashboardWidgetType.MINI_GAUGE)) {
                HorizontalDivider()
                SheetHeading("หน้าตามาตรวัด")
                Text("เลือกแบบที่ชอบ แล้วปรับสีเพิ่มเติมด้านล่างได้")
                GaugePresetGallery(selected = draft.gaugeStyle, onSelect = ::applyAnalogPreset)

                SheetHeading("ขอบมาตรวัด")
                ScrollableChips {
                    BezelFinish.entries.forEach { finish ->
                        FilterChip(
                            selected = (draft.bezelFinish ?: initialPreset.bezelFinish) == finish,
                            onClick = { draft = draft.copy(bezelFinish = finish) },
                            label = { Text(finish.label()) },
                        )
                    }
                }

                SheetHeading("การเคลื่อนที่ของเข็ม")
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
                    label = { Text(if (draft.showPeakMarker) "แสดงจุดค่าสูงสุด" else "ไม่แสดงจุดค่าสูงสุด") },
                )
            }

            if (draft.type == DashboardWidgetType.DIGITAL_RING) {
                HorizontalDivider()
                SheetHeading("วงแหวนดิจิทัล")
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
                Text("จำนวนขีดรอบวง: ${segmentCount.toInt()}")
                Slider(
                    value = segmentCount,
                    onValueChange = { segmentCount = it.coerceIn(12f, 72f) },
                    valueRange = 12f..72f,
                    steps = 59,
                )
                FilterChip(
                    selected = showScaleLabels,
                    onClick = { showScaleLabels = !showScaleLabels },
                    label = { Text(if (showScaleLabels) "แสดงตัวเลขรอบวง" else "ซ่อนตัวเลขรอบวง") },
                )
                ColorPicker("สีตัวเลขตรงกลาง", digitColor) { digitColor = it; ringPreset = DigitalRingColorPreset.CUSTOM }
                ColorPicker("สีขีดที่ติด", activeSegmentColor) { activeSegmentColor = it; ringPreset = DigitalRingColorPreset.CUSTOM }
                ColorPicker("สีขีดที่ยังไม่ติด", inactiveSegmentColor) { inactiveSegmentColor = it; ringPreset = DigitalRingColorPreset.CUSTOM }
                ColorPicker("สีตัวเลขรอบวง", scaleColor) { scaleColor = it; ringPreset = DigitalRingColorPreset.CUSTOM }
                ColorPicker("สีชื่อข้อมูล", titleColor) { titleColor = it; ringPreset = DigitalRingColorPreset.CUSTOM }
                ColorPicker("สีขอบวงแหวน", ringBezelColor) { ringBezelColor = it; ringPreset = DigitalRingColorPreset.CUSTOM }
            }

            HorizontalDivider()
            SheetHeading("หน่วยและขนาด")
            ScrollableChips {
                DisplayUnit.entries.forEach { unit ->
                    FilterChip(
                        selected = draft.unit == unit,
                        onClick = { draft = draft.copy(unit = unit) },
                        label = { Text(unit.label().ifBlank { "ไม่มีหน่วย" }) },
                    )
                }
            }
            Text("ความกว้าง: ${draft.columnSpan.coerceIn(1, columns)} จาก $columns ช่อง")
            if (columns > 1) {
                Slider(
                    value = draft.columnSpan.coerceIn(1, columns).toFloat(),
                    onValueChange = { draft = draft.copy(columnSpan = it.toInt().coerceIn(1, columns)) },
                    valueRange = 1f..columns.toFloat(),
                    steps = (columns - 2).coerceAtLeast(0),
                )
            }
            Text("ความสูง: ${draft.rowSpan.coerceIn(1, 4)} แถว")
            Slider(
                value = draft.rowSpan.coerceIn(1, 4).toFloat(),
                onValueChange = { draft = draft.copy(rowSpan = it.toInt().coerceIn(1, 4)) },
                valueRange = 1f..4f,
                steps = 2,
            )
            Text("จำนวนทศนิยม: ${draft.decimals}")
            Slider(
                value = draft.decimals.toFloat(),
                onValueChange = { draft = draft.copy(decimals = it.toInt().coerceIn(0, 3)) },
                valueRange = 0f..3f,
                steps = 2,
            )
            Text("ขนาดตัวเลข: ${draft.valueSize}")
            Slider(
                value = draft.valueSize.toFloat(),
                onValueChange = { draft = draft.copy(valueSize = it.toInt().coerceIn(20, 80)) },
                valueRange = 20f..80f,
                steps = 11,
            )

            HorizontalDivider()
            SheetHeading("ขอบเขตตัวเลขของ Widget")
            Text("กำหนดค่าน้อยสุดและมากสุดของมาตรวัด วงแหวน และแถบแสดงค่า เว้นว่างทั้งสองช่องเพื่อใช้ช่วงอัตโนมัติ")
            ThresholdRow("ค่าต่ำสุดของสเกล", scaleMinimum) { scaleMinimum = it }
            ThresholdRow("ค่าสูงสุดของสเกล", scaleMaximum) { scaleMaximum = it }
            if (!scaleIsValid) {
                Text(
                    "กรอกทั้งสองค่า และค่าสูงสุดต้องมากกว่าค่าต่ำสุด",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedButton(
                onClick = {
                    scaleMinimum = ""
                    scaleMaximum = ""
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("ใช้ช่วงอัตโนมัติจากข้อมูล OBD") }

            HorizontalDivider()
            SheetHeading("ช่วงเตือน")
            Text("เว้นว่างได้เมื่อไม่ต้องการใช้ช่วงนั้น")
            ThresholdRow("เริ่มเตือนเมื่อค่าต่ำกว่า", warningLow) { warningLow = it }
            ThresholdRow("เริ่มเตือนเมื่อค่าสูงกว่า", warningHigh) { warningHigh = it }
            ThresholdRow("อันตรายเมื่อค่าต่ำกว่า", criticalLow) { criticalLow = it }
            ThresholdRow("อันตรายเมื่อค่าสูงกว่า", criticalHigh) { criticalHigh = it }

            HorizontalDivider()
            SheetHeading("เลือกสี")
            Text("แตะชื่อสีที่ต้องการ ไม่ต้องกรอกรหัสสี")
            ColorPicker("สีค่าตัวเลข", valueColor) { valueColor = it; draft = draft.copy(gaugeStyle = GaugeStyle.CUSTOM) }
            ColorPicker("สีชื่อข้อมูล", labelColor) { labelColor = it; draft = draft.copy(gaugeStyle = GaugeStyle.CUSTOM) }
            ColorPicker("สีพื้นหลัง", backgroundColor) { backgroundColor = it; draft = draft.copy(gaugeStyle = GaugeStyle.CUSTOM) }
            ColorPicker("สีขอบ", borderColor) { borderColor = it; draft = draft.copy(gaugeStyle = GaugeStyle.CUSTOM) }
            if (draft.type in setOf(DashboardWidgetType.ANALOG, DashboardWidgetType.MINI_GAUGE)) {
                ColorPicker("สีขีดสเกล", tickColor) { tickColor = it; draft = draft.copy(gaugeStyle = GaugeStyle.CUSTOM) }
                ColorPicker("สีเข็ม", needleColor) { needleColor = it; draft = draft.copy(gaugeStyle = GaugeStyle.CUSTOM) }
                ColorPicker("สีเงาบนเข็ม", needleHighlightColor) { needleHighlightColor = it; draft = draft.copy(gaugeStyle = GaugeStyle.CUSTOM) }
                ColorPicker("สีแสงเรือง", glowColor) { glowColor = it; draft = draft.copy(gaugeStyle = GaugeStyle.CUSTOM) }
            }
            ColorPicker("สีเตือน", warningColor) { warningColor = it }
            ColorPicker("สีอันตราย", criticalColor) { criticalColor = it }

            Button(
                onClick = {
                    val ring = if (draft.type == DashboardWidgetType.DIGITAL_RING) previewRing else draft.digitalRing
                    onSave(
                        draft.copy(
                            threshold = WarningThreshold(
                                warningLow = warningLow.toDoubleOrNull(),
                                warningHigh = warningHigh.toDoubleOrNull(),
                                criticalLow = criticalLow.toDoubleOrNull(),
                                criticalHigh = criticalHigh.toDoubleOrNull(),
                            ),
                            scaleMinimum = parsedScaleMinimum,
                            scaleMaximum = parsedScaleMaximum,
                            colors = previewColors,
                            digitalRing = ring,
                        ),
                    )
                },
                enabled = scaleIsValid,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("ใช้การตั้งค่านี้") }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("ยกเลิก") }
        }
    }
}

@Composable
private fun ColorPicker(title: String, selected: Long, onSelect: (Long) -> Unit) {
    val choices = remember(selected) {
        if (everydayPalette.any { it.argb == selected }) everydayPalette
        else listOf(PaletteColor("สีที่ใช้อยู่", selected)) + everydayPalette
    }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        ScrollableChips {
            choices.forEach { option ->
                FilterChip(
                    selected = selected == option.argb,
                    onClick = { onSelect(option.argb) },
                    leadingIcon = { ColorDot(Color(option.argb), Modifier.size(16.dp)) },
                    label = { Text(option.label) },
                )
            }
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
private fun ColorDot(color: Color, modifier: Modifier = Modifier.size(11.dp)) {
    Canvas(modifier) { drawCircle(color) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardThemeSheet(
    initial: ThemeConfig,
    onDismiss: () -> Unit,
    onApply: (ThemeConfig) -> Unit,
) {
    var name by remember(initial) { mutableStateOf(initial.name) }
    var primary by remember(initial) { mutableLongStateOf(initial.primary) }
    var background by remember(initial) { mutableLongStateOf(initial.background) }
    var card by remember(initial) { mutableLongStateOf(initial.card) }
    var text by remember(initial) { mutableLongStateOf(initial.text) }
    var needle by remember(initial) { mutableLongStateOf(initial.gaugeNeedle) }
    var tick by remember(initial) { mutableLongStateOf(initial.gaugeTick) }
    var warning by remember(initial) { mutableLongStateOf(initial.warning) }
    var critical by remember(initial) { mutableLongStateOf(initial.critical) }
    var border by remember(initial) { mutableLongStateOf(initial.border) }
    val preview = initial.copy(
        name = name.ifBlank { "สีของฉัน" },
        primary = primary,
        accent = primary,
        background = background,
        card = card,
        text = text,
        gaugeNeedle = needle,
        gaugeTick = tick,
        warning = warning,
        critical = critical,
        border = border,
    )

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("เลือกสีของโปรไฟล์", style = MaterialTheme.typography.headlineSmall)
            Card(colors = CardDefaults.cardColors(containerColor = Color(preview.card)), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("ตัวอย่างหน้าปัด", color = Color(preview.text), fontWeight = FontWeight.Bold)
                    Text("2,450 rpm", color = Color(preview.gaugeNeedle), style = MaterialTheme.typography.headlineMedium)
                }
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(30) },
                label = { Text("ชื่อโทนสี") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            ColorPicker("สีหลัก", primary) { primary = it }
            ColorPicker("สีพื้นหลังหน้าปัด", background) { background = it }
            ColorPicker("สีพื้นหลังแต่ละข้อมูล", card) { card = it }
            ColorPicker("สีข้อความ", text) { text = it }
            ColorPicker("สีเข็มและค่าตัวเลข", needle) { needle = it }
            ColorPicker("สีขีดสเกล", tick) { tick = it }
            ColorPicker("สีเตือน", warning) { warning = it }
            ColorPicker("สีอันตราย", critical) { critical = it }
            ColorPicker("สีขอบ", border) { border = it }
            Button(onClick = { onApply(preview) }, modifier = Modifier.fillMaxWidth()) { Text("ใช้โทนสีนี้") }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("ยกเลิก") }
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

private fun DashboardConfig.dashboardTheme(orientation: EditorOrientation): ThemeConfig {
    val colors = layout(orientation).widgets.firstOrNull()?.colors ?: ColorConfig()
    return ThemeConfig(
        name = "สีของฉัน",
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
            gaugeStyle = if (widget.type in setOf(DashboardWidgetType.ANALOG, DashboardWidgetType.MINI_GAUGE)) GaugeStyle.CUSTOM else widget.gaugeStyle,
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

private fun EditorOrientation.label(): String = when (this) {
    EditorOrientation.PORTRAIT -> "แนวตั้ง"
    EditorOrientation.LANDSCAPE -> "แนวนอน"
}

private fun DashboardWidgetType.label(): String = when (this) {
    DashboardWidgetType.DIGITAL -> "ตัวเลข"
    DashboardWidgetType.DIGITAL_RING -> "วงแหวนดิจิทัล"
    DashboardWidgetType.ANALOG -> "มาตรวัดเข็ม"
    DashboardWidgetType.MINI_GAUGE -> "มาตรวัดขนาดเล็ก"
    DashboardWidgetType.PROGRESS -> "แถบแสดงค่า"
    DashboardWidgetType.DTC_CARD -> "การ์ดรหัสปัญหา"
}

private fun GaugeStyle.label(): String = when (this) {
    GaugeStyle.CLASSIC_METAL -> "คลาสสิกขอบเงิน"
    GaugeStyle.SPORT_RED -> "สปอร์ตแดง"
    GaugeStyle.NEO_CYAN -> "ฟ้านีออน"
    GaugeStyle.RACING_AMBER -> "ส้มเรซซิ่ง"
    GaugeStyle.OEM_BLUE -> "น้ำเงินแบบโรงงาน"
    GaugeStyle.HUD_GREEN -> "เขียว HUD"
    GaugeStyle.CUSTOM -> "สีที่เลือกเอง"
}

private fun BezelFinish.label(): String = when (this) {
    BezelFinish.BRUSHED_STEEL -> "เงินปัดด้าน"
    BezelFinish.BLACK_CHROME -> "โครเมียมดำ"
    BezelFinish.TITANIUM_DARK -> "ไทเทเนียมเข้ม"
}

private fun GaugeSmoothing.label(): String = when (this) {
    GaugeSmoothing.FAST -> "ตอบสนองไว"
    GaugeSmoothing.BALANCED -> "สมดุล"
    GaugeSmoothing.SMOOTH -> "นุ่มนวล"
}

private fun DigitalRingColorPreset.label(): String = when (this) {
    DigitalRingColorPreset.AMBER -> "ส้มอำพัน"
    DigitalRingColorPreset.CYAN -> "ฟ้า"
    DigitalRingColorPreset.GREEN -> "เขียว"
    DigitalRingColorPreset.RED -> "แดง"
    DigitalRingColorPreset.PURPLE -> "ม่วง"
    DigitalRingColorPreset.WHITE -> "ขาว"
    DigitalRingColorPreset.CUSTOM -> "เลือกเอง"
}

private fun DisplayUnit.label(): String = when (this) {
    DisplayUnit.KMH -> "กม./ชม."
    DisplayUnit.MPH -> "ไมล์/ชม."
    DisplayUnit.CELSIUS -> "°C"
    DisplayUnit.FAHRENHEIT -> "°F"
    DisplayUnit.VOLT -> "โวลต์"
    DisplayUnit.PERCENT -> "%"
    DisplayUnit.KPA -> "kPa"
    DisplayUnit.BAR -> "bar"
    DisplayUnit.PSI -> "psi"
    DisplayUnit.LITER -> "ลิตร"
    DisplayUnit.GALLON -> "แกลลอน"
    DisplayUnit.RPM -> "รอบ/นาที"
    DisplayUnit.NONE -> ""
}

private fun Double?.text(): String = this?.toString().orEmpty()
