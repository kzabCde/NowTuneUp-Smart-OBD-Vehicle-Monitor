from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(
            f"{path}: expected exactly one occurrence, found {count}: {old[:100]!r}"
        )
    file.write_text(text.replace(old, new, 1))


models = "android-app/app/src/main/java/com/nowtuneup/app/domain/model/Models.kt"
replace_once(
    models,
    "    val showPeakMarker: Boolean = true,\n    val colors: ColorConfig = ColorConfig(),",
    "    val showPeakMarker: Boolean = true,\n"
    "    val scaleMinimum: Double? = null,\n"
    "    val scaleMaximum: Double? = null,\n"
    "    val colors: ColorConfig = ColorConfig(),",
)


defaults = "android-app/app/src/main/java/com/nowtuneup/app/data/dashboard/DashboardDefaults.kt"
replace_once(
    defaults,
    "        val premium = premiumGaugePreset(gaugeStyle)\n"
    "        return DashboardWidgetConfig(",
    """        val premium = premiumGaugePreset(gaugeStyle)
        val defaultScale = when {
            pid == DerivedPids.TURBO_PRESSURE && unit == DisplayUnit.PSI -> -15.0 to 30.0
            unit == DisplayUnit.RPM -> 0.0 to 7_000.0
            unit == DisplayUnit.KMH -> 0.0 to 200.0
            unit == DisplayUnit.MPH -> 0.0 to 120.0
            unit == DisplayUnit.CELSIUS -> -40.0 to 130.0
            unit == DisplayUnit.FAHRENHEIT -> -40.0 to 266.0
            unit == DisplayUnit.VOLT -> 8.0 to 18.0
            unit == DisplayUnit.PERCENT -> 0.0 to 100.0
            unit == DisplayUnit.KPA -> 0.0 to 255.0
            unit == DisplayUnit.BAR -> 0.0 to 3.0
            unit == DisplayUnit.PSI -> 0.0 to 100.0
            unit == DisplayUnit.LITER -> 0.0 to 100.0
            unit == DisplayUnit.GALLON -> 0.0 to 30.0
            else -> null
        }
        return DashboardWidgetConfig(""",
)
replace_once(
    defaults,
    "            bezelFinish = premium.bezelFinish,\n"
    "            colors = ColorConfig(",
    "            bezelFinish = premium.bezelFinish,\n"
    "            scaleMinimum = defaultScale?.first,\n"
    "            scaleMaximum = defaultScale?.second,\n"
    "            colors = ColorConfig(",
)


codec = "android-app/app/src/main/java/com/nowtuneup/app/data/dashboard/DashboardCodec.kt"
replace_once(
    codec,
    "            val ringIsValid = widget.digitalRing?.let { ring -> ring.segmentCount in 12..72 } ?: true\n"
    "            widget.title.isNotBlank() &&",
    """            val ringIsValid = widget.digitalRing?.let { ring -> ring.segmentCount in 12..72 } ?: true
            val scaleMinimum = widget.scaleMinimum
            val scaleMaximum = widget.scaleMaximum
            val scaleIsValid = when {
                scaleMinimum == null && scaleMaximum == null -> true
                scaleMinimum == null || scaleMaximum == null -> false
                !scaleMinimum.isFinite() || !scaleMaximum.isFinite() -> false
                else -> scaleMaximum > scaleMinimum
            }
            widget.title.isNotBlank() &&""",
)
replace_once(
    codec,
    "                widget.rowSpan in 1..4 &&\n"
    "                ringIsValid",
    "                widget.rowSpan in 1..4 &&\n"
    "                ringIsValid &&\n"
    "                scaleIsValid",
)


