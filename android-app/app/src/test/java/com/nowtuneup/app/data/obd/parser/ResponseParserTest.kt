package com.nowtuneup.app.data.obd.parser
import org.junit.Assert.*
import org.junit.Test
class ResponseParserTest {
 @Test fun removesEchoAndPrompt(){val n=ObdResponseParser.normalize("010C\r41 0C 1A F8\r>","010C").getOrThrow();assertEquals(listOf(0x41,0x0C,0x1A,0xF8),n.frames.single())}
 @Test fun noDataFails()=assertTrue(ObdResponseParser.normalize("NO DATA\r>").isFailure)
 @Test fun invalidHexFails()=assertTrue(ObdResponseParser.normalize("41 0C ZZ\r>").isFailure)
 @Test fun searchingIsInformational(){val n=ObdResponseParser.normalize("SEARCHING...\r41 0D 3C\r>").getOrThrow();assertEquals(1,n.informational.size);assertEquals(60.0,ObdResponseParser.parseMode1("SEARCHING...\r41 0D 3C\r>",0x0D).getOrThrow(),0.0)}
 @Test fun multipleEcus(){val n=ObdResponseParser.normalize("41 0D 3C\r41 0D 3D\r>").getOrThrow();assertEquals(2,n.frames.size)}
 @Test fun mismatchedPidFails()=assertTrue(ObdResponseParser.parseMode1("41 0D 3C>",0x0C).isFailure)
}
