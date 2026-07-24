package com.nowtuneup.app.data.obd.command
import com.nowtuneup.app.data.transport.ObdTransport
import com.nowtuneup.app.domain.model.ConnectionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
class CommandQueueTest {@Test fun timeoutIsBounded()=runTest {val t=object:ObdTransport{override val connectionState=MutableStateFlow(ConnectionState.CONNECTED);override suspend fun connect()=Result.success(Unit);override suspend fun disconnect(){};override suspend fun write(command:String)=Result.success(Unit);override suspend fun readUntilPrompt(timeoutMillis:Long):Result<String>{delay(1000);return Result.success(">")}};assertTrue(ObdCommandQueue(t).execute(ObdRequest("010C",10,0)).isFailure)}}
