package com.nowtuneup.app.data.dashboard

import com.nowtuneup.app.domain.model.DashboardMode
import com.nowtuneup.app.domain.model.DashboardWidgetType
import org.junit.Assert.*
import org.junit.Test

class DashboardCodecTest {
    @Test fun exportImportRestoresDashboardAndCustomColors() {
        val original = DashboardDefaults.presets.first()
        val restored = DashboardCodec.import(DashboardCodec.export(original)).getOrThrow()
        assertEquals(original, restored)
        assertEquals(original.portrait.widgets.first().colors, restored.portrait.widgets.first().colors)
    }

    @Test fun digitalCanBecomeAnalogWithoutChangingParameter() {
        val widget = DashboardDefaults.presets.first().portrait.widgets[1]
        val analog = widget.copy(type = DashboardWidgetType.ANALOG)
        assertEquals(widget.pid, analog.pid)
        assertEquals(DashboardWidgetType.ANALOG, analog.type)
    }

    @Test fun invalidImportIsRejected() {
        val bad = DashboardDefaults.presets.first().copy(mode = DashboardMode.DIGITAL, portrait = DashboardDefaults.presets.first().portrait.copy(columns = 0))
        assertTrue(DashboardCodec.import(DashboardCodec.export(bad)).isFailure)
    }
}
