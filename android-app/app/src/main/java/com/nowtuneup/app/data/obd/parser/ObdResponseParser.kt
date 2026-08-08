package com.nowtuneup.app.data.obd.parser

import com.nowtuneup.app.data.obd.pid.StandardPids
import com.nowtuneup.app.domain.model.Dtc
import com.nowtuneup.app.domain.model.FreezeFrameSummary
import com.nowtuneup.app.domain.model.ObdError
import com.nowtuneup.app.domain.model.ReadinessStatus

data class NormalizedResponse(
    val raw: String,
    val frames: List<List<Int>>,
    val informational: List<String>,
)

object ObdResponseParser {
    private val errorTokens = listOf(
        "NO DATA",
        "STOPPED",
        "UNABLE TO CONNECT",
        "BUS INIT: ERROR",
        "BUS INIT ERROR",
        "CAN ERROR",
        "BUFFER FULL",
        "ERROR",
        "?",
    )

    fun normalize(raw: String, command: String? = null): Result<NormalizedResponse> = runCatching {
        val expectedEcho = command?.filterNot(Char::isWhitespace)?.uppercase()
        val information = mutableListOf<String>()
        val payloadLines = mutableListOf<String>()
        raw.replace('>', '\n')
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .forEach { original ->
                val withoutFramePrefix = original.replace(Regex("^\\d+\\s*:\\s*"), "")
                val compact = withoutFramePrefix.filterNot(Char::isWhitespace).uppercase()
                if (expectedEcho != null && compact == expectedEcho) return@forEach
                val upper = withoutFramePrefix.uppercase()
                val error = errorTokens.firstOrNull { token -> upper == token || upper.contains(token) }
                if (error != null) {
                    val obdError = when (error) {
                        "NO DATA" -> ObdError.NoData
                        "BUFFER FULL" -> ObdError.BufferFull
                        "STOPPED" -> ObdError.DeviceDisconnected
                        "UNABLE TO CONNECT", "BUS INIT: ERROR", "BUS INIT ERROR", "CAN ERROR" -> ObdError.EcuNotResponding
                        "?" -> ObdError.UnsupportedAdapter
                        else -> ObdError.InvalidResponse(raw)
                    }
                    throw ObdException(obdError)
                }
                if (upper.startsWith("SEARCHING")) {
                    information += withoutFramePrefix
                    val remainder = withoutFramePrefix.substringAfter("...", "").trim()
                    if (remainder.isNotEmpty()) payloadLines += remainder
                } else if (!upper.equals("OK", true)) {
                    payloadLines += withoutFramePrefix
                }
            }

        val frames = payloadLines.map { line ->
            val cleaned = line.replace(" ", "").replace("\t", "")
            require(cleaned.length >= 4 && cleaned.length % 2 == 0) { "Incomplete hexadecimal response" }
            require(cleaned.matches(Regex("[0-9A-Fa-f]+"))) { "Malformed hexadecimal response" }
            cleaned.chunked(2).map { it.toInt(16) }
        }
        require(frames.isNotEmpty()) { "No OBD-II data frames" }
        NormalizedResponse(raw, frames, information)
    }

    fun parseMode1(raw: String, pid: Int, command: String? = null): Result<Double> =
        normalize(raw, command).mapCatching { normalized ->
            normalized.frames.firstOrNull { it.size >= 3 && it[0] == 0x7F }?.let { frame ->
                error("ECU negative response 7F%02X%02X".format(frame.getOrElse(1) { 0 }, frame.getOrElse(2) { 0 }))
            }
            val definition = StandardPids.find(pid) ?: error("Unsupported PID 0x%02X".format(pid))
            val frame = normalized.frames.firstOrNull {
                it.size >= definition.bytes + 2 && it[0] == 0x41 && it[1] == pid
            } ?: error("Response mode/PID mismatch or incomplete payload")
            StandardPids.parse(pid, frame.drop(2))
        }
}

class ObdException(val error: ObdError) : Exception(error.toString())

object DtcParser {
    private val descriptions = mapOf(
        "P0300" to "Random/multiple cylinder misfire detected",
        "P0420" to "Catalyst system efficiency below threshold",
    )

    fun parse(
        raw: String,
        command: String = "03",
        responseMode: Int = 0x43,
        status: String = "Stored",
    ): List<Dtc> {
        val normalized = ObdResponseParser.normalize(raw, command).getOrElse { return emptyList() }
        return normalized.frames.flatMap { frame ->
            if (frame.firstOrNull() != responseMode) {
                emptyList()
            } else {
                frame.drop(1)
                    .chunked(2)
                    .filter { it.size == 2 && (it[0] != 0 || it[1] != 0) }
                    .map { bytes ->
                        val code = decodeCode(bytes[0], bytes[1])
                        Dtc(
                            code = code,
                            category = code.first().toString(),
                            status = status,
                            description = descriptions[code],
                            raw = raw,
                        )
                    }
            }
        }.distinctBy { "${it.status}-${it.code}" }
    }

    fun decodeCode(first: Int, second: Int): String {
        val prefix = "PCBU"[(first shr 6) and 3]
        return buildString {
            append(prefix)
            append((first shr 4) and 3)
            append((first and 15).toString(16))
            append(((second shr 4) and 15).toString(16))
            append((second and 15).toString(16))
        }.uppercase()
    }
}

object VinParser {
    private val vinRegex = Regex("[A-HJ-NPR-Z0-9]{17}")

    fun parse(raw: String): String? {
        val normalized = ObdResponseParser.normalize(raw, "0902").getOrNull() ?: return null
        val bytes = normalized.frames
            .filter { it.size >= 4 && it[0] == 0x49 && it[1] == 0x02 }
            .sortedBy { it.getOrElse(2) { Int.MAX_VALUE } }
            .flatMap { frame ->
                val payload = frame.drop(2)
                if (payload.firstOrNull() in 1..20) payload.drop(1) else payload
            }
        val printable = bytes
            .filter { it in 0x20..0x7E }
            .map(Int::toChar)
            .joinToString("")
            .uppercase()
        return vinRegex.find(printable)?.value
    }
}

object ReadinessParser {
    fun parse(raw: String): ReadinessStatus? {
        val normalized = ObdResponseParser.normalize(raw, "0101").getOrNull() ?: return null
        val frame = normalized.frames.firstOrNull { it.size >= 6 && it[0] == 0x41 && it[1] == 0x01 } ?: return null
        val a = frame[2]
        val monitorBytes = frame.drop(3).take(3).joinToString("") { "%02X".format(it) }
        return ReadinessStatus(
            milOn = a and 0x80 != 0,
            dtcCount = a and 0x7F,
            rawMonitorBytes = monitorBytes,
            readAtMillis = System.currentTimeMillis(),
        )
    }
}

object FreezeFrameParser {
    fun parse(raw: String): FreezeFrameSummary? {
        val normalized = ObdResponseParser.normalize(raw, "020200").getOrNull() ?: return null
        val frame = normalized.frames.firstOrNull { it.size >= 5 && it[0] == 0x42 && it[1] == 0x02 } ?: return null
        val bytes = frame.takeLast(2)
        val trigger = if (bytes[0] == 0 && bytes[1] == 0) null else DtcParser.decodeCode(bytes[0], bytes[1])
        return FreezeFrameSummary(
            triggerDtc = trigger,
            raw = raw,
            readAtMillis = System.currentTimeMillis(),
        )
    }
}
