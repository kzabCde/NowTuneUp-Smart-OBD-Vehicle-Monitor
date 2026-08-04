package com.nowtuneup.app.feature.timeslip

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.nowtuneup.app.data.obd.session.ObdSpeedSample
import kotlin.math.abs
import kotlin.math.hypot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lightweight local sensor fusion for Time Slip.
 * OBD remains the primary speed source; accurate GNSS speed corrects it and the phone linear
 * acceleration sensor is recorded for launch/graph analysis. No location coordinates are stored.
 */
class TimeSlipSensorFusion(context: Context) : LocationListener, SensorEventListener {
    private val appContext = context.applicationContext
    private val locationManager = appContext.getSystemService(LocationManager::class.java)
    private val sensorManager = appContext.getSystemService(SensorManager::class.java)
    private val linearAcceleration = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

    private val _state = MutableStateFlow(TimeSlipSensorState())
    val state: StateFlow<TimeSlipSensorState> = _state.asStateFlow()

    private var started = false
    private var latestLocation: Location? = null
    private var previousLocation: Location? = null
    private var accelerationMagnitude = 0.0

    private val gnssCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            var used = 0
            for (index in 0 until status.satelliteCount) {
                if (status.usedInFix(index)) used += 1
            }
            _state.value = _state.value.copy(satellitesUsed = used)
        }
    }

    fun hasLocationPermission(): Boolean = ContextCompat.checkSelfPermission(
        appContext,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (started) return true
        sensorManager?.registerListener(
            this,
            linearAcceleration,
            SensorManager.SENSOR_DELAY_GAME,
        )
        if (hasLocationPermission()) {
            runCatching {
                locationManager?.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    100L,
                    0f,
                    this,
                )
                locationManager?.registerGnssStatusCallback(gnssCallback)
            }
        }
        started = true
        _state.value = _state.value.copy(
            active = true,
            locationPermissionGranted = hasLocationPermission(),
            accelerometerAvailable = linearAcceleration != null,
        )
        return true
    }

    fun stop() {
        if (!started) return
        runCatching { locationManager?.removeUpdates(this) }
        runCatching { locationManager?.unregisterGnssStatusCallback(gnssCallback) }
        sensorManager?.unregisterListener(this)
        started = false
        _state.value = _state.value.copy(active = false)
    }

    fun fuse(obd: ObdSpeedSample): TimeSlipTelemetrySample {
        val location = latestLocation
        val gpsFresh = location != null &&
            abs(obd.responseReceivedAtNanos - location.elapsedRealtimeNanos) <= GPS_FRESH_NANOS
        val gpsSpeedKmh = location?.takeIf { gpsFresh && it.hasSpeed() }?.speed?.times(3.6)
        val accuracyMeters = location?.takeIf { gpsFresh && it.hasAccuracy() }?.accuracy?.toDouble()
        val gpsWeight = when {
            gpsSpeedKmh == null -> 0.0
            accuracyMeters != null && accuracyMeters <= 3.0 -> 0.50
            accuracyMeters != null && accuracyMeters <= 6.0 -> 0.35
            accuracyMeters != null && accuracyMeters <= 12.0 -> 0.20
            else -> 0.10
        }
        val fusedSpeed = if (gpsSpeedKmh != null) {
            obd.speedKmh * (1.0 - gpsWeight) + gpsSpeedKmh * gpsWeight
        } else {
            obd.speedKmh
        }
        val sensorState = _state.value
        return TimeSlipTelemetrySample(
            timeNanos = obd.responseReceivedAtNanos,
            wallClockMillis = obd.wallClockMillis,
            obdSpeedKmh = obd.speedKmh,
            gpsSpeedKmh = gpsSpeedKmh,
            fusedSpeedKmh = fusedSpeed.coerceIn(0.0, 400.0),
            accelerationMps2 = sensorState.linearAccelerationMps2,
            gpsAccuracyMeters = accuracyMeters,
            satellitesUsed = sensorState.satellitesUsed,
            slopePercent = sensorState.slopePercent,
            transportLatencyMillis = obd.transportLatencyMillis,
            source = if (gpsWeight > 0.0) MeasurementSource.OBD_GPS_IMU else MeasurementSource.OBD_ONLY,
        )
    }

    override fun onLocationChanged(location: Location) {
        previousLocation = latestLocation
        latestLocation = location
        val slope = calculateSlopePercent(previousLocation, location)
        _state.value = _state.value.copy(
            locationPermissionGranted = true,
            gpsAvailable = true,
            gpsSpeedKmh = if (location.hasSpeed()) location.speed * 3.6 else null,
            gpsAccuracyMeters = if (location.hasAccuracy()) location.accuracy.toDouble() else null,
            speedAccuracyKmh = if (location.hasSpeedAccuracy()) location.speedAccuracyMetersPerSecond * 3.6 else null,
            slopePercent = slope,
            lastLocationElapsedRealtimeNanos = location.elapsedRealtimeNanos,
        )
    }

    @Deprecated("Deprecated by Android but required by LocationListener on older devices")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    override fun onProviderEnabled(provider: String) = Unit

    override fun onProviderDisabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER) {
            _state.value = _state.value.copy(gpsAvailable = false)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_LINEAR_ACCELERATION || event.values.size < 3) return
        val magnitude = hypot(event.values[0].toDouble(), hypot(event.values[1].toDouble(), event.values[2].toDouble()))
        accelerationMagnitude = accelerationMagnitude * 0.75 + magnitude * 0.25
        _state.value = _state.value.copy(linearAccelerationMps2 = accelerationMagnitude)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun calculateSlopePercent(previous: Location?, current: Location): Double? {
        if (previous == null || !previous.hasAltitude() || !current.hasAltitude()) return null
        val distance = previous.distanceTo(current).toDouble()
        if (distance < 5.0) return _state.value.slopePercent
        val elevationChange = current.altitude - previous.altitude
        return (elevationChange / distance * 100.0).coerceIn(-20.0, 20.0)
    }

    companion object {
        private const val GPS_FRESH_NANOS = 1_500_000_000L
    }
}

data class TimeSlipSensorState(
    val active: Boolean = false,
    val locationPermissionGranted: Boolean = false,
    val gpsAvailable: Boolean = false,
    val accelerometerAvailable: Boolean = false,
    val gpsSpeedKmh: Double? = null,
    val gpsAccuracyMeters: Double? = null,
    val speedAccuracyKmh: Double? = null,
    val satellitesUsed: Int = 0,
    val linearAccelerationMps2: Double = 0.0,
    val slopePercent: Double? = null,
    val lastLocationElapsedRealtimeNanos: Long = 0L,
)
