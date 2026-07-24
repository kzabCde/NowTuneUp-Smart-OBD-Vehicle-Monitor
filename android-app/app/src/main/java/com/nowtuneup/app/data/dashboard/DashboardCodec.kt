package com.nowtuneup.app.data.dashboard

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.nowtuneup.app.domain.model.DashboardConfig

object DashboardCodec {
    private val gson = Gson()
    fun export(config: DashboardConfig): String = gson.toJson(config)
    fun import(json: String): Result<DashboardConfig> = runCatching {
        require(json.length <= 1_000_000) { "Configuration is too large" }
        val config = gson.fromJson(json, DashboardConfig::class.java) ?: throw JsonParseException("Empty configuration")
        require(config.id.isNotBlank() && config.name.isNotBlank()) { "Dashboard id and name are required" }
        require(config.portrait.columns in 1..6 && config.landscape.columns in 1..6) { "Columns must be between 1 and 6" }
        require((config.portrait.widgets + config.landscape.widgets).all { it.decimals in 0..3 && it.columnSpan in 1..6 }) { "Invalid widget configuration" }
        config
    }
}
