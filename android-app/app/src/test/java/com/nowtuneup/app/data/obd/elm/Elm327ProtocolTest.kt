package com.nowtuneup.app.data.obd.elm

import com.nowtuneup.app.domain.model.ObdError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Elm327ProtocolTest {
    @Test
    fun removesEchoPromptAndNormalizesWhitespace() {
        val response = Elm327ResponseInterpreter.requireSuccess(
            "010C\r\r41 0C 1A F8\r>",
            "010C",
        ).getOrThrow()
        assertEquals(listOf("41 0C 1A F8"), response.normalizedLines)
        assertEquals("41 0C 1A F8", response.body)
    }

    @Test
    fun searchingMessageDoesNotDiscardData() {
        val response = Elm327ResponseInterpreter.requireSuccess(
            "SEARCHING...\r41 00 BE 1F A8 13\r>",
            "0100",
        ).getOrThrow()
        assertTrue(response.body.contains("4100", ignoreCase = true) || response.body.contains("41 00"))
    }

    @Test
    fun noDataMapsToTypedFailure() {
        val error = Elm327ResponseInterpreter.requireSuccess("NO DATA\r>", "010C").exceptionOrNull()
        assertTrue(error is Elm327ProtocolException)
        assertEquals(ObdError.NoData, (error as Elm327ProtocolException).obdError)
    }

    @Test
    fun bufferFullMapsToTypedFailure() {
        val error = Elm327ResponseInterpreter.requireSuccess("BUFFER FULL\r>", "010C").exceptionOrNull()
        assertTrue(error is Elm327ProtocolException)
        assertEquals(ObdError.BufferFull, (error as Elm327ProtocolException).obdError)
    }

    @Test
    fun unableToConnectMapsToEcuFailure() {
        val error = Elm327ResponseInterpreter.requireSuccess("UNABLE TO CONNECT\r>", "0100").exceptionOrNull()
        assertTrue(error is Elm327ProtocolException)
        assertEquals(ObdError.EcuNotResponding, (error as Elm327ProtocolException).obdError)
    }
}
