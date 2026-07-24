package com.nowtuneup.app.data.transport.mock
import com.nowtuneup.app.domain.model.ConnectionState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
class MockObdTransportTest {
 @Test fun telemetryChangesAndDisconnectFailsWrites()=runTest {val mock=MockObdTransport();mock.connect().getOrThrow();assertEquals(ConnectionState.CONNECTED,mock.connectionState.value);mock.write("010C").getOrThrow();val first=mock.readUntilPrompt(1000).getOrThrow();mock.write("010C").getOrThrow();val second=mock.readUntilPrompt(1000).getOrThrow();assertNotEquals(first,second);mock.disconnect();assertTrue(mock.write("010C").isFailure)}
}
