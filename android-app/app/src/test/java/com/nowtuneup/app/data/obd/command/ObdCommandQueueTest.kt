package com.nowtuneup.app.data.obd.command

import com.nowtuneup.app.data.transport.ObdTransport
import com.nowtuneup.app.domain.model.ConnectionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObdCommandQueueTest {
    @Test
    fun timeoutInvokesTransportRecoveryBeforeReturningFailure() = runBlocking {
        val transport = SlowTransport()
        val queue = ObdCommandQueue(transport)

        val result = queue.execute(ObdRequest(command = "010C", timeoutMillis = 20L, retryLimit = 0))

        assertTrue(result.isFailure)
        assertEquals(1, transport.recoveryCount)
        assertEquals(listOf("010C\r"), transport.writes)
    }

    private class SlowTransport : ObdTransport {
        private val state = MutableStateFlow(ConnectionState.CONNECTED)
        override val connectionState: StateFlow<ConnectionState> = state
        val writes = mutableListOf<String>()
        var recoveryCount = 0

        override suspend fun connect(): Result<Unit> = Result.success(Unit)

        override suspend fun disconnect() {
            state.value = ConnectionState.DISCONNECTED
        }

        override suspend fun write(command: String): Result<Unit> {
            writes += command
            return Result.success(Unit)
        }

        override suspend fun readUntilPrompt(timeoutMillis: Long): Result<String> {
            delay(timeoutMillis * 10)
            return Result.success(">")
        }

        override suspend fun recoverAfterTimeout() {
            recoveryCount += 1
        }
    }
}
