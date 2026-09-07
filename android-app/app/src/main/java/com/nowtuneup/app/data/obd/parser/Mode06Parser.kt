package com.nowtuneup.app.data.obd.parser

import com.nowtuneup.app.domain.model.Mode06Summary

object Mode06Parser {
    fun parse(raw: String, command: String = "0600", nowMillis: Long = System.currentTimeMillis()): Mode06Summary? {
        val normalized = ObdResponseParser.normalize(raw, command).getOrNull() ?: return null
        val monitorFrames = normalized.frames.filter { frame -> frame.size >= 2 && frame[0] == 0x46 }
        if (monitorFrames.isEmpty()) return null
        return Mode06Summary(
            supported = true,
            monitorFrameCount = monitorFrames.size,
            raw = normalized.frames.joinToString("\n") { frame -> frame.joinToString(" ") { "%02X".format(it) } }.take(8_000),
            readAtMillis = nowMillis,
        )
    }
}
