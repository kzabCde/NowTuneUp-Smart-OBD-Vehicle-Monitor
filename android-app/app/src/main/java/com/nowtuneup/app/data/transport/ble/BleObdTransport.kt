package com.nowtuneup.app.data.transport.ble

import com.nowtuneup.app.data.transport.ObdTransport
import com.nowtuneup.app.domain.model.ConnectionState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * BLE transport contract placeholder.
 *
 * ELM327 BLE adapters do not share one standard GATT service/characteristic mapping.
 * This implementation intentionally stays disabled until the target adapter UUIDs are known
 * and verified with real hardware.
 */
@Singleton
class BleObdTransport @Inject constructor() : ObdTransport {
    private val state = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = state

    override suspend fun connect(): Result<Unit> {
        state.value = ConnectionState.ERROR
        return Result.failure(
            UnsupportedOperationException(
                "BLE ELM327 support is experimental and requires adapter-specific service and characteristic UUIDs",
            ),
        )
    }

    override suspend fun disconnect() {
        state.value = ConnectionState.DISCONNECTED
    }

    override suspend fun write(command: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("BLE transport is not configured"))

    override suspend fun readUntilPrompt(timeoutMillis: Long): Result<String> =
        Result.failure(UnsupportedOperationException("BLE transport is not configured"))
}
