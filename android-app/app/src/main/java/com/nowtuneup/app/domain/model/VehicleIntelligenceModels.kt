package com.nowtuneup.app.domain.model

data class VehicleIdentity(
    val vin: String? = null,
    val detectedAtMillis: Long = 0L,
)

data class ReadinessStatus(
    val milOn: Boolean = false,
    val dtcCount: Int = 0,
    val rawMonitorBytes: String = "",
    val readAtMillis: Long = 0L,
)

data class FreezeFrameSummary(
    val triggerDtc: String? = null,
    val raw: String = "",
    val readAtMillis: Long = 0L,
)

data class Mode06Summary(
    val supported: Boolean = false,
    val monitorFrameCount: Int = 0,
    val raw: String = "",
    val readAtMillis: Long = 0L,
)

data class DiagnosticOverview(
    val stored: List<Dtc> = emptyList(),
    val pending: List<Dtc> = emptyList(),
    val permanent: List<Dtc> = emptyList(),
    val readiness: ReadinessStatus? = null,
    val freezeFrame: FreezeFrameSummary? = null,
    val vin: String? = null,
    val readAtMillis: Long = 0L,
) {
    val allDtcs: List<Dtc>
        get() = stored + pending + permanent
}

/**
 * A vehicle that the user explicitly created.
 *
 * VIN/OBD discovery is deliberately separate from this model's lifecycle. OBD code may build a
 * record for capability metadata, but VehicleProfileRepository refuses to persist records unless
 * [userCreated] is true. This prevents detected VINs, demos, and hardcoded data from silently
 * becoming saved vehicles.
 */
data class VehicleProfileRecord(
    val id: String = "",
    val vin: String = "",
    val displayName: String = "",
    val brand: String = "",
    val model: String = "",
    val year: String = "",
    val engine: String = "",
    val fuelType: String = "",
    val transmission: String = "",
    val notes: String = "",
    val isActive: Boolean = false,
    val userCreated: Boolean = false,
    val adapterIdentity: String? = null,
    val supportedPids: Set<Int> = emptySet(),
    val recommendedPollingMode: String = "BALANCED",
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
    val lastSeenAtMillis: Long = 0L,
)

data class AdapterSelfTestResult(
    val adapterIdentity: String? = null,
    val voltage: Double? = null,
    val passedChecks: Int = 0,
    val totalChecks: Int = 0,
    val averageLatencyMillis: Long = 0L,
    val timeSlipSupported: Boolean = false,
    val turboSupported: Boolean = false,
    val recommendedMode: String = "BALANCED",
    val details: List<String> = emptyList(),
)
