package com.nowtuneup.app.data.obd.parser
import org.junit.Assert.*
import org.junit.Test
class DtcParserTest {@Test fun parsesCodes(){val codes=DtcParser.parse("43 01 00 03 00 00 00>");assertEquals(listOf("P0100","P0300"),codes.map{it.code});assertNotNull(codes[1].description)} }