widgets = "android-app/app/src/main/java/com/nowtuneup/app/ui/dashboard/components/DashboardWidgets.kt"
replace_once(
    widgets,
    "            DashboardWidgetType.PROGRESS -> ProgressWidget(config, value, status, statusColor)",
    "            DashboardWidgetType.PROGRESS -> ProgressWidget(config, reading, status, statusColor)",
)
replace_once(
    widgets,
    """private fun ProgressWidget(
    config: DashboardWidgetConfig,
    value: Double?,
    status: ReadingStatus,
    color: Color,
) {
    Column(""",
    """private fun ProgressWidget(
    config: DashboardWidgetConfig,
    reading: VehicleReading?,
    status: ReadingStatus,
    color: Color,
) {
    val value = reading?.takeIf { it.supported }?.value
    val scale = config.resolveScale(reading)
    Column(""",
)
replace_once(
    widgets,
    "            progress = { ((value ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f) },",
    "            progress = { value?.let { normalize(it, scale.minimum, scale.maximum) } ?: 0f },",
)
replace_once(
    widgets,
    """    val ring = config.digitalRing ?: digitalRingPreset(DigitalRingColorPreset.AMBER)
    val minimum = reading?.minimum ?: 0.0
    val maximum = reading?.maximum?.takeIf { it > minimum } ?: 100.0
    val value = reading?.takeIf { it.supported }?.value""",
    """    val ring = config.digitalRing ?: digitalRingPreset(DigitalRingColorPreset.AMBER)
    val scale = config.resolveScale(reading)
    val minimum = scale.minimum
    val maximum = scale.maximum
    val value = reading?.takeIf { it.supported }?.value""",
)
replace_once(
    widgets,
    """    val preset = config.resolvedGaugePreset()
    val minimum = reading?.minimum ?: 0.0
    val maximum = reading?.maximum?.takeIf { it > minimum } ?: 100.0
    val value = reading?.takeIf { it.supported }?.value""",
    """    val preset = config.resolvedGaugePreset()
    val scale = config.resolveScale(reading)
    val minimum = scale.minimum
    val maximum = scale.maximum
    val value = reading?.takeIf { it.supported }?.value""",
)
replace_once(
    widgets,
    """private fun normalize(value: Double, minimum: Double, maximum: Double): Float =
    if (maximum <= minimum) 0f else ((value - minimum) / (maximum - minimum)).toFloat().coerceIn(0f, 1f)""",
    """private data class WidgetScale(val minimum: Double, val maximum: Double)

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
    if (maximum <= minimum) 0f else ((value - minimum) / (maximum - minimum)).toFloat().coerceIn(0f, 1f)""",
)


editor = "android-app/app/src/main/java/com/nowtuneup/app/ui/dashboard/editor/DashboardEditor.kt"
replace_once(
    editor,
    """    var criticalLow by remember(widget.id) { mutableStateOf(widget.threshold.criticalLow.text()) }
    var criticalHigh by remember(widget.id) { mutableStateOf(widget.threshold.criticalHigh.text()) }
    var valueColor""",
    """    var criticalLow by remember(widget.id) { mutableStateOf(widget.threshold.criticalLow.text()) }
    var criticalHigh by remember(widget.id) { mutableStateOf(widget.threshold.criticalHigh.text()) }
    var scaleMinimum by remember(widget.id) { mutableStateOf(widget.scaleMinimum.text()) }
    var scaleMaximum by remember(widget.id) { mutableStateOf(widget.scaleMaximum.text()) }
    var valueColor""",
)
replace_once(
    editor,
    "    val previewColors = ColorConfig(",
    """    val parsedScaleMinimum = scaleMinimum.toDoubleOrNull()
    val parsedScaleMaximum = scaleMaximum.toDoubleOrNull()
    val scaleIsValid = when {
        scaleMinimum.isBlank() && scaleMaximum.isBlank() -> true
        parsedScaleMinimum == null || parsedScaleMaximum == null -> false
        !parsedScaleMinimum.isFinite() || !parsedScaleMaximum.isFinite() -> false
        else -> parsedScaleMaximum > parsedScaleMinimum
    }

    val previewColors = ColorConfig(""",
)
replace_once(
    editor,
    """    val previewWidget = draft.copy(
        colors = previewColors,
        digitalRing = previewRing,""",
    """    val previewWidget = draft.copy(
        scaleMinimum = parsedScaleMinimum.takeIf { scaleIsValid },
        scaleMaximum = parsedScaleMaximum.takeIf { scaleIsValid },
        colors = previewColors,
        digitalRing = previewRing,""",
)
replace_once(
    editor,
    """    val previewReading = VehicleReading(
        pid = previewWidget.pid,
        name = previewWidget.title,
        value = if (previewWidget.unit == DisplayUnit.RPM) 2_450.0 else 62.0,
        unit = previewWidget.unit.name,
        supported = true,
        minimum = 0.0,
        maximum = if (previewWidget.unit == DisplayUnit.RPM) 8_000.0 else 100.0,
    )""",
    """    val previewMinimum = previewWidget.scaleMinimum ?: 0.0
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
    )""",
)
replace_once(
    editor,
    """            Text("ขนาดตัวเลข: ${draft.valueSize}")
            Slider(
                value = draft.valueSize.toFloat(),
                onValueChange = { draft = draft.copy(valueSize = it.toInt().coerceIn(20, 80)) },
                valueRange = 20f..80f,
                steps = 11,
            )

            HorizontalDivider()
            SheetHeading("ช่วงเตือน")""",
    """            Text("ขนาดตัวเลข: ${draft.valueSize}")
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
            SheetHeading("ช่วงเตือน")""",
)
replace_once(
    editor,
    """                            threshold = WarningThreshold(
                                warningLow = warningLow.toDoubleOrNull(),
                                warningHigh = warningHigh.toDoubleOrNull(),
                                criticalLow = criticalLow.toDoubleOrNull(),
                                criticalHigh = criticalHigh.toDoubleOrNull(),
                            ),
                            colors = previewColors,""",
    """                            threshold = WarningThreshold(
                                warningLow = warningLow.toDoubleOrNull(),
                                warningHigh = warningHigh.toDoubleOrNull(),
                                criticalLow = criticalLow.toDoubleOrNull(),
                                criticalHigh = criticalHigh.toDoubleOrNull(),
                            ),
                            scaleMinimum = parsedScaleMinimum,
                            scaleMaximum = parsedScaleMaximum,
                            colors = previewColors,""",
)
replace_once(
    editor,
    """                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("ใช้การตั้งค่านี้") }""",
    """                },
                enabled = scaleIsValid,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("ใช้การตั้งค่านี้") }""",
)


