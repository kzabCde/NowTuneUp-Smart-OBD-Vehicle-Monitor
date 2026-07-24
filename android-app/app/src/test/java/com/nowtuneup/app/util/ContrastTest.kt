package com.nowtuneup.app.util

import org.junit.Assert.*
import org.junit.Test

class ContrastTest {
    @Test fun validatesAccessibleTextContrast() {
        assertTrue(Contrast.isReadable(0xFFFFFFFF, 0xFF000000))
        assertFalse(Contrast.isReadable(0xFF777777, 0xFF888888))
    }
}
