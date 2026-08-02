package com.nowtuneup.app.data.transport

import com.nowtuneup.app.domain.model.ConnectionState
import kotlinx.coroutines.flow.StateFlow

interface ObdTransport {
    val connectionState: StateFlow<ConnectionState>

    suspend fun connect(): Result<Unit>
    suspend fun disconnect()
    suspend fun write(command: String): Result<Unit>
    suspend fun readUntilPrompt(timeoutMillis: Long): Result<String>

    /**
     * Gives transports a chance to discard a partial or late response after a command deadline.
     * Implementations that do not buffer stream data can keep the default no-op behavior.
     */
    suspend fun recoverAfterTimeout() = Unit
}
