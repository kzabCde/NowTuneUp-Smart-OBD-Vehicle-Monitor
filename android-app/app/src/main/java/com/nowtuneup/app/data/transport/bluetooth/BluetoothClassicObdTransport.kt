package com.nowtuneup.app.data.transport.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.nowtuneup.app.data.logging.DiagnosticLogger
import com.nowtuneup.app.data.obd.elm.Elm327StreamBuffer
import com.nowtuneup.app.data.transport.ObdTransport
import com.nowtuneup.app.domain.model.BluetoothDeviceInfo
import com.nowtuneup.app.domain.model.ConnectionState
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

@Singleton
class BluetoothClassicObdTransport @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: DiagnosticLogger,
) : ObdTransport {
    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val adapter: BluetoothAdapter? get() = bluetoothManager?.adapter
    private val state = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = state

    private val writeMutex = Mutex()
    private val readMutex = Mutex()
    private val receiveBuffer = Elm327StreamBuffer()
    private var selectedAddress: String? = null
    private var socket: BluetoothSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null

    @Volatile
    private var lastByteAtMillis: Long = 0L

    fun isSupported(): Boolean = adapter != null

    @SuppressLint("MissingPermission")
    fun isEnabled(): Boolean = hasConnectPermission() && adapter?.isEnabled == true

    fun hasConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    fun hasScanPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun pairedDevices(): Result<List<BluetoothDeviceInfo>> = runCatching {
        val localAdapter = adapter ?: error("Bluetooth is not supported on this device")
        check(hasConnectPermission()) { "Bluetooth permission is required" }
        localAdapter.bondedDevices
            .map { device ->
                BluetoothDeviceInfo(
                    name = device.name?.takeIf { it.isNotBlank() } ?: "Unnamed Bluetooth device",
                    address = device.address,
                    bonded = device.bondState == BluetoothDevice.BOND_BONDED,
                )
            }
            .sortedWith(compareBy<BluetoothDeviceInfo> { !looksLikeElm327(it.name) }.thenBy { it.name.lowercase() })
    }

    fun selectDevice(address: String?) {
        require(address == null || MAC_ADDRESS.matches(address)) { "Invalid Bluetooth address" }
        selectedAddress = address
    }

    fun selectedAddress(): String? = selectedAddress

    @SuppressLint("MissingPermission")
    override suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (state.value in setOf(ConnectionState.CONNECTING, ConnectionState.INITIALIZING, ConnectionState.CONNECTED)) {
                error("Bluetooth connection is already in progress")
            }
            val localAdapter = adapter ?: error("Bluetooth is not supported on this device")
            check(hasConnectPermission()) { "Bluetooth permission is required" }
            check(localAdapter.isEnabled) { "Bluetooth is disabled" }
            val address = selectedAddress ?: error("Select a paired ELM327 adapter first")
            val device = localAdapter.getRemoteDevice(address)
            check(device.bondState == BluetoothDevice.BOND_BONDED) { "Bluetooth device is not paired" }

            state.value = ConnectionState.DEVICE_DETECTED
            logger.info("Bluetooth", "Connecting to ${logger.safeDeviceLabel(device.name, device.address)} with RFCOMM SPP")
            localAdapter.cancelDiscovery()
            closeSocketOnly()
            state.value = ConnectionState.CONNECTING

            val connectedSocket = connectRfcomm(device)
            socket = connectedSocket
            input = connectedSocket.inputStream
            output = connectedSocket.outputStream
            receiveBuffer.clear()
            lastByteAtMillis = System.currentTimeMillis()
            state.value = ConnectionState.CONNECTED
            logger.info("Bluetooth", "RFCOMM socket connected and stream ready")
        }.onFailure { error ->
            logger.error("Bluetooth", "RFCOMM connection failed", error)
            closeSocketOnly()
            state.value = ConnectionState.ERROR
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        logger.info("Bluetooth", "Disconnect requested")
        closeSocketOnly()
        state.value = ConnectionState.DISCONNECTED
    }

    override suspend fun write(command: String): Result<Unit> = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            runCatching {
                val activeSocket = socket ?: error("Bluetooth socket is not connected")
                check(activeSocket.isConnected) { "Bluetooth socket is closed" }
                val stream = output ?: error("Bluetooth socket is not connected")

                // A late prompt from a timed-out clone must never become the next PID response.
                var discardedResponses = 0
                while (receiveBuffer.pollResponse() != null) discardedResponses += 1
                if (receiveBuffer.pendingCharacters() > 0) {
                    receiveBuffer.clear()
                    discardedResponses += 1
                }
                if (discardedResponses > 0) {
                    logger.warning("Bluetooth", "Discarded $discardedResponses stale response fragment(s) before transmit")
                }

                val bytes = command.toByteArray(Charsets.US_ASCII)
                runInterruptible {
                    stream.write(bytes)
                    stream.flush()
                }
                logger.debug("ELM327 TX", command.trim().take(64))
            }.onFailure {
                logger.error("Bluetooth", "Write failed", it)
                state.value = ConnectionState.ERROR
            }
        }
    }

    override suspend fun readUntilPrompt(timeoutMillis: Long): Result<String> = withContext(Dispatchers.IO) {
        readMutex.withLock {
            runCatching {
                receiveBuffer.pollResponse()?.let { return@runCatching it }
                withTimeout(timeoutMillis) {
                    val stream = input ?: error("Bluetooth socket is not connected")
                    val chunk = ByteArray(512)
                    while (true) {
                        val activeSocket = socket ?: error("Bluetooth socket closed")
                        check(activeSocket.isConnected) { "Bluetooth socket closed" }
                        val available = runCatching { stream.available() }
                            .getOrElse { error -> throw IllegalStateException("Bluetooth socket read failed", error) }
                        if (available <= 0) {
                            delay(READ_POLL_INTERVAL_MILLIS)
                            continue
                        }

                        val count = runInterruptible {
                            stream.read(chunk, 0, minOf(chunk.size, available))
                        }
                        if (count < 0) error("Bluetooth socket closed")
                        if (count == 0) {
                            delay(READ_POLL_INTERVAL_MILLIS)
                            continue
                        }
                        lastByteAtMillis = System.currentTimeMillis()
                        receiveBuffer.append(String(chunk, 0, count, Charsets.US_ASCII))
                        receiveBuffer.pollResponse()?.let { response ->
                            logger.debug("ELM327 RX", response.replace('\r', ' ').replace('\n', ' ').take(300))
                            return@withTimeout response
                        }
                    }
                    @Suppress("UNREACHABLE_CODE")
                    error("No ELM327 prompt")
                }
            }.onFailure { error ->
                logger.error("Bluetooth", "Read failed or timed out", error)
                if (
                    error.message?.contains("socket", ignoreCase = true) == true ||
                    error.message?.contains("closed", ignoreCase = true) == true
                ) {
                    state.value = ConnectionState.ERROR
                }
            }
        }
    }

    override suspend fun recoverAfterTimeout() = withContext(Dispatchers.IO) {
        readMutex.withLock {
            receiveBuffer.clear()
            val stream = input ?: return@withLock
            val startedAt = System.currentTimeMillis()
            var quietSince = startedAt
            var drained = 0
            val chunk = ByteArray(512)

            // Cheap ELM327 clones can finish hundreds of milliseconds after the app timeout.
            // Wait for a real quiet window so that a late answer cannot be parsed as the next PID.
            while (System.currentTimeMillis() - startedAt < MAX_TIMEOUT_RECOVERY_MILLIS) {
                val available = runCatching { stream.available() }.getOrDefault(0)
                if (available > 0) {
                    val count = runCatching {
                        runInterruptible { stream.read(chunk, 0, minOf(chunk.size, available)) }
                    }.getOrDefault(0)
                    if (count > 0) {
                        drained += count
                        lastByteAtMillis = System.currentTimeMillis()
                        quietSince = lastByteAtMillis
                    }
                } else {
                    val now = System.currentTimeMillis()
                    if (now - quietSince >= TIMEOUT_QUIET_WINDOW_MILLIS) break
                    delay(RECOVERY_POLL_INTERVAL_MILLIS)
                }
            }
            receiveBuffer.clear()
            logger.warning(
                "Bluetooth",
                "Recovered command stream after timeout; discarded $drained late byte(s) after ${System.currentTimeMillis() - startedAt} ms",
            )
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun connectRfcomm(device: BluetoothDevice): BluetoothSocket {
        var firstFailure: Throwable? = null
        val factories: List<Pair<String, () -> BluetoothSocket>> = listOf(
            "insecure" to { device.createInsecureRfcommSocketToServiceRecord(SPP_UUID) },
            "secure" to { device.createRfcommSocketToServiceRecord(SPP_UUID) },
        )
        factories.forEach { (mode, factory) ->
            val candidate = factory()
            try {
                withTimeout(CONNECT_TIMEOUT_MILLIS) {
                    runInterruptible { candidate.connect() }
                }
                logger.info("Bluetooth", "Connected using $mode RFCOMM SPP socket")
                return candidate
            } catch (error: Throwable) {
                runCatching { candidate.close() }
                if (firstFailure == null) firstFailure = error
                logger.warning("Bluetooth", "$mode RFCOMM connection attempt failed: ${error.message}")
            }
        }
        throw firstFailure ?: IllegalStateException("Unable to open RFCOMM socket")
    }

    private fun closeSocketOnly() {
        runCatching { input?.close() }
        runCatching { output?.close() }
        runCatching { socket?.close() }
        input = null
        output = null
        socket = null
        lastByteAtMillis = 0L
        receiveBuffer.clear()
    }

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val CONNECT_TIMEOUT_MILLIS = 15_000L
        private const val READ_POLL_INTERVAL_MILLIS = 4L
        private const val RECOVERY_POLL_INTERVAL_MILLIS = 10L
        private const val TIMEOUT_QUIET_WINDOW_MILLIS = 180L
        private const val MAX_TIMEOUT_RECOVERY_MILLIS = 900L
        private val MAC_ADDRESS = Regex("(?i)(?:[0-9A-F]{2}:){5}[0-9A-F]{2}")

        private fun looksLikeElm327(name: String): Boolean {
            val normalized = name.uppercase()
            return listOf("OBD", "ELM", "V-LINK", "VLINK", "KONNWEI").any(normalized::contains)
        }
    }
}
