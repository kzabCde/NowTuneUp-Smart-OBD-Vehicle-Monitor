package com.nowtuneup.app.data.dashboard

import com.nowtuneup.app.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class PrimaryGaugeMigrationTest {
    @Test fun legacyPresentationsMapToOnlyTwoGaugesWithoutLosingConfiguration() {
        val mappings = mapOf(DashboardWidgetType.DIGITAL to PrimaryGaugePresentation.DIGITAL,
            DashboardWidgetType.DIGITAL_RING to PrimaryGaugePresentation.DIGITAL,
            DashboardWidgetType.PROGRESS to PrimaryGaugePresentation.DIGITAL,
            DashboardWidgetType.ANALOG to PrimaryGaugePresentation.DIAL,
            DashboardWidgetType.MINI_GAUGE to PrimaryGaugePresentation.DIAL)
        mappings.forEach { (type,presentation) ->
            val widget = DashboardDefaults.widgetCatalog.first().copy(type = type,
                id="stable-widget", digitalRing=DigitalRingConfig(segmentCount=48),
                canvasX=1,canvasY=2,canvasWidth=5,canvasHeight=7,
                colors=ColorConfig(value=0xFF123456),threshold=WarningThreshold(warningHigh=5000.0))
            val profile=DashboardDefaults.newProfile(id="stable-profile").copy(portrait=DashboardLayout(widgets=listOf(widget)))
            val reopened=DashboardCodec.import(DashboardCodec.export(profile)).getOrThrow()
            assertEquals(profile,reopened)
            assertEquals(presentation,reopened.portrait.widgets.single().type.primaryGaugePresentation())
        }
        assertEquals(listOf(DashboardWidgetType.ANALOG,DashboardWidgetType.DIGITAL),primaryGaugeTypes)
        assertNull(DashboardWidgetType.LINE_CHART.primaryGaugePresentation())
    }
    @Test fun bothGaugesUseTheSameUnavailableAndThresholdBinding() {
        val widget=DashboardDefaults.widgetCatalog.first().copy(threshold=WarningThreshold(warningHigh=4000.0,criticalHigh=6000.0))
        val reading=VehicleReading(0x0C,"RPM",4500.0,"rpm")
        assertEquals(AlertSeverity.WARNING,bindTelemetryGauge(widget,reading).severity)
        listOf(DataFreshness.STALE,DataFreshness.NO_DATA,DataFreshness.UNSUPPORTED,DataFreshness.RECONNECTING).forEach {
            assertNull(bindTelemetryGauge(widget,reading,freshness=it).value)
        }
        assertNull(bindTelemetryGauge(widget,reading.copy(value=Double.POSITIVE_INFINITY)).value)
        assertEquals(0.0,bindTelemetryGauge(widget,reading.copy(value=0.0)).value!!,0.0)
    }
    @Test fun removingResizingAndLayeringPreserveStableIdsAcrossReopen() {
        val first=DashboardDefaults.widgetCatalog.first().copy(id="first",canvasX=9,canvasY=20,canvasWidth=3,canvasHeight=4)
        val second=first.copy(id="second",canvasX=0,canvasY=0,zIndex=1,locked=true)
        val layout=DashboardLayout(widgets=listOf(first,second))
        val resized=layout.resizeWidget("first",8,10)
        assertEquals(4,resized.widgets.first().canvasX)
        assertEquals(14,resized.widgets.first().canvasY)
        assertEquals(second,resized.resizeWidget("second",1,1).widgets.last())
        val reordered=resized.moveWidget("second",-1)
        assertEquals(listOf("second","first"),reordered.widgets.map { it.id })
        val remaining=reordered.removeWidget("first")
        val profile=DashboardDefaults.newProfile(id="workspace").copy(portrait=remaining)
        assertEquals(listOf("second"),DashboardCodec.import(DashboardCodec.export(profile)).getOrThrow().portrait.widgets.map { it.id })
        assertTrue(remaining.removeWidget("second").widgets.isEmpty())
    }
    @Test fun unknownTypesAndUnitsFailImportInsteadOfCrashingTheRenderer() {
        val profile=DashboardDefaults.newProfile().copy(portrait=DashboardLayout(widgets=listOf(DashboardDefaults.widgetCatalog.first())))
        val json=DashboardCodec.export(profile)
        assertTrue(DashboardCodec.import(json.replace("\"type\":\"ANALOG\"","\"type\":\"UNKNOWN\"")).isFailure)
        assertTrue(DashboardCodec.import(json.replace("\"unit\":\"RPM\"","\"unit\":\"INVALID\"")).isFailure)
    }
}
