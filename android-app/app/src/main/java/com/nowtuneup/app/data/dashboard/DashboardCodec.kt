package com.nowtuneup.app.data.dashboard

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.nowtuneup.app.domain.model.DashboardConfig
import com.nowtuneup.app.domain.model.DashboardLayout
import com.nowtuneup.app.domain.model.DigitalRingColorPreset
import com.nowtuneup.app.domain.model.GaugeStyle
import com.nowtuneup.app.domain.model.HUD_CANVAS_COLUMNS
import com.nowtuneup.app.domain.model.HUD_CANVAS_ROWS
import com.nowtuneup.app.domain.model.HUD_SCHEMA_VERSION
import kotlin.math.roundToInt

object DashboardCodec {
    private const val MAX_JSON_BYTES = 1_000_000
    private const val MAX_WIDGETS_PER_LAYOUT = 64
    private val gson = Gson()

    fun export(config: DashboardConfig): String = gson.toJson(
        config.copy(schemaVersion = HUD_SCHEMA_VERSION, isDefault = false),
    )

    fun import(json: String): Result<DashboardConfig> = runCatching {
        require(json.toByteArray().size <= MAX_JSON_BYTES) { "Configuration is too large" }
        val root = JsonParser.parseString(json).asJsonObject
        migrateLegacyValues(root)
        migrateCanvas(root)

        val config = gson.fromJson(root, DashboardConfig::class.java)
            ?: throw JsonParseException("Empty configuration")

        require(config.id.isNotBlank() && config.name.isNotBlank()) {
            "Dashboard id and name are required"
        }
        require(config.schemaVersion == HUD_SCHEMA_VERSION) {
            "Unsupported HUD schema version"
        }
        validateLayout(config.portrait)
        validateLayout(config.landscape)
        config
    }

    private fun validateLayout(layout: DashboardLayout) {
        require(layout.columns in 4..24 && layout.rows in 8..48) {
            "HUD canvas dimensions are invalid"
        }
        require(layout.widgets.size <= MAX_WIDGETS_PER_LAYOUT) {
            "Dashboard contains too many widgets"
        }
        require(layout.widgets.map { it.id }.all { it.isNotBlank() }) {
            "Widget ids are required"
        }
        require(layout.widgets.map { it.id }.distinct().size == layout.widgets.size) {
            "Widget ids must be unique inside a layout"
        }

        require(layout.widgets.all { widget ->
            val ringIsValid = widget.digitalRing?.let { ring -> ring.segmentCount in 12..72 } ?: true
            val scaleMinimum = widget.scaleMinimum
            val scaleMaximum = widget.scaleMaximum
            val scaleIsValid = when {
                scaleMinimum == null && scaleMaximum == null -> true
                scaleMinimum == null || scaleMaximum == null -> false
                !scaleMinimum.isFinite() || !scaleMaximum.isFinite() -> false
                else -> scaleMaximum > scaleMinimum
            }
            val geometryIsValid =
                widget.canvasWidth in 1..layout.columns &&
                    widget.canvasHeight in 1..layout.rows &&
                    widget.canvasX in 0 until layout.columns &&
                    widget.canvasY in 0 until layout.rows &&
                    widget.canvasX + widget.canvasWidth <= layout.columns &&
                    widget.canvasY + widget.canvasHeight <= layout.rows

            widget.title.isNotBlank() &&
                widget.decimals in 0..3 &&
                widget.valueSize in 16..96 &&
                widget.opacity.isFinite() &&
                widget.opacity in 0.20f..1f &&
                widget.zIndex in 0..10_000 &&
                geometryIsValid &&
                ringIsValid &&
                scaleIsValid
        }) { "Invalid widget configuration" }
    }

    /**
     * Convert the old ordered-grid dashboard into the v2 freeform HUD canvas.
     *
     * We preserve every saved widget and approximate its old span while packing it from top-left.
     * After the first save the profile is permanently stored with explicit canvas geometry.
     */
    private fun migrateCanvas(root: JsonObject) {
        listOf("portrait", "landscape").forEach { layoutName ->
            val layout = root.getAsJsonObject(layoutName) ?: return@forEach
            val oldColumns = layout.intOrNull("columns")?.coerceIn(1, 6) ?: 2
            val widgets = layout.getAsJsonArray("widgets")

            layout.addProperty("columns", HUD_CANVAS_COLUMNS)
            if (!layout.has("rows")) layout.addProperty("rows", HUD_CANVAS_ROWS)
            if (!layout.has("snapToGrid")) layout.addProperty("snapToGrid", true)

            var cursorX = 0
            var cursorY = 0
            var currentRowHeight = 0

            widgets?.forEachIndexed { index, element ->
                val widget = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEachIndexed
                val oldSpan = widget.intOrNull("columnSpan")?.coerceIn(1, oldColumns) ?: 1
                val type = widget.stringOrNull("type")
                val defaultWidth = ((oldSpan.toFloat() / oldColumns) * HUD_CANVAS_COLUMNS)
                    .roundToInt()
                    .coerceIn(3, HUD_CANVAS_COLUMNS)
                val defaultHeight = when (type) {
                    "ANALOG" -> 7
                    "MINI_GAUGE" -> 5
                    "DIGITAL_RING" -> 7
                    "PROGRESS" -> 3
                    "LINE_CHART" -> 5
                    "DTC_CARD" -> 4
                    else -> 4
                }.coerceAtMost(HUD_CANVAS_ROWS)

                val width = widget.intOrNull("canvasWidth")
                    ?.takeIf { it > 0 }
                    ?.coerceIn(1, HUD_CANVAS_COLUMNS)
                    ?: defaultWidth
                val height = widget.intOrNull("canvasHeight")
                    ?.takeIf { it > 0 }
                    ?.coerceIn(1, HUD_CANVAS_ROWS)
                    ?: defaultHeight

                if (cursorX + width > HUD_CANVAS_COLUMNS) {
                    cursorX = 0
                    cursorY += currentRowHeight.coerceAtLeast(1)
                    currentRowHeight = 0
                }
                if (cursorY + height > HUD_CANVAS_ROWS) {
                    cursorY = (HUD_CANVAS_ROWS - height).coerceAtLeast(0)
                }

                if (!widget.has("canvasX")) widget.addProperty("canvasX", cursorX)
                if (!widget.has("canvasY")) widget.addProperty("canvasY", cursorY)
                if (!widget.has("canvasWidth")) widget.addProperty("canvasWidth", width)
                if (!widget.has("canvasHeight")) widget.addProperty("canvasHeight", height)
                if (!widget.has("zIndex")) widget.addProperty("zIndex", index)
                if (!widget.has("locked")) widget.addProperty("locked", false)
                if (!widget.has("showLabel")) widget.addProperty("showLabel", true)
                if (!widget.has("showUnit")) widget.addProperty("showUnit", true)
                if (!widget.has("opacity")) widget.addProperty("opacity", 1f)

                cursorX += width
                currentRowHeight = maxOf(currentRowHeight, height)
            }
        }
        root.addProperty("schemaVersion", HUD_SCHEMA_VERSION)
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

    private fun JsonObject.intOrNull(name: String): Int? =
        get(name)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asInt }.getOrNull() }

    private fun JsonObject.stringOrNull(name: String): String? =
        get(name)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asString }.getOrNull() }
}
