package com.nowtuneup.app.data.dashboard

import com.nowtuneup.app.data.obd.pid.DerivedPids
import com.nowtuneup.app.domain.model.ColorConfig
import com.nowtuneup.app.domain.model.DashboardConfig
import com.nowtuneup.app.domain.model.DashboardLayout
import com.nowtuneup.app.domain.model.DashboardMode
import com.nowtuneup.app.domain.model.DashboardWidgetConfig
import com.nowtuneup.app.domain.model.DashboardWidgetType
import com.nowtuneup.app.domain.model.DigitalRingColorPreset
import com.nowtuneup.app.domain.model.DisplayUnit
import com.nowtuneup.app.domain.model.GaugeStyle
import com.nowtuneup.app.domain.model.ThemeConfig
import com.nowtuneup.app.domain.model.WarningThreshold
import com.nowtuneup.app.domain.model.digitalRingPreset
import com.nowtuneup.app.domain.model.premiumGaugePreset

/**
 * Catalogs used by the profile editor.
 *
 * Version 1.6.2 intentionally does not ship a ready-made dashboard. A new profile starts empty and
 * the user chooses every reading, layout and colour. The legacy ids are retained only so old built-in
 * profiles can be converted into normal user profiles without silently discarding saved edits.
 */
object DashboardDefaults {
    val legacyPresetIds: Set<String> = setOf(
        "daily",
        "sport",
        "hud",
        "health",
        "diagnostic",
        "minimal",
        "night",
    )

    private fun widget(
        id: String,
        pid: Int,
        title: String,
        unit: DisplayUnit,
        type: DashboardWidgetType,
        gaugeStyle: GaugeStyle,
        columnSpan: Int = 1,
        rowSpan: Int = 1,
        valueSize: Int = 34,
        threshold: WarningThreshold = WarningThreshold(),
    ): DashboardWidgetConfig {
        val premium = premiumGaugePreset(gaugeStyle)
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
        return DashboardWidgetConfig(
            id = id,
            pid = pid,
            type = type,
            title = title,
            unit = unit,
            row = 0,
            columnSpan = columnSpan,
            rowSpan = rowSpan,
            valueSize = valueSize,
            gaugeStyle = gaugeStyle,
            bezelFinish = premium.bezelFinish,
            scaleMinimum = defaultScale?.first,
            scaleMaximum = defaultScale?.second,
            colors = ColorConfig(
                value = premium.value,
                label = premium.label,
                background = premium.face,
                border = premium.bezel,
                warning = premium.warning,
                critical = premium.critical,
                face = premium.face,
                bezel = premium.bezel,
                tick = premium.tick,
                needle = premium.needle,
                needleHighlight = premium.needleHighlight,
                glow = premium.glow,
            ),
            threshold = threshold,
        )
    }

