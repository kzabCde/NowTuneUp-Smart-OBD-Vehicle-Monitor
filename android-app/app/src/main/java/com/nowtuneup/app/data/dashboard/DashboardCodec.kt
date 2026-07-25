package com.nowtuneup.app.data.dashboard

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.nowtuneup.app.domain.model.DashboardConfig

object DashboardCodec {
    private const val MAX_JSON_BYTES = 1_000_000
    private const val MAX_WIDGETS_PER_LAYOUT = 50
    private val gson = Gson()

    fun export(config: DashboardConfig): String = gson.toJson(config)

    fun import(json: String): Result<DashboardConfig> = runCatching {
        require(json.toByteArray().size <= MAX_JSON_BYTES) { "Configuration is too large" }
        val config = gson.fromJson(json, DashboardConfig::class.java)
            ?: throw JsonParseException("Empty configuration")

        require(config.id.isNotBlank() && config.name.isNotBlank()) {
            "Dashboard id and name are required"
        }
        require(config.portrait.columns in 1..6 && config.landscape.columns in 1..6) {
            "Columns must be between 1 and 6"
        }
        require(
            config.portrait.widgets.size <= MAX_WIDGETS_PER_LAYOUT &&
                config.landscape.widgets.size <= MAX_WIDGETS_PER_LAYOUT,
        ) { "Dashboard contains too many widgets" }

        val widgets = config.portrait.widgets + config.landscape.widgets
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

        config
    }
}
