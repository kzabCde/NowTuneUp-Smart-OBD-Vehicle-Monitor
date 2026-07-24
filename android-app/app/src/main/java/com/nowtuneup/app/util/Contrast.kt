package com.nowtuneup.app.util

object Contrast {
    fun ratio(first: Long, second: Long): Double {
        fun luminance(color: Long): Double {
            fun channel(shift: Int): Double {
                val component = ((color shr shift) and 0xFF) / 255.0
                return if (component <= .03928) component / 12.92 else Math.pow((component + .055) / 1.055, 2.4)
            }
            return .2126 * channel(16) + .7152 * channel(8) + .0722 * channel(0)
        }
        val a = luminance(first)
        val b = luminance(second)
        return (maxOf(a, b) + .05) / (minOf(a, b) + .05)
    }

    fun isReadable(text: Long, background: Long) = ratio(text, background) >= 4.5
}
