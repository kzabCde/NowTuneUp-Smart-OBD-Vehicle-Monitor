package com.nowtuneup.app.data.dashboard

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.nowtuneup.app.domain.model.DashboardConfig
import com.nowtuneup.app.domain.model.DashboardLayout
import com.nowtuneup.app.domain.model.DashboardWidgetConfig
import com.nowtuneup.app.domain.model.DashboardWidgetType
import com.nowtuneup.app.domain.model.DigitalRingColorPreset
import com.nowtuneup.app.domain.model.GaugeStyle

object DashboardCodec {
    private const val MAX_JSON_BYTES = 1_000_000
    private const val MAX_WIDGETS_PER_LAYOUT = 50
    private val gson = Gson()

    fun export(config: DashboardConfig): String = gson.toJson(config.normalized())

    fun import(json: String): Result<DashboardConfig> = runCatching {
        require(json.toByteArray().size <= MAX_JSON_BYTES) { "Configuration is too large" }
        val root = JsonParser.parseString(json).asJsonObject
        migrateLegacyValues(root)
        val config = gson.fromJson(root, DashboardConfig::class.java)
            ?: throw JsonParseException("Empty configuration")
        val normalized = config.normalized()

        require(normalized.id.isNotBlank() && normalized.name.isNotBlank()) {
            "Dashboard id and name are required"
        }
        require(normalized.portrait.columns in 1..6 && normalized.landscape.columns in 1..6) {
            "Columns must be between 1 and 6"
        }
        require(
            normalized.portrait.widgets.size <= MAX_WIDGETS_PER_LAYOUT &&
                normalized.landscape.widgets.size <= MAX_WIDGETS_PER_LAYOUT,
        ) { "Dashboard contains too many widgets" }

        val widgets = normalized.portrait.widgets + normalized.landscape.widgets
        require(widgets.map { it.id }.all { it.isNotBlank() }) { "Widget ids are required" }
        require(widgets.all { widget ->
            val ringIsValid = widget.digitalRing?.let { ring ->
                ring.segmentCount in 12..72
            } ?: true
            widget.title.isNotBlank() &&
                widget.decimals in 0..3 &&
                widget.valueSize in 20..80 &&
                widget.columnSpan in 1..6 &&
                widget.rowSpan in 1..4 &&
                ringIsValid
        }) { "Invalid widget configuration" }

        normalized
    }

    private fun migrateLegacyValues(root: JsonObject) {
        listOf("portrait", "landscape").forEach { layoutName ->
            val widgets = root.getAsJsonObject(layoutName)?.getAsJsonArray("widgets") ?: return@forEach
            widgets.forEach { element ->
                val widget = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
                val rawStyle = widget.get("gaugeStyle")?.takeIf { it.isJsonPrimitive }?.asString
                widget.addProperty("gaugeStyle", migrateGaugeStyle(rawStyle).name)

                widget.getAsJsonObject("digitalRing")?.let { ring ->
                    val rawPreset = ring.get("preset")?.takeIf { it.isJsonPrimitive }?.asString
                    val preset = runCatching { DigitalRingColorPreset.valueOf(rawPreset.orEmpty()) }
                        .getOrDefault(DigitalRingColorPreset.CYAN)
                    ring.addProperty("preset", preset.name)
                }
            }
        }
    }

    private fun migrateGaugeStyle(raw: String?): GaugeStyle = when (raw) {
        "CLASSIC" -> GaugeStyle.CLASSIC_METAL
        "SPORT" -> GaugeStyle.SPORT_RED
        "MINIMAL" -> GaugeStyle.CLASSIC_METAL
        "NEON" -> GaugeStyle.NEO_CYAN
        "OEM" -> GaugeStyle.OEM_BLUE
        else -> runCatching { GaugeStyle.valueOf(raw.orEmpty()) }.getOrDefault(GaugeStyle.CLASSIC_METAL)
    }

    private fun DashboardConfig.normalized(): DashboardConfig = copy(
        portrait = portrait.normalized(),
        landscape = landscape.normalized(),
    )

    private fun DashboardLayout.normalized(): DashboardLayout = copy(
        columns = columns.coerceIn(1, 6),
        widgets = widgets.map(DashboardWidgetConfig::normalized),
    )

    private fun DashboardWidgetConfig.normalized(): DashboardWidgetConfig = copy(
        decimals = decimals.coerceIn(0, 3),
        valueSize = valueSize.coerceIn(20, 80),
        columnSpan = columnSpan.coerceIn(1, 6),
        rowSpan = rowSpan.coerceIn(1, 4),
        gaugeStyle = gaugeStyle,
        digitalRing = digitalRing?.copy(segmentCount = digitalRing.segmentCount.coerceIn(12, 72)),
        showPeakMarker = showPeakMarker && type in setOf(DashboardWidgetType.ANALOG, DashboardWidgetType.MINI_GAUGE),
    )
}
