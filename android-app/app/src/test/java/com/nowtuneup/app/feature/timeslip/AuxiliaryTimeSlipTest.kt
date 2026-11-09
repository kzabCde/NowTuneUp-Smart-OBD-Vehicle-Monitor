package com.nowtuneup.app.feature.timeslip

import com.google.gson.Gson
import com.nowtuneup.app.domain.model.VehicleReading
import org.junit.Assert.*
import org.junit.Test

class AuxiliaryTimeSlipTest {
    @Test fun capturesOnlyFiniteFreshSourceObservations() {
        val reading = VehicleReading(0x0C,"RPM",3000.0,"rpm",sourceTimeNanos=1_000_000_000)
        assertEquals(1_000_000_000L, captureTimeSlipSignal(reading,1_100_000_000)!!.sourceTimeNanos)
        assertNull(captureTimeSlipSignal(reading,3_000_000_000))
        assertNull(captureTimeSlipSignal(reading,500_000_000))
        assertNull(captureTimeSlipSignal(reading.copy(value=Double.NaN),1_100_000_000))
        assertNull(captureTimeSlipSignal(reading.copy(supported=false),1_100_000_000))
    }
    @Test fun retainsIndependentSourceTimesAndNeverInventsLegacyCurves() {
        val base = TimeSlipRecord(timingStartNanos=1_000_000_000, completionTimeNanos=5_000_000_000)
        assertTrue(auxiliaryRunSeries(base,0x0C).isEmpty())
        val first = TimeSlipTelemetrySample(2_000_000_000, 2, 20.0, rpmSample=TimeSlipSignalSample(1_500_000_000,2000.0))
        val last = TimeSlipTelemetrySample(4_000_000_000,4,80.0,rpmSample=TimeSlipSignalSample(3_800_000_000,5000.0))
        val record=base.copy(rawSamples=listOf(first,first,last))
        val points=auxiliaryRunSeries(record,0x0C)
        assertEquals(2,points.size)
        assertEquals(0.5,points.first().seconds,0.0)
        assertEquals(2.8,points.last().seconds,0.000001)
        assertTrue(points.last().breakBefore)
        assertTrue(auxiliaryRunSeries(record,0x11).isEmpty())
        assertEquals(record,Gson().fromJson(Gson().toJson(record),TimeSlipRecord::class.java))
    }
}
