package com.nowtuneup.app.data.obd.command

import com.nowtuneup.app.data.transport.ObdTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

data class ObdRequest(val command: String, val timeoutMillis: Long = 2_000, val retryLimit: Int = 1)

/** Serializes every adapter exchange; cancellation is never converted into a retry. */
class ObdCommandQueue(private val transport: ObdTransport) {
    private val mutex = Mutex()

    suspend fun execute(request: ObdRequest): Result<String> = mutex.withLock {
        require(request.timeoutMillis > 0)
        require(request.retryLimit in 0..3)
        var failure: Throwable = IllegalStateException("No command attempt completed")
        repeat(request.retryLimit + 1) { attempt ->
            try {
                val response = withTimeout(request.timeoutMillis) {
                    transport.write(request.command.trimEnd('\r') + "\r").getOrThrow()
                    transport.readUntilPrompt(request.timeoutMillis).getOrThrow()
                }
                return@withLock Result.success(response)
            } catch (cancelled: CancellationException) {
                if (cancelled !is TimeoutCancellationException) throw cancelled
                failure = cancelled
            } catch (error: Throwable) {
                failure = error
            }
            if (attempt == request.retryLimit) return@withLock Result.failure(failure)
        }
        Result.failure(failure)
    }
}
