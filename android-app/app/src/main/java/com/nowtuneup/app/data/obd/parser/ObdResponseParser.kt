package com.nowtuneup.app.data.obd.parser

import com.nowtuneup.app.data.obd.pid.StandardPids
import com.nowtuneup.app.domain.model.Dtc
import com.nowtuneup.app.domain.model.ObdError

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
                val compact = original.filterNot(Char::isWhitespace).uppercase()
                if (expectedEcho != null && compact == expectedEcho) return@forEach
                val upper = original.uppercase()
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
                    information += original
                    val remainder = original.substringAfter("...", "").trim()
                    if (remainder.isNotEmpty()) payloadLines += remainder
                } else if (!upper.equals("OK", true)) {
                    payloadLines += original
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

    fun parse(raw: String): List<Dtc> {
        val normalized = ObdResponseParser.normalize(raw, "03").getOrElse { return emptyList() }
        return normalized.frames.flatMap { frame ->
            if (frame.firstOrNull() != 0x43) {
                emptyList()
            } else {
                frame.drop(1)
                    .chunked(2)
                    .filter { it.size == 2 && (it[0] != 0 || it[1] != 0) }
                    .map { bytes ->
                        val prefix = "PCBU"[(bytes[0] shr 6) and 3]
                        val code = buildString {
                            append(prefix)
                            append((bytes[0] shr 4) and 3)
                            append((bytes[0] and 15).toString(16))
                            append(((bytes[1] shr 4) and 15).toString(16))
                            append((bytes[1] and 15).toString(16))
                        }.uppercase()
                        Dtc(
                            code = code,
                            category = prefix.toString(),
                            description = descriptions[code],
                            raw = raw,
                        )
                    }
            }
        }
    }
}
