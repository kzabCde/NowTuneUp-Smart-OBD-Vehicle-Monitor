package com.nowtuneup.app.util

import com.nowtuneup.app.domain.model.DisplayUnit
import com.nowtuneup.app.domain.model.WarningThreshold

object UnitConverter {
    fun convert(value: Double, from: DisplayUnit, to: DisplayUnit): Double = when (from to to) {
        DisplayUnit.KMH to DisplayUnit.MPH -> value * 0.621371192
        DisplayUnit.MPH to DisplayUnit.KMH -> value / 0.621371192
        DisplayUnit.CELSIUS to DisplayUnit.FAHRENHEIT -> value * 9 / 5 + 32
        DisplayUnit.FAHRENHEIT to DisplayUnit.CELSIUS -> (value - 32) * 5 / 9
        DisplayUnit.KPA to DisplayUnit.BAR -> value / 100
        DisplayUnit.BAR to DisplayUnit.KPA -> value * 100
        DisplayUnit.KPA to DisplayUnit.PSI -> value * 0.145037738
        DisplayUnit.PSI to DisplayUnit.KPA -> value / 0.145037738
        DisplayUnit.LITER to DisplayUnit.GALLON -> value * 0.264172052
        DisplayUnit.GALLON to DisplayUnit.LITER -> value / 0.264172052
        else -> value
    }

    fun convert(threshold: WarningThreshold, from: DisplayUnit, to: DisplayUnit) = WarningThreshold(
        threshold.warningLow?.let { convert(it, from, to) },
        threshold.warningHigh?.let { convert(it, from, to) },
        threshold.criticalLow?.let { convert(it, from, to) },
        threshold.criticalHigh?.let { convert(it, from, to) },
    )
}