tests = "android-app/app/src/test/java/com/nowtuneup/app/data/dashboard/DashboardCodecTest.kt"
replace_once(
    tests,
    """        assertTrue(DashboardDefaults.widgetCatalog.isNotEmpty())
    }

    @Test
    fun roundTripPreservesIndependentResponsiveLayouts()""",
    """        assertTrue(DashboardDefaults.widgetCatalog.isNotEmpty())
    }

    @Test
    fun catalogProvidesEditableVehicleScaleDefaults() {
        val speed = DashboardDefaults.widgetCatalog.first { it.pid == 0x0D }
        val rpm = DashboardDefaults.widgetCatalog.first { it.pid == 0x0C }

        assertEquals(0.0, speed.scaleMinimum)
        assertEquals(200.0, speed.scaleMaximum)
        assertEquals(0.0, rpm.scaleMinimum)
        assertEquals(7_000.0, rpm.scaleMaximum)
    }

    @Test
    fun roundTripPreservesIndependentResponsiveLayouts()""",
)
replace_once(
    tests,
    """            unit = DisplayUnit.KMH,
            columnSpan = 2,""",
    """            unit = DisplayUnit.KMH,
            columnSpan = 2,
            scaleMinimum = 20.0,
            scaleMaximum = 240.0,""",
)
replace_once(
    tests,
    """        assertEquals(DashboardWidgetType.DIGITAL, imported.portrait.widgets.single().type)
        assertEquals(DashboardWidgetType.DIGITAL_RING, imported.landscape.widgets.single().type)""",
    """        assertEquals(DashboardWidgetType.DIGITAL, imported.portrait.widgets.single().type)
        assertEquals(20.0, imported.portrait.widgets.single().scaleMinimum)
        assertEquals(240.0, imported.portrait.widgets.single().scaleMaximum)
        assertEquals(DashboardWidgetType.DIGITAL_RING, imported.landscape.widgets.single().type)""",
)
replace_once(
    tests,
    """    @Test
    fun importRejectsInvalidDigitalRingSegmentCount()""",
    """    @Test
    fun importRejectsIncompleteOrReversedWidgetScale() {
        val base = DashboardDefaults.widgetCatalog.first()
        val incomplete = DashboardDefaults.newProfile(id = "invalid-scale-incomplete").copy(
            portrait = DashboardLayout(
                columns = 2,
                widgets = listOf(base.copy(scaleMinimum = 0.0, scaleMaximum = null)),
            ),
        )
        val reversed = DashboardDefaults.newProfile(id = "invalid-scale-reversed").copy(
            portrait = DashboardLayout(
                columns = 2,
                widgets = listOf(base.copy(scaleMinimum = 200.0, scaleMaximum = 0.0)),
            ),
        )

        assertTrue(DashboardCodec.import(DashboardCodec.export(incomplete)).isFailure)
        assertTrue(DashboardCodec.import(DashboardCodec.export(reversed)).isFailure)
    }

    @Test
    fun importRejectsInvalidDigitalRingSegmentCount()""",
)
