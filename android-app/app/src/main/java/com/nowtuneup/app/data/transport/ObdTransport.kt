package com.nowtuneup.app.data.transport
import com.nowtuneup.app.domain.model.ConnectionState
import kotlinx.coroutines.flow.StateFlow
interface ObdTransport {
 val connectionState: StateFlow<ConnectionState>
 suspend fun connect(): Result<Unit>
 suspend fun disconnect()
 suspend fun write(command:String): Result<Unit>
 suspend fun readUntilPrompt(timeoutMillis:Long): Result<String>
}
