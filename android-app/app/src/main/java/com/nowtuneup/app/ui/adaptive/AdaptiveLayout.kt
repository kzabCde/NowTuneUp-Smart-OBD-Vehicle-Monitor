package com.nowtuneup.app.ui.adaptive

import com.nowtuneup.app.domain.model.AdaptiveLayoutProfile

enum class ResolvedDeviceLayout { PHONE, TABLET, HEAD_UNIT }

object AdaptiveLayoutResolver {
    fun resolve(
        requested: AdaptiveLayoutProfile,
        screenWidthDp: Int,
        screenHeightDp: Int,
        smallestWidthDp: Int,
    ): ResolvedDeviceLayout = when (requested) {
        AdaptiveLayoutProfile.PHONE -> ResolvedDeviceLayout.PHONE
        AdaptiveLayoutProfile.TABLET -> ResolvedDeviceLayout.TABLET
        AdaptiveLayoutProfile.HEAD_UNIT -> ResolvedDeviceLayout.HEAD_UNIT
        AdaptiveLayoutProfile.AUTO -> when {
            screenWidthDp >= 900 && screenWidthDp > screenHeightDp -> ResolvedDeviceLayout.HEAD_UNIT
            smallestWidthDp >= 600 -> ResolvedDeviceLayout.TABLET
            else -> ResolvedDeviceLayout.PHONE
        }
    }

    fun dashboardColumns(baseColumns: Int, layout: ResolvedDeviceLayout, landscape: Boolean): Int {
        val minimum = when (layout) {
            ResolvedDeviceLayout.PHONE -> if (landscape) 2 else 1
            ResolvedDeviceLayout.TABLET -> if (landscape) 4 else 3
            ResolvedDeviceLayout.HEAD_UNIT -> 4
        }
        return maxOf(baseColumns, minimum).coerceIn(1, 6)
    }
}
