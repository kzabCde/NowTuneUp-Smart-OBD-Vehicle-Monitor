package com.nowtuneup.app.data.transport

import com.nowtuneup.app.BuildConfig
import com.nowtuneup.app.data.logging.DiagnosticLogger
import com.nowtuneup.app.data.transport.ble.BleObdTransport
import com.nowtuneup.app.data.transport.bluetooth.BluetoothClassicObdTransport
import com.nowtuneup.app.data.transport.mock.MockObdTransport
import com.nowtuneup.app.data.transport.usb.UsbObdTransport
import com.nowtuneup.app.domain.model.BluetoothDeviceInfo
import com.nowtuneup.app.domain.model.ConnectionState
import com.nowtuneup.app.domain.model.ObdTransportType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Singleton
class ObdTransportManager @Inject constructor(
    private val usb: UsbObdTransport,
    private val bluetooth: BluetoothClassicObdTransport,
    private val ble: BleObdTransport,
    private val mock: MockObdTransport,
    private val logger: DiagnosticLogger,
) : ObdTransport {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val selectedType = MutableStateFlow(
        if (BuildConfig.MOCK_OBD_DEFAULT) ObdTransportType.MOCK else ObdTransportType.USB,
    )
    private val state = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = state.asStateFlow()
    val transportType: StateFlow<ObdTransportType> = selectedType.asStateFlow()

    init {
        observe(ObdTransportType.USB, usb)
        observe(ObdTransportType.BLUETOOTH_CLASSIC, bluetooth)
        observe(ObdTransportType.BLE_EXPERIMENTAL, ble)
        observe(ObdTransportType.MOCK, mock)
    }

    fun selectTransport(type: ObdTransportType): Result<Unit> = runCatching {
        if (type == ObdTransportType.MOCK && !BuildConfig.DEBUG && !BuildConfig.MOCK_OBD_DEFAULT) {
            error("Mock transport is available only in debug builds")
        }
        check(state.value !in setOf(ConnectionState.CONNECTING, ConnectionState.INITIALIZING, ConnectionState.CONNECTED)) {
            "Disconnect before changing connection type"
        }
        selectedType.value = type
        state.value = active().connectionState.value
        logger.info("Transport", "Selected ${type.name}")
    }

    fun activeType(): ObdTransportType = selectedType.value

    fun selectBluetoothDevice(address: String?) = bluetooth.selectDevice(address)
    fun selectedBluetoothAddress(): String? = bluetooth.selectedAddress()
    fun bluetoothSupported(): Boolean = bluetooth.isSupported()
    fun bluetoothEnabled(): Boolean = bluetooth.isEnabled()
    fun bluetoothPermissionGranted(): Boolean = bluetooth.hasConnectPermission() && bluetooth.hasScanPermission()
    fun pairedBluetoothDevices(): Result<List<BluetoothDeviceInfo>> = bluetooth.pairedDevices()

    override suspend fun connect(): Result<Unit> {
        logger.info("Transport", "Connect using ${selectedType.value.name}")
        return active().connect()
    }

    override suspend fun disconnect() = active().disconnect()
    override suspend fun write(command: String): Result<Unit> = active().write(command)
    override suspend fun readUntilPrompt(timeoutMillis: Long): Result<String> = active().readUntilPrompt(timeoutMillis)

    private fun active(): ObdTransport = when (selectedType.value) {
        ObdTransportType.USB -> usb
        ObdTransportType.BLUETOOTH_CLASSIC -> bluetooth
        ObdTransportType.BLE_EXPERIMENTAL -> ble
        ObdTransportType.MOCK -> mock
    }

    private fun observe(type: ObdTransportType, transport: ObdTransport) {
        scope.launch {
            transport.connectionState.collect { transportState ->
                if (selectedType.value == type) state.value = transportState
            }
        }
    }
}
