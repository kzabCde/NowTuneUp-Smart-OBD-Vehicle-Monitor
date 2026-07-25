package com.nowtuneup.app.util

import com.nowtuneup.app.domain.model.DisplayUnit
import com.nowtuneup.app.domain.model.ReadingStats
import com.nowtuneup.app.domain.model.VehicleReading

object DisplayReadingAdapter {
    fun nativeUnit(unit: String): DisplayUnit = when (unit.trim().lowercase()) {
        "rpm" -> DisplayUnit.RPM
        "km/h", "kmh" -> DisplayUnit.KMH
        "mph" -> DisplayUnit.MPH
        "°c", "c", "celsius" -> DisplayUnit.CELSIUS
        "°f", "f", "fahrenheit" -> DisplayUnit.FAHRENHEIT
        "v", "volt", "volts" -> DisplayUnit.VOLT
        "%", "percent" -> DisplayUnit.PERCENT
        "kpa" -> DisplayUnit.KPA
        "bar" -> DisplayUnit.BAR
        "psi" -> DisplayUnit.PSI
        "l", "liter", "litre" -> DisplayUnit.LITER
        "gal", "gallon" -> DisplayUnit.GALLON
        else -> DisplayUnit.NONE
    }

    fun reading(reading: VehicleReading?, target: DisplayUnit): VehicleReading? {
        reading ?: return null
        val source = nativeUnit(reading.unit)
        if (target == DisplayUnit.NONE || source == DisplayUnit.NONE || source == target) return reading
        return reading.copy(
            value = reading.value?.let { UnitConverter.convert(it, source, target) },
            unit = target.label(),
            minimum = reading.minimum?.let { UnitConverter.convert(it, source, target) },
            maximum = reading.maximum?.let { UnitConverter.convert(it, source, target) },
        )
    }

    fun stats(stats: ReadingStats?, sourceUnit: String, target: DisplayUnit): ReadingStats? {
        stats ?: return null
        val source = nativeUnit(sourceUnit)
        if (target == DisplayUnit.NONE || source == DisplayUnit.NONE || source == target) return stats
        return stats.copy(
            minimum = stats.minimum?.let { UnitConverter.convert(it, source, target) },
            maximum = stats.maximum?.let { UnitConverter.convert(it, source, target) },
            peak = stats.peak?.let { UnitConverter.convert(it, source, target) },
        )
    }
}

fun DisplayUnit.label(): String = when (this) {
    DisplayUnit.RPM -> "rpm"
    DisplayUnit.KMH -> "km/h"
    DisplayUnit.MPH -> "mph"
    DisplayUnit.CELSIUS -> "°C"
    DisplayUnit.FAHRENHEIT -> "°F"
    DisplayUnit.VOLT -> "V"
    DisplayUnit.PERCENT -> "%"
    DisplayUnit.KPA -> "kPa"
    DisplayUnit.BAR -> "bar"
    DisplayUnit.PSI -> "PSI"
    DisplayUnit.LITER -> "L"
    DisplayUnit.GALLON -> "gal"
    DisplayUnit.NONE -> ""
}
