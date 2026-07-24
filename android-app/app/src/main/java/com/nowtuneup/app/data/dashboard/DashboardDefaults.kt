package com.nowtuneup.app.data.dashboard

import com.nowtuneup.app.domain.model.*

object DashboardDefaults {
    private fun widget(id: String, pid: Int, title: String, unit: DisplayUnit, type: DashboardWidgetType, row: Int) =
        DashboardWidgetConfig(id, pid, type, title, unit, row = row, threshold = when (pid) {
            0x05 -> WarningThreshold(warningHigh = 105.0, criticalHigh = 115.0)
            0x42 -> WarningThreshold(warningLow = 12.0, warningHigh = 15.0, criticalLow = 11.0, criticalHigh = 16.0)
            else -> WarningThreshold()
        })

    private val core = listOf(
        widget("rpm", 0x0C, "RPM", DisplayUnit.RPM, DashboardWidgetType.ANALOG, 0),
        widget("speed", 0x0D, "Speed", DisplayUnit.KMH, DashboardWidgetType.DIGITAL, 0),
        widget("coolant", 0x05, "Coolant", DisplayUnit.CELSIUS, DashboardWidgetType.MINI_GAUGE, 1),
        widget("voltage", 0x42, "Voltage", DisplayUnit.VOLT, DashboardWidgetType.MINI_GAUGE, 1),
        widget("load", 0x04, "Engine load", DisplayUnit.PERCENT, DashboardWidgetType.PROGRESS, 2),
        widget("throttle", 0x11, "Throttle", DisplayUnit.PERCENT, DashboardWidgetType.DIGITAL, 2),
    )

    val presets = listOf(
        DashboardConfig("daily", "Daily Driving", DashboardMode.HYBRID, DashboardLayout(widgets = core), isDefault = true),
        DashboardConfig("sport", "Sport", DashboardMode.ANALOG, DashboardLayout(widgets = core.take(2).map { it.copy(type = DashboardWidgetType.ANALOG, gaugeStyle = GaugeStyle.SPORT) })),
        DashboardConfig("health", "Engine Health", DashboardMode.HYBRID, DashboardLayout(widgets = core.filter { it.pid in setOf(0x05, 0x42, 0x04, 0x11) })),
        DashboardConfig("diagnostic", "Diagnostic", DashboardMode.DIGITAL, DashboardLayout(widgets = core + widget("dtc", -1, "DTC status", DisplayUnit.NONE, DashboardWidgetType.DTC_CARD, 3))),
        DashboardConfig("minimal", "Minimal", DashboardMode.DIGITAL, DashboardLayout(1, core.take(2).map { it.copy(type = DashboardWidgetType.DIGITAL) })),
        DashboardConfig("night", "Night Driving", DashboardMode.ANALOG, DashboardLayout(widgets = core.take(4).map { it.copy(type = DashboardWidgetType.ANALOG, gaugeStyle = GaugeStyle.NEON) })),
    )

    val themes = listOf(
        ThemeConfig(),
        ThemeConfig("Light", background = 0xFFF4F6F8, card = 0xFFFFFFFF, text = 0xFF101820, border = 0xFFB0BEC5),
        ThemeConfig("AMOLED Black", background = 0xFF000000, card = 0xFF050505),
        ThemeConfig("Classic Automotive", primary = 0xFFFFB300, accent = 0xFFFFB300),
        ThemeConfig("Racing Red", primary = 0xFFFF1744, accent = 0xFFFF1744, gaugeNeedle = 0xFFFF1744),
        ThemeConfig("Electric Blue", primary = 0xFF2979FF, accent = 0xFF2979FF, gaugeNeedle = 0xFF2979FF),
        ThemeConfig("Neon", primary = 0xFF39FF14, accent = 0xFFFF00FF, gaugeNeedle = 0xFFFF00FF),
        ThemeConfig("OEM Gray", primary = 0xFFB0BEC5, accent = 0xFF90A4AE, card = 0xFF20252A),
    )
}
