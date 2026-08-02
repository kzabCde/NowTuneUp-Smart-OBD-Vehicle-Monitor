package com.nowtuneup.app.data.transport.mock

import com.nowtuneup.app.data.transport.ObdTransport
import com.nowtuneup.app.domain.model.ConnectionState
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Singleton
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
        this.command = command.trim().uppercase()
    }

    override suspend fun readUntilPrompt(timeoutMillis: Long) = runCatching {
        delay(15)
        tick++
        when (command) {
            "ATZ" -> "ATZ\rELM327 v1.5\r>"
            "ATI" -> "ELM327 v1.5\r>"
            "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0" -> "OK\r>"
            "0100" -> "SEARCHING...\r41 00 18 3B 80 01\r>"
            "0120" -> "41 20 00 02 20 01\r>"
            "0140" -> "41 40 40 00 00 00\r>"
            "0160" -> "41 60 00 00 00 00\r>"
            "0104" -> "41 04 60\r>"
            "0105" -> "41 05 7D\r>"
            "010B" -> "41 0B %02X\r>".format((115 + 55 * sin(tick / 6.0)).toInt().coerceIn(35, 220))
            "010C" -> {
                val encoded = ((900 + 1200 * sin(tick / 5.0)) * 4).toInt()
                "41 0C %02X %02X\r>".format((encoded shr 8) and 255, encoded and 255)
            }
            "010D" -> "41 0D %02X\r>".format((45 + 35 * sin(tick / 8.0)).toInt())
            "010F" -> "41 0F 46\r>"
            "0110" -> "41 10 09 C4\r>"
            "0111" -> "41 11 45\r>"
            "012F" -> "41 2F A0\r>"
            "0133" -> "41 33 64\r>"
            "0142" -> "41 42 36 20\r>"
            "03" -> "43 01 00 03 00\r>"
            "04" -> "44\r>"
            else -> "NO DATA\r>"
        }
    }
}
