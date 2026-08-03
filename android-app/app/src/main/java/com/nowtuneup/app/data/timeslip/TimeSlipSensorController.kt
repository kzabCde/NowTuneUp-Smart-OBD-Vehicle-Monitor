package com.nowtuneup.app.data.timeslip

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import com.nowtuneup.app.feature.timeslip.domain.AccelerometerTelemetry
import com.nowtuneup.app.feature.timeslip.domain.GpsStatus
import com.nowtuneup.app.feature.timeslip.domain.GpsTelemetry
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class TimeSlipSensorController @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocationListener, SensorEventListener {
    private val locationManager = context.getSystemService(LocationManager::class.java)
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val linearAcceleration = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _gpsStatus = MutableStateFlow(GpsStatus())
    val gpsStatus: StateFlow<GpsStatus> = _gpsStatus.asStateFlow()
    private val _gpsSamples = MutableSharedFlow<GpsTelemetry>(extraBufferCapacity = 64)
    val gpsSamples: SharedFlow<GpsTelemetry> = _gpsSamples.asSharedFlow()
    private val _accelerometerSamples = MutableSharedFlow<AccelerometerTelemetry>(extraBufferCapacity = 128)
    val accelerometerSamples: SharedFlow<AccelerometerTelemetry> = _accelerometerSamples.asSharedFlow()

    private val gpsTimes = ArrayDeque<Long>()
    private val accuracyWindow = ArrayDeque<Double>()
    private var locationStarted = false
    private var sensorsStarted = false
    private var usingLinearAcceleration = false

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun isGpsEnabled(): Boolean = runCatching {
        locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
    }.getOrDefault(false)

    fun start(): Result<Unit> = runCatching {
        startMotionSensor()
        refreshProviderState()
        if (!hasLocationPermission()) {
            _gpsStatus.value = _gpsStatus.value.copy(permissionGranted = false)
            return@runCatching
        }
        if (locationStarted) return@runCatching
        val manager = locationManager ?: error("อุปกรณ์นี้ไม่มีบริการตำแหน่ง")
        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            _gpsStatus.value = _gpsStatus.value.copy(permissionGranted = true, providerEnabled = false)
            return@runCatching
        }
        manager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            GPS_REQUEST_INTERVAL_MS,
            0f,
            this,
            Looper.getMainLooper(),
        )
        locationStarted = true
        _gpsStatus.value = _gpsStatus.value.copy(permissionGranted = true, providerEnabled = true)
    }

    fun refreshProviderState() {
        _gpsStatus.value = _gpsStatus.value.copy(
            permissionGranted = hasLocationPermission(),
            providerEnabled = isGpsEnabled(),
        )
    }

    fun stop() {
        if (locationStarted) runCatching { locationManager?.removeUpdates(this) }
        if (sensorsStarted) sensorManager?.unregisterListener(this)
        locationStarted = false
        sensorsStarted = false
        gpsTimes.clear()
        accuracyWindow.clear()
    }

    fun averageAccuracyMeters(): Double? = accuracyWindow.takeIf { it.isNotEmpty() }?.average()

    override fun onLocationChanged(location: Location) {
        val monotonicMs = location.elapsedRealtimeNanos / 1_000_000L
        val mock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) location.isMock else {
            @Suppress("DEPRECATION")
            location.isFromMockProvider
        }
        val accuracy = location.accuracy.toDouble().coerceAtLeast(0.0)
        gpsTimes.addLast(monotonicMs)
        while (gpsTimes.size > 1 && monotonicMs - gpsTimes.first() > RATE_WINDOW_MS) gpsTimes.removeFirst()
        accuracyWindow.addLast(accuracy)
        while (accuracyWindow.size > ACCURACY_WINDOW_SIZE) accuracyWindow.removeFirst()
        val rate = sampleRate(gpsTimes)
        val status = GpsStatus(
            permissionGranted = true,
            providerEnabled = true,
            hasPositionLock = location.latitude.isFinite() && location.longitude.isFinite() && accuracy > 0.0,
            accuracyMeters = accuracy,
            sampleRateHz = rate,
            lastUpdateMonotonicMs = monotonicMs,
            isMock = mock,
        )
        _gpsStatus.value = status
        _gpsSamples.tryEmit(
            GpsTelemetry(
                speedKmh = location.speed.takeIf { location.hasSpeed() }?.toDouble()?.times(3.6),
                latitude = location.latitude,
                longitude = location.longitude,
                accuracyMeters = accuracy,
                monotonicTimeMs = monotonicMs,
                isMock = mock,
            ),
        )
    }

    override fun onProviderEnabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER) refreshProviderState()
    }

    override fun onProviderDisabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER) {
            _gpsStatus.value = _gpsStatus.value.copy(providerEnabled = false, hasPositionLock = false)
        }
    }

    @Deprecated("Deprecated by Android but required by LocationListener on older API levels")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    override fun onSensorChanged(event: SensorEvent) {
        val monotonicMs = event.timestamp / 1_000_000L
        val x = event.values.getOrElse(0) { 0f }.toDouble()
        val y = event.values.getOrElse(1) { 0f }.toDouble()
        val z = event.values.getOrElse(2) { 0f }.toDouble()
        val magnitude = sqrt(x * x + y * y + z * z)
        val linearMagnitude = if (usingLinearAcceleration) magnitude else (magnitude - SensorManager.GRAVITY_EARTH).coerceAtLeast(0.0)
        _accelerometerSamples.tryEmit(
            AccelerometerTelemetry(
                longitudinalAccelerationMs2 = linearMagnitude,
                monotonicTimeMs = monotonicMs,
            ),
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun startMotionSensor() {
        if (sensorsStarted) return
        val sensor = linearAcceleration ?: accelerometer ?: return
        usingLinearAcceleration = sensor.type == Sensor.TYPE_LINEAR_ACCELERATION
        sensorsStarted = sensorManager?.registerListener(
            this,
            sensor,
            SensorManager.SENSOR_DELAY_GAME,
        ) == true
    }

    private fun sampleRate(times: ArrayDeque<Long>): Double {
        if (times.size < 2) return 0.0
        val elapsed = (times.last() - times.first()).coerceAtLeast(1L)
        return (times.size - 1) * 1_000.0 / elapsed
    }

    companion object {
        private const val GPS_REQUEST_INTERVAL_MS = 100L
        private const val RATE_WINDOW_MS = 5_000L
        private const val ACCURACY_WINDOW_SIZE = 40
    }
}
