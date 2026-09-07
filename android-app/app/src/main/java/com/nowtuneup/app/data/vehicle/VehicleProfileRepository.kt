package com.nowtuneup.app.data.vehicle

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nowtuneup.app.domain.model.VehicleProfileRecord
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class VehicleProfileRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val type = object : TypeToken<List<VehicleProfileRecord>>() {}.type
    private val _profiles = MutableStateFlow(loadUserProfiles())

    /** Saved vehicles are user-created only. Legacy VIN-generated profiles are intentionally ignored. */
    val profiles: StateFlow<List<VehicleProfileRecord>> = _profiles.asStateFlow()

    fun list(): List<VehicleProfileRecord> = _profiles.value

    fun active(): VehicleProfileRecord? = list().firstOrNull { it.isActive }

    fun find(vin: String?): VehicleProfileRecord? {
        val normalized = vin?.trim()?.uppercase()?.takeIf { it.length == 17 } ?: return null
        return list().firstOrNull { it.vin == normalized }
    }

    fun findById(id: String?): VehicleProfileRecord? {
        val normalized = id?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return list().firstOrNull { it.id == normalized }
    }

    /**
     * Compatibility entry point used by older OBD code.
     * Auto-detected records have userCreated=false and are deliberately discarded.
     */
    fun save(profile: VehicleProfileRecord) {
        if (!profile.userCreated) return
        upsertUserProfile(profile)
    }

    fun create(profile: VehicleProfileRecord): VehicleProfileRecord {
        val now = System.currentTimeMillis()
        val firstUserVehicle = list().isEmpty()
        val created = sanitize(
            profile.copy(
                id = profile.id.ifBlank { UUID.randomUUID().toString() },
                userCreated = true,
                isActive = firstUserVehicle || profile.isActive,
                createdAtMillis = profile.createdAtMillis.takeIf { it > 0L } ?: now,
                updatedAtMillis = now,
                lastSeenAtMillis = profile.lastSeenAtMillis.takeIf { it > 0L } ?: now,
            ),
        )
        persist(normalizeActive(list() + created, preferredActiveId = created.id.takeIf { created.isActive }))
        return findById(created.id) ?: created
    }

    fun update(profile: VehicleProfileRecord): VehicleProfileRecord? {
        val existing = findById(profile.id) ?: return null
        val updated = sanitize(
            profile.copy(
                id = existing.id,
                userCreated = true,
                createdAtMillis = existing.createdAtMillis,
                updatedAtMillis = System.currentTimeMillis(),
            ),
        )
        val next = list().map { current -> if (current.id == updated.id) updated else current }
        persist(normalizeActive(next, preferredActiveId = updated.id.takeIf { updated.isActive }))
        return findById(updated.id)
    }

    fun duplicate(profileId: String): VehicleProfileRecord? {
        val source = findById(profileId) ?: return null
        val now = System.currentTimeMillis()
        val duplicate = source.copy(
            id = UUID.randomUUID().toString(),
            displayName = "${source.displayName} Copy",
            isActive = false,
            userCreated = true,
            createdAtMillis = now,
            updatedAtMillis = now,
            lastSeenAtMillis = now,
        )
        persist((list() + duplicate).takeLast(MAX_PROFILES))
        return duplicate
    }

    fun delete(profileId: String) {
        val existing = findById(profileId) ?: return
        val remaining = list().filterNot { it.id == profileId }
        val preferred = if (existing.isActive) remaining.firstOrNull()?.id else active()?.id
        persist(normalizeActive(remaining, preferred))
    }

    fun setActive(profileId: String) {
        if (findById(profileId) == null) return
        persist(list().map { it.copy(isActive = it.id == profileId) })
    }

    fun saveLastSessionReport(report: String) {
        preferences.edit().putString(KEY_LAST_SESSION_REPORT, report.take(MAX_REPORT_CHARS)).apply()
    }

    fun lastSessionReport(): String = preferences.getString(KEY_LAST_SESSION_REPORT, "").orEmpty()

    private fun upsertUserProfile(profile: VehicleProfileRecord) {
        val normalizedId = profile.id.ifBlank { UUID.randomUUID().toString() }
        val existing = findById(normalizedId)
        if (existing == null) {
            create(profile.copy(id = normalizedId))
        } else {
            update(profile.copy(id = normalizedId))
        }
    }

    private fun loadUserProfiles(): List<VehicleProfileRecord> = runCatching {
        val json = preferences.getString(KEY_USER_PROFILES, null) ?: return emptyList()
        gson.fromJson<List<VehicleProfileRecord>>(json, type).orEmpty()
            .filter { it.userCreated && it.id.isNotBlank() }
            .map(::sanitize)
            .take(MAX_PROFILES)
            .let(::normalizeActive)
    }.getOrDefault(emptyList())

    private fun persist(records: List<VehicleProfileRecord>) {
        val safe = records
            .filter { it.userCreated && it.id.isNotBlank() }
            .map(::sanitize)
            .take(MAX_PROFILES)
        preferences.edit().putString(KEY_USER_PROFILES, gson.toJson(safe, type)).apply()
        _profiles.value = safe
    }

    private fun sanitize(profile: VehicleProfileRecord): VehicleProfileRecord {
        val normalizedVin = profile.vin.trim().uppercase().takeIf { it.length == 17 }.orEmpty()
        val normalizedName = profile.displayName.trim().ifBlank {
            listOf(profile.brand.trim(), profile.model.trim()).filter { it.isNotBlank() }.joinToString(" ")
        }
        return profile.copy(
            id = profile.id.trim(),
            vin = normalizedVin,
            displayName = normalizedName,
            brand = profile.brand.trim(),
            model = profile.model.trim(),
            year = profile.year.trim(),
            engine = profile.engine.trim(),
            fuelType = profile.fuelType.trim(),
            transmission = profile.transmission.trim(),
            notes = profile.notes.trim(),
            userCreated = true,
        )
    }

    private fun normalizeActive(
        records: List<VehicleProfileRecord>,
        preferredActiveId: String? = records.firstOrNull { it.isActive }?.id,
    ): List<VehicleProfileRecord> {
        if (records.isEmpty()) return emptyList()
        val activeId = preferredActiveId?.takeIf { preferred -> records.any { it.id == preferred } }
            ?: records.firstOrNull { it.isActive }?.id
            ?: records.first().id
        return records.map { it.copy(isActive = it.id == activeId) }
    }

    companion object {
        private const val PREFERENCES_NAME = "ntu_vehicle_profiles"
        // V2 intentionally does not migrate profiles_v1 because v1 entries were generated from detected VINs.
        private const val KEY_USER_PROFILES = "profiles_v2_user_created"
        private const val KEY_LAST_SESSION_REPORT = "last_session_report"
        private const val MAX_PROFILES = 20
        private const val MAX_REPORT_CHARS = 40_000
    }
}
