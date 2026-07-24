package com.nowtuneup.app.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nowtuneup.app.data.local.dao.NtuDao
import com.nowtuneup.app.data.local.entity.DiagnosticScanEntity
import com.nowtuneup.app.data.local.entity.SampleEntity
import com.nowtuneup.app.data.local.entity.TripEntity
import com.nowtuneup.app.data.obd.session.ObdSessionManager
import com.nowtuneup.app.domain.model.ConnectionState
import com.nowtuneup.app.domain.model.Dtc
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(private val session: ObdSessionManager, private val dao: NtuDao) : ViewModel() {
    val connection = session.connectionState
    val readings = session.readings
    val trips = dao.trips()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _dtcs = MutableStateFlow<List<Dtc>>(emptyList())
    val dtcs = _dtcs.asStateFlow()
    private var tripId: Long? = null
    private var recorder: Job? = null

    fun toggleConnection() = viewModelScope.launch {
        if (connection.value == ConnectionState.CONNECTED) session.disconnect()
        else session.connect().onFailure { _error.value = it.message ?: "Connection failed" }
    }
    fun pause() = session.pause()
    fun resume() = session.startPolling()
    fun dismissError() { _error.value = null }

    fun scan() = viewModelScope.launch {
        session.readDtcs().onSuccess {
            _dtcs.value = it
            dao.insertScan(DiagnosticScanEntity(readAt = System.currentTimeMillis(), codes = it.joinToString { code -> code.code }, raw = it.firstOrNull()?.raw.orEmpty()))
        }.onFailure { _error.value = it.message }
    }

    fun toggleTrip() = viewModelScope.launch {
        val active = tripId
        if (active != null) {
            recorder?.cancel(); recorder = null
            dao.finishTrip(active, System.currentTimeMillis()); tripId = null
        } else {
            val id = dao.startTrip(TripEntity(startTime = System.currentTimeMillis()))
            tripId = id
            recorder = viewModelScope.launch {
                while (isActive) {
                    val values = readings.value.associate { it.pid to it.value }
                    dao.insertSamples(listOf(SampleEntity(tripId = id, timestamp = System.currentTimeMillis(), rpm = values[0x0C], speedKmh = values[0x0D], coolantTempC = values[0x05], voltageV = values[0x42], engineLoadPercent = values[0x04], throttlePercent = values[0x11])))
                    delay(1_000)
                }
            }
        }
    }

    override fun onCleared() { session.close(); super.onCleared() }
}
