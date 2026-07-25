package com.nowtuneup.app.data.transport.mock

import com.nowtuneup.app.data.transport.ObdTransport
import com.nowtuneup.app.domain.model.ConnectionState
import javax.inject.Inject
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MockObdTransport @Inject constructor() : ObdTransport {
    private val state = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = state
    private var command = ""
    private var tick = 0

    override suspend fun connect() = runCatching {
        state.value = ConnectionState.CONNECTING
        delay(150)
        state.value = ConnectionState.CONNECTED
    }

    override suspend fun disconnect() {
        state.value = ConnectionState.DISCONNECTED
    }

    override suspend fun write(command: String) = runCatching {
        check(state.value == ConnectionState.CONNECTED)
        this.command = command.trim()
    }

    override suspend fun readUntilPrompt(timeoutMillis: Long) = runCatching {
        delay(15)
        tick++
        when (command) {
            "0100" -> "41 00 18 38 80 01\r>"
            "0120" -> "41 20 40 00 20 01\r>"
            "0140" -> "41 40 40 00 00 00\r>"
            "0160" -> "41 60 00 00 00 00\r>"
            "010B" -> "41 0B %02X\r>".format((115 + 55 * sin(tick / 6.0)).toInt().coerceIn(35, 220))
            "0133" -> "41 33 64\r>"
            "010C" -> {
                val encoded = ((900 + 1200 * sin(tick / 5.0)) * 4).toInt()
                "41 0C %02X %02X\r>".format((encoded shr 8) and 255, encoded and 255)
            }
            "010D" -> "41 0D %02X\r>".format((45 + 35 * sin(tick / 8.0)).toInt())
            "0105" -> "41 05 7D\r>"
            "0142" -> "41 42 36 20\r>"
            "0104" -> "41 04 60\r>"
            "0111" -> "41 11 45\r>"
            "03" -> "43 01 00 03 00\r>"
            else -> if (command.startsWith("AT")) "$command\rOK\r>" else "NO DATA\r>"
        }
    }
}