    /** Individual readings that users may add to their own profile. This is not a dashboard preset. */
    val widgetCatalog: List<DashboardWidgetConfig> = listOf(
        widget(
            id = "rpm",
            pid = 0x0C,
            title = "รอบเครื่อง",
            unit = DisplayUnit.RPM,
            type = DashboardWidgetType.ANALOG,
            gaugeStyle = GaugeStyle.SPORT_RED,
            rowSpan = 2,
            valueSize = 42,
        ),
        widget(
            id = "speed",
            pid = 0x0D,
            title = "ความเร็ว",
            unit = DisplayUnit.KMH,
            type = DashboardWidgetType.DIGITAL,
            gaugeStyle = GaugeStyle.NEO_CYAN,
            valueSize = 56,
        ),
        widget(
            id = "coolant",
            pid = 0x05,
            title = "อุณหภูมิน้ำหล่อเย็น",
            unit = DisplayUnit.CELSIUS,
            type = DashboardWidgetType.MINI_GAUGE,
            gaugeStyle = GaugeStyle.OEM_BLUE,
            threshold = WarningThreshold(warningHigh = 105.0, criticalHigh = 115.0),
        ),
        widget(
            id = "voltage",
            pid = 0x42,
            title = "แรงดันแบตเตอรี่",
            unit = DisplayUnit.VOLT,
            type = DashboardWidgetType.MINI_GAUGE,
            gaugeStyle = GaugeStyle.RACING_AMBER,
            threshold = WarningThreshold(
                warningLow = 12.0,
                warningHigh = 15.0,
                criticalLow = 11.0,
                criticalHigh = 16.0,
            ),
        ),
        widget(
            id = "engine-load",
            pid = 0x04,
            title = "ภาระเครื่องยนต์",
            unit = DisplayUnit.PERCENT,
            type = DashboardWidgetType.PROGRESS,
            gaugeStyle = GaugeStyle.NEO_CYAN,
        ),
        widget(
            id = "throttle",
            pid = 0x11,
            title = "ตำแหน่งคันเร่ง",
            unit = DisplayUnit.PERCENT,
            type = DashboardWidgetType.DIGITAL,
            gaugeStyle = GaugeStyle.HUD_GREEN,
        ),
        widget(
            id = "turbo-pressure",
            pid = DerivedPids.TURBO_PRESSURE,
            title = "แรงดันเทอร์โบ",
            unit = DisplayUnit.PSI,
            type = DashboardWidgetType.DIGITAL_RING,
            gaugeStyle = GaugeStyle.NEO_CYAN,
            columnSpan = 2,
            rowSpan = 2,
            valueSize = 58,
        ).copy(digitalRing = digitalRingPreset(DigitalRingColorPreset.CYAN, segmentCount = 48)),
        widget(
            id = "intake-temperature",
            pid = 0x0F,
            title = "อุณหภูมิอากาศเข้า",
            unit = DisplayUnit.CELSIUS,
            type = DashboardWidgetType.DIGITAL,
            gaugeStyle = GaugeStyle.OEM_BLUE,
        ),
        widget(
            id = "intake-pressure",
            pid = 0x0B,
            title = "ความดันท่อร่วมไอดี",
            unit = DisplayUnit.KPA,
            type = DashboardWidgetType.DIGITAL,
            gaugeStyle = GaugeStyle.NEO_CYAN,
        ),
        widget(
            id = "fuel-level",
            pid = 0x2F,
            title = "ระดับน้ำมันเชื้อเพลิง",
            unit = DisplayUnit.PERCENT,
            type = DashboardWidgetType.PROGRESS,
            gaugeStyle = GaugeStyle.HUD_GREEN,
        ),
        widget(
            id = "dtc-status",
            pid = -1,
            title = "สถานะรหัสปัญหา",
            unit = DisplayUnit.NONE,
            type = DashboardWidgetType.DTC_CARD,
            gaugeStyle = GaugeStyle.SPORT_RED,
            columnSpan = 2,
        ),
    )

    fun newProfile(
        id: String = "profile-${System.currentTimeMillis()}",
        name: String = "โปรไฟล์ใหม่",
    ): DashboardConfig = DashboardConfig(
        id = id,
        name = name,
        mode = DashboardMode.HYBRID,
        portrait = DashboardLayout(columns = 2, widgets = emptyList()),
        landscape = DashboardLayout(columns = 4, widgets = emptyList()),
        isDefault = false,
    )

    val themes = listOf(
        ThemeConfig(name = "ดำเรียบง่าย"),
        ThemeConfig(
            name = "สปอร์ตแดง",
            primary = 0xFFFF1744,
            accent = 0xFFFF1744,
            background = 0xFF08080A,
            card = 0xFF121214,
            gaugeNeedle = 0xFFFF1744,
            gaugeTick = 0xFFFFE8E8,
        ),
        ThemeConfig(
            name = "ฟ้านีออน",
            primary = 0xFF35E6FF,
            accent = 0xFF35E6FF,
            background = 0xFF041017,
            card = 0xFF091820,
            gaugeNeedle = 0xFF35E6FF,
            gaugeTick = 0xFFBDEFFF,
        ),
        ThemeConfig(
            name = "ส้มกลางคืน",
            primary = 0xFFFFC247,
            accent = 0xFFFFC247,
            background = 0xFF0D0903,
            card = 0xFF171106,
            gaugeNeedle = 0xFFFFD166,
            gaugeTick = 0xFFFFE5A5,
        ),
        ThemeConfig(
            name = "น้ำเงินพรีเมียม",
            primary = 0xFF5AAEFF,
            accent = 0xFF5AAEFF,
            background = 0xFF06101D,
            card = 0xFF0B1928,
            gaugeNeedle = 0xFF5AAEFF,
            gaugeTick = 0xFFD7EBFF,
        ),
        ThemeConfig(
            name = "เขียว HUD",
            primary = 0xFF48FF8A,
            accent = 0xFF48FF8A,
            background = 0xFF020806,
            card = 0xFF06110C,
            gaugeNeedle = 0xFF48FF8A,
            gaugeTick = 0xFFB9FFD0,
        ),
    )
}
