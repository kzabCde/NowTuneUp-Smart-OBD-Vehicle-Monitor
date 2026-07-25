package com.nowtuneup.app.ui.adaptive

import com.nowtuneup.app.domain.model.AdaptiveLayoutProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveLayoutTest {
    @Test
    fun autoSelectsPhoneForCompactDisplay() {
        assertEquals(
            ResolvedDeviceLayout.PHONE,
            AdaptiveLayoutResolver.resolve(AdaptiveLayoutProfile.AUTO, 412, 915, 412),
        )
    }

    @Test
    fun autoSelectsTabletForLargePortraitDisplay() {
        assertEquals(
            ResolvedDeviceLayout.TABLET,
            AdaptiveLayoutResolver.resolve(AdaptiveLayoutProfile.AUTO, 800, 1280, 800),
        )
    }

    @Test
    fun autoSelectsHeadUnitForWideDisplay() {
        assertEquals(
            ResolvedDeviceLayout.HEAD_UNIT,
            AdaptiveLayoutResolver.resolve(AdaptiveLayoutProfile.AUTO, 1280, 480, 480),
        )
    }

    @Test
    fun headUnitUsesAtLeastFourColumns() {
        assertEquals(4, AdaptiveLayoutResolver.dashboardColumns(2, ResolvedDeviceLayout.HEAD_UNIT, true))
    }
}
