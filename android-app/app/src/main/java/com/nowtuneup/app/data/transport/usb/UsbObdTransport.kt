package com.nowtuneup.app.data.transport.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.nowtuneup.app.data.transport.ObdTransport
import com.nowtuneup.app.domain.model.ConnectionState
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class UsbObdTransport @Inject constructor(@ApplicationContext private val context: Context) : ObdTransport {
    companion object { const val ACTION_PERMISSION = "com.nowtuneup.app.USB_PERMISSION" }
    private val manager = context.getSystemService(UsbManager::class.java)
    private var port: UsbSerialPort? = null
    private var connectedDeviceId: Int? = null
    private val state = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = state

    private val detachReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val device = intent?.usbDevice()
            if (intent?.action == UsbManager.ACTION_USB_DEVICE_DETACHED && device?.deviceId == connectedDeviceId) {
                runCatching { port?.close() }
                port = null
                connectedDeviceId = null
                state.value = ConnectionState.ERROR
            }
        }
    }

    init {
        ContextCompat.registerReceiver(context, detachReceiver, IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    fun devices(): List<UsbDevice> = UsbSerialProber.getDefaultProber().findAllDrivers(manager).map { it.device }

    override suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (state.value in setOf(ConnectionState.CONNECTING, ConnectionState.REQUESTING_PERMISSION, ConnectionState.INITIALIZING, ConnectionState.CONNECTED)) error("Connection already in progress")
            val driver = UsbSerialProber.getDefaultProber().findAllDrivers(manager).firstOrNull() ?: error("No supported USB serial adapter")
            state.value = ConnectionState.DEVICE_DETECTED
            if (!manager.hasPermission(driver.device) && !requestPermission(driver.device)) {
                state.value = ConnectionState.ERROR
                error("USB permission denied")
            }
            state.value = ConnectionState.CONNECTING
            val connection = manager.openDevice(driver.device) ?: error("Cannot open USB device")
            port = driver.ports.firstOrNull()?.also {
                it.open(connection)
                it.setParameters(38_400, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            } ?: error("USB serial adapter has no port")
            connectedDeviceId = driver.device.deviceId
            state.value = ConnectionState.CONNECTED
        }.onFailure { if (state.value != ConnectionState.ERROR) state.value = ConnectionState.ERROR }
    }

    private suspend fun requestPermission(device: UsbDevice): Boolean = suspendCancellableCoroutine { continuation ->
        state.value = ConnectionState.REQUESTING_PERMISSION
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action != ACTION_PERMISSION) return
                runCatching { context.unregisterReceiver(this) }
                if (continuation.isActive) continuation.resume(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(ACTION_PERMISSION), ContextCompat.RECEIVER_NOT_EXPORTED)
        continuation.invokeOnCancellation { runCatching { context.unregisterReceiver(receiver) } }
        val permissionIntent = Intent(ACTION_PERMISSION).setPackage(context.packageName)
        manager.requestPermission(device, PendingIntent.getBroadcast(context, device.deviceId, permissionIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        runCatching { port?.close() }
        port = null
        connectedDeviceId = null
        state.value = ConnectionState.DISCONNECTED
    }

    override suspend fun write(command: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { port?.write(command.toByteArray(Charsets.US_ASCII), 2_000) ?: error("USB disconnected") }
    }

    override suspend fun readUntilPrompt(timeoutMillis: Long): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val output = ByteArrayOutputStream()
            val startedAt = System.currentTimeMillis()
            while (System.currentTimeMillis() - startedAt < timeoutMillis) {
                val buffer = ByteArray(256)
                val count = port?.read(buffer, 200) ?: error("USB disconnected")
                if (count > 0) {
                    output.write(buffer, 0, count)
                    if (buffer.take(count).contains('>'.code.toByte())) return@runCatching output.toString(Charsets.US_ASCII.name())
                }
            }
            error("Timed out waiting for ELM327 prompt")
        }
    }

    @Suppress("DEPRECATION")
    private fun Intent.usbDevice(): UsbDevice? = if (android.os.Build.VERSION.SDK_INT >= 33) getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java) else getParcelableExtra(UsbManager.EXTRA_DEVICE)
}
